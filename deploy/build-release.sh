#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tag=${1:?Usage: sh deploy/build-release.sh RELEASE_TAG}
case "$tag" in *[!a-zA-Z0-9_.-]*|'') echo 'Invalid tag' >&2; exit 1;; esac
for name in business ai web; do
  if docker image inspect "campus-$name:$tag" >/dev/null 2>&1; then echo 'Never overwrite an existing release tag' >&2; exit 1; fi
done
mvn -DskipTests=false package
(cd frontend && npm ci && npm run test:knowledge && npm run test:business && npm run test:prototype && npm run build)
RELEASE=$tag docker compose --env-file deploy/.env -f deploy/compose.yml build
mkdir -p deploy/releases
find campus-service/src/main/resources/db/migration ai-service/src/main/resources/db/migration -name '*.sql' -type f | sort | xargs sha256sum > "deploy/releases/$tag.migrations"
git rev-parse HEAD > "deploy/releases/$tag.commit"
sha256sum deploy/compose.yml > "deploy/releases/$tag.runtime"
echo "Built tested release $tag. Databases, secrets, certificates and backups are not images."
