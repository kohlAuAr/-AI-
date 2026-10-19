#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
command=${1:?Usage: sh deploy/manage.sh start|update|rollback|stop|status|logs|backup [RELEASE]}
test -f .env || { echo 'Prepare deploy/.env and secrets first' >&2; exit 1; }
if ! mkdir .lock 2>/dev/null; then echo 'Another deployment operation is active; inspect before removing a stale .lock' >&2; exit 1; fi
trap 'rmdir .lock' EXIT
trap 'exit 130' HUP INT TERM
current=$(cat .release 2>/dev/null || true)
dc() { RELEASE=${release:-$current} docker compose --env-file .env -f compose.yml "$@"; }
case "$command" in
  start|update|rollback)
    release=${2:-}
    if [ "$command" = rollback ]; then
      release=$(cat .previous)
      cmp "releases/$current.migrations" "releases/$release.migrations" || { echo 'Schema changed: automatic application rollback refused. Operator review/restore trial required.' >&2; exit 1; }
      cmp "releases/$current.runtime" "releases/$release.runtime" || { echo 'Deployment topology changed: automatic rollback refused. Operator review required.' >&2; exit 1; }
    fi
    case "$release" in *[!a-zA-Z0-9_.-]*|'') echo 'Provide a release tag' >&2; exit 1;; esac
    test -s tls/fullchain.pem && test -s tls/privkey.pem || { echo 'Install real TLS certificate files first' >&2; exit 1; }
    openssl x509 -in tls/fullchain.pem -noout -checkend 86400 >/dev/null || { echo 'Certificate expired or expires within 24h; renew first' >&2; exit 1; }
    test -s "releases/$release.migrations"
    for name in business ai web; do docker image inspect "campus-$name:$release" >/dev/null 2>&1; done
    if [ "$command" = update ]; then bash backup.sh; fi
    dc up -d --wait --wait-timeout 240
    dc exec -T web nginx -t
    if [ -n "$current" ] && [ "$current" != "$release" ]; then printf '%s\n' "$current" > .previous; fi
    printf '%s\n' "$release" > .release.tmp; mv .release.tmp .release
    echo "Active release: $release. Failed updates never change .release; inspect logs before retrying."
    ;;
  stop) dc stop ;;
  status) dc ps ;;
  logs) dc logs --tail 100 ;;
  backup) bash backup.sh ;;
  *) echo 'Unsupported operation; no volume deletion or production overwrite is provided' >&2; exit 1 ;;
esac
