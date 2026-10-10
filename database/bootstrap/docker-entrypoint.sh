#!/bin/sh
set -eu

: "${UNINAFOODLAB_DB_MIGRATOR_PASSWORD:?UNINAFOODLAB_DB_MIGRATOR_PASSWORD is required}"
: "${UNINAFOODLAB_DB_PASSWORD:?UNINAFOODLAB_DB_PASSWORD is required}"

psql \
  --username "${POSTGRES_USER}" \
  --dbname "${POSTGRES_DB}" \
  --set=ON_ERROR_STOP=1 \
  --set="migrator_password=${UNINAFOODLAB_DB_MIGRATOR_PASSWORD}" \
  --set="app_password=${UNINAFOODLAB_DB_PASSWORD}" \
  --file=/opt/uninafoodlab/bootstrap/001__create_roles.sql
