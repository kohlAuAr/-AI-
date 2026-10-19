#!/bin/sh
set -eu
cd "$(dirname "$0")"
umask 077
mkdir -p secrets tls backups
chmod 700 secrets tls backups
for name in mysql_root_password business_db_password ai_db_password redis_password internal_token admin_password; do
  if [ ! -e "secrets/$name" ]; then openssl rand -hex 32 | tr -d '\r\n' > "secrets/$name"; fi
  test -s "secrets/$name" || { echo "Empty secret: $name" >&2; exit 1; }
done
if [ ! -e secrets/ai_master_key ]; then openssl rand -base64 32 | tr -d '\r\n' > secrets/ai_master_key; fi
chmod 444 secrets/*
# Compose mounts individual files into non-root app containers; parent remains private (0700).
echo 'Secret files prepared without overwriting existing values. Read admin_password locally; never commit, screenshot or paste it.'
echo 'Back up ai_master_key securely; losing it makes existing encrypted model keys unreadable.'
