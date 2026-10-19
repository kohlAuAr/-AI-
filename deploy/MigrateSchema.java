import java.nio.file.*;
import org.flywaydb.core.Flyway;

/** Operator CLI for EMPTY MySQL import targets; runtime also runs these same versioned migrations. */
class MigrateSchema {
    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !args[0].startsWith("jdbc:mysql:")) throw new IllegalArgumentException("MigrateSchema MYSQL_JDBC_URL USER PASSWORD_FILE MIGRATIONS_FOLDER");
        Flyway.configure().dataSource(args[0], args[1], Files.readString(Path.of(args[2])).strip())
                .locations("filesystem:" + Path.of(args[3]).toAbsolutePath()).load().migrate();
    }
}
