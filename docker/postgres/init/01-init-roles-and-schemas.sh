#!/bin/sh
set -eu

# Runs only on the FIRST initialisation of the data volume.
# Each service gets its own login role that owns exactly one schema.
psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" \
     --dbname "$POSTGRES_DB" \
     -v maint_pw="$MAINTENANCE_DB_PASSWORD" \
     -v inv_pw="$INVENTORY_DB_PASSWORD" <<'SQL'

CREATE ROLE maintenance_app LOGIN PASSWORD :'maint_pw';
CREATE ROLE inventory_app   LOGIN PASSWORD :'inv_pw';

CREATE SCHEMA maintenance AUTHORIZATION maintenance_app;
CREATE SCHEMA inventory   AUTHORIZATION inventory_app;

-- Nobody else may use or create objects in these schemas.
REVOKE ALL ON SCHEMA maintenance FROM PUBLIC;
REVOKE ALL ON SCHEMA inventory   FROM PUBLIC;
REVOKE CREATE ON SCHEMA public   FROM PUBLIC;

ALTER ROLE maintenance_app SET search_path = maintenance;
ALTER ROLE inventory_app   SET search_path = inventory;
SQL