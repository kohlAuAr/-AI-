#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
umask 077
release=$(cat .release)
dc() { RELEASE=$release docker compose --env-file .env -f compose.yml "$@"; }
folder="backups/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir "$folder"
# Freeze writes to BOTH databases. Resume only services which were previously running.
running=$(dc ps --status running --services | grep -E '^(campus|ai)$' || true)
resume() { if [ -n "$running" ]; then dc start $running >/dev/null; fi; }
trap resume EXIT
trap 'exit 130' HUP INT TERM
dc stop campus ai
dc exec -T mysql sh -c 'export MYSQL_PWD=$(cat /run/secrets/mysql_root_password); exec mysqldump -uroot --single-transaction --hex-blob --routines --events --triggers --set-gtid-purged=OFF --databases campus_business campus_ai' > "$folder/databases.sql.tmp"
dc exec -T mysql sh -c 'export MYSQL_PWD=$(cat /run/secrets/mysql_root_password); exec mysqldump -uroot --compact --skip-comments --no-create-info --skip-extended-insert --order-by-primary --hex-blob --set-gtid-purged=OFF --databases campus_business campus_ai' | sha256sum | cut -d' ' -f1 > "$folder/data.sha256"
mv "$folder/databases.sql.tmp" "$folder/databases.sql"
(cd "$folder" && sha256sum databases.sql > databases.sha256)
printf '%s\n' "$release" > "$folder/release"
echo "Backup retained in $folder. Store an encrypted off-host copy and a separate secure copy of ai_master_key."
