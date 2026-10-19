#!/bin/sh
set -eu
business=$(cat /run/secrets/business_db_password)
ai=$(cat /run/secrets/ai_db_password)
# Generated hexadecimal credentials only; never interpolate arbitrary SQL input.
for value in "$business" "$ai"; do
  test ${#value} -eq 64 && ! printf '%s' "$value" | grep -q '[^a-f0-9]' || exit 1
done
export MYSQL_PWD=$(cat /run/secrets/mysql_root_password)
mysql -uroot <<SQL
CREATE DATABASE campus_business CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE campus_ai CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'campus_business_app'@'%' IDENTIFIED BY '$business';
CREATE USER 'campus_ai_app'@'%' IDENTIFIED BY '$ai';
GRANT ALL PRIVILEGES ON campus_business.* TO 'campus_business_app'@'%';
GRANT ALL PRIVILEGES ON campus_ai.* TO 'campus_ai_app'@'%';
SQL
