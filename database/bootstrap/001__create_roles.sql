\set ON_ERROR_STOP on

SELECT 'CREATE ROLE uninafoodlab_migrator'
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'uninafoodlab_migrator')
\gexec

ALTER ROLE uninafoodlab_migrator
    WITH LOGIN PASSWORD :'migrator_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;

SELECT 'CREATE ROLE uninafoodlab_app'
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'uninafoodlab_app')
\gexec

ALTER ROLE uninafoodlab_app
    WITH LOGIN PASSWORD :'app_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;

SELECT format(
    'REVOKE CREATE ON DATABASE %I FROM PUBLIC',
    current_database()
)
\gexec

SELECT format(
    'REVOKE TEMPORARY ON DATABASE %I FROM PUBLIC',
    current_database()
)
\gexec

SELECT format(
    'GRANT CONNECT ON DATABASE %I TO uninafoodlab_migrator, uninafoodlab_app',
    current_database()
)
\gexec

REVOKE CREATE ON SCHEMA public FROM PUBLIC;

CREATE SCHEMA IF NOT EXISTS app AUTHORIZATION uninafoodlab_migrator;
ALTER SCHEMA app OWNER TO uninafoodlab_migrator;

REVOKE ALL ON SCHEMA app FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA app TO uninafoodlab_migrator;
GRANT USAGE ON SCHEMA app TO uninafoodlab_app;
