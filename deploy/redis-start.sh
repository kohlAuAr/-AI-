#!/bin/sh
set -eu
umask 077
password=$(cat /run/secrets/redis_password)
test ${#password} -eq 64 && ! printf '%s' "$password" | grep -q '[^a-f0-9]' || exit 1
printf 'bind 0.0.0.0\nprotected-mode yes\nrequirepass %s\nappendonly yes\nappendfsync always\nmaxmemory 128mb\nmaxmemory-policy noeviction\ndir /data\n' "$password" > /tmp/campus-redis.conf
exec redis-server /tmp/campus-redis.conf
