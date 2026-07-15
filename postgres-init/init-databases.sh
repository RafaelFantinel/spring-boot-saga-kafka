#!/bin/bash
# Runs on first container start (mounted into /docker-entrypoint-initdb.d/).
# Creates one database per service that owns its own schema (database-per-service
# pattern), while keeping a single Postgres container for demo simplicity.
set -euo pipefail

for db in orders_db saga_db inventory_db; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    SELECT 'CREATE DATABASE $db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db')\gexec
EOSQL
done
