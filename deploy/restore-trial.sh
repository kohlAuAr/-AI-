#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
folder=${1:?Usage: sh deploy/restore-trial.sh backups/TIMESTAMP}
folder=$(cd "$folder" && pwd)
(cd "$folder" && sha256sum -c databases.sha256)
name="campus-restore-$(date -u +%Y%m%d%H%M%S)"
docker run -d --name "$name" --network none --memory 768m -e MYSQL_ROOT_PASSWORD_FILE=/run/secrets/root \
  --mount "type=bind,source=$(pwd)/secrets/mysql_root_password,target=/run/secrets/root,readonly" \
  --mount "type=volume,source=$name-data,target=/var/lib/mysql" mysql:8.4@sha256:24989179f58856db763f418010c1169fecc944e3ec1ea7e921572d83eb070f70 --innodb-buffer-pool-size=256M >/dev/null
ready=0
for attempt in $(seq 1 60); do
  if docker exec "$name" sh -c 'MYSQL_PWD=$(cat /run/secrets/root) mysql -uroot -e "SELECT 1"' >/dev/null 2>&1; then ready=1; break; fi
  sleep 2
done
test "$ready" = 1 || { echo "Trial startup failed; inspect $name" >&2; exit 1; }
docker exec -i "$name" sh -c 'MYSQL_PWD=$(cat /run/secrets/root) mysql -uroot' < "$folder/databases.sql"
actual=$(docker exec "$name" sh -c 'export MYSQL_PWD=$(cat /run/secrets/root); exec mysqldump -uroot --compact --skip-comments --no-create-info --skip-extended-insert --order-by-primary --hex-blob --set-gtid-purged=OFF --databases campus_business campus_ai' | sha256sum | cut -d' ' -f1)
test "$actual" = "$(cat "$folder/data.sha256")" || { echo "Restored row digest mismatch; retained $name" >&2; exit 1; }
docker stop "$name" >/dev/null
echo "PASS: all restored row data matched. Isolated stopped container $name and volume $name-data retained; production was not overwritten."
