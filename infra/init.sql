CREATE DATABASE IF NOT EXISTS campus_ai CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER DATABASE campus_business CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON campus_ai.* TO 'campus'@'%';
