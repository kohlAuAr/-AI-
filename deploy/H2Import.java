import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.security.MessageDigest;
import java.util.*;

/** Offline copy only: closed H2 backup -> empty, already-migrated MySQL schema. No source writes. */
class H2Import {
    public static void main(String[] args) throws Exception {
        if (args.length != 5 || !args[4].equals("--confirm-empty-target")) throw new IllegalArgumentException("H2Import CLOSED_BACKUP_WITHOUT_SUFFIX MYSQL_JDBC_URL USER PASSWORD_FILE --confirm-empty-target");
        if (!args[1].startsWith("jdbc:mysql:")) throw new IllegalArgumentException("Target must be MySQL");
        Path source = Path.of(args[0] + ".mv.db").toRealPath();
        String password = Files.readString(Path.of(args[3]), StandardCharsets.UTF_8).strip();
        try (var h2 = DriverManager.getConnection("jdbc:h2:file:" + source.toString().replace("\\", "/").replaceFirst("\\.mv\\.db$", "") + ";IFEXISTS=TRUE;ACCESS_MODE_DATA=r;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
             var mysql = DriverManager.getConnection(args[1], args[2], password)) {
            String catalog = mysql.getCatalog();
            if (catalog == null || Set.of("mysql", "sys", "information_schema", "performance_schema").contains(catalog)) throw new IllegalArgumentException("Select a dedicated target schema");
            List<String> tables = new ArrayList<>();
            try (var rs = h2.getMetaData().getTables(null, null, "%", new String[]{"BASE TABLE", "TABLE"})) {
                while (rs.next()) if (rs.getString("TABLE_SCHEM").equalsIgnoreCase("public")) tables.add(identifier(rs.getString("TABLE_NAME")));
            }
            Set<String> targetTables = new HashSet<>();
            if (tables.isEmpty()) throw new IllegalStateException("Source has no application tables; verify the closed backup path");
            try (var rs = mysql.getMetaData().getTables(catalog, null, "%", new String[]{"TABLE"})) { while (rs.next()) targetTables.add(rs.getString("TABLE_NAME")); }
            if (!targetTables.containsAll(tables)) throw new IllegalStateException("Target schema lacks source tables; run Flyway first");
            for (String table : targetTables) if (!table.equals("flyway_schema_history")) {
                try (var stmt = mysql.createStatement(); var rs = stmt.executeQuery("SELECT COUNT(*) FROM `" + identifier(table) + "`")) {
                    rs.next(); if (rs.getLong(1) != 0) throw new IllegalStateException("Target is nonempty; no data copied");
                }
            }
            var ordered = new ArrayList<String>();
            while (ordered.size() < tables.size()) {
                int before = ordered.size();
                for (String table : tables) if (!ordered.contains(table)) {
                    var parents = new HashSet<String>();
                    try (var keys = mysql.getMetaData().getImportedKeys(catalog, null, table)) { while (keys.next()) parents.add(keys.getString("PKTABLE_NAME")); }
                    if (ordered.containsAll(parents)) ordered.add(table);
                }
                if (before == ordered.size()) throw new IllegalStateException("Cyclic/unknown foreign keys; manual migration required");
            }
            mysql.setAutoCommit(false);
            try {
                for (String table : ordered) {
                    List<String> columns = new ArrayList<>();
                    try (var rs = h2.getMetaData().getColumns(null, null, table, "%")) { while (rs.next()) columns.add(identifier(rs.getString("COLUMN_NAME"))); }
                    Collections.sort(columns);
                    String names = String.join(",", columns.stream().map(c -> "`" + c + "`").toList());
                    String sql = "INSERT INTO `" + table + "` (" + names + ") VALUES (" + String.join(",", Collections.nCopies(columns.size(), "?")) + ")";
                    long count = 0;
                    try (var read = h2.createStatement(); var rows = read.executeQuery(select(table, columns, false)); var insert = mysql.prepareStatement(sql)) {
                        while (rows.next()) {
                            for (int i = 1; i <= columns.size(); i++) insert.setObject(i, read(rows, i));
                            insert.addBatch(); if (++count % 200 == 0) insert.executeBatch();
                        }
                        insert.executeBatch();
                    }
                    if (!Arrays.equals(digest(h2, table, columns, false), digest(mysql, table, columns, true))) throw new IllegalStateException("Row digest mismatch: " + table);
                    System.out.println("Verified " + table + ": " + count + " rows");
                }
                mysql.commit(); System.out.println("PASS: source read-only, every imported row verified, target committed. No demo credentials were changed.");
            } catch (Exception error) { mysql.rollback(); throw error; }
        }
    }
    static String identifier(String name) { if (!name.matches("[a-z_][a-z0-9_]*")) throw new IllegalArgumentException("Unsupported identifier"); return name; }
    static String select(String table, List<String> columns, boolean mysql) {
        String quote = mysql ? "`" : "\"";
        String names = String.join(",", columns.stream().map(c -> quote + c + quote).toList());
        String order = columns.contains("id") ? "id" : columns.contains("purpose") ? "purpose" : columns.contains("content_key") ? "content_key" : columns.get(0);
        return "SELECT " + names + " FROM " + quote + table + quote + " ORDER BY " + quote + order + quote;
    }
    static Object value(Object object) throws Exception {
        if (object instanceof Clob clob) return clob.getSubString(1, Math.toIntExact(clob.length()));
        if (object instanceof Blob blob) return blob.getBytes(1, Math.toIntExact(blob.length()));
        if (object instanceof java.time.OffsetDateTime time) return Timestamp.from(time.toInstant());
        return object;
    }
    static Object read(ResultSet rows, int index) throws Exception {
        Object object = rows.getObject(index);
        return object instanceof java.time.LocalDateTime ? rows.getTimestamp(index) : value(object);
    }
    static byte[] digest(Connection connection, String table, List<String> columns, boolean mysql) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var stmt = connection.createStatement(); var rows = stmt.executeQuery(select(table, columns, mysql))) {
            while (rows.next()) for (int i = 1; i <= columns.size(); i++) {
                Object object = read(rows, i);
                digest.update((byte) (object == null ? 0 : 1));
                String text = object == null ? "NULL" : object instanceof byte[] bytes ? Base64.getEncoder().encodeToString(bytes)
                        : object instanceof Timestamp time ? time.toInstant().toString()
                        : object instanceof java.math.BigDecimal number ? number.stripTrailingZeros().toPlainString() : object.toString();
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
            }
        }
        return digest.digest();
    }
}
