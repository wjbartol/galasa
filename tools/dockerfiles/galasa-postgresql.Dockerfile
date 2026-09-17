# ==============================================================================
# 🐘 Galasa Ecosystem - PostgreSQL Container Image
# ==============================================================================
FROM postgres:17-alpine

LABEL name="galasa-postgresql" \
      vendor="Galasa Ecosystem" \
      description="PostgreSQL container with pre-loaded Galasa DSS/CPS/RAS schema"

ENV POSTGRES_DB=galasadb
ENV POSTGRES_USER=galasa
ENV POSTGRES_PASSWORD=galasapassword

COPY init.sql /docker-entrypoint-initdb.d/init-galasa.sql

EXPOSE 5432
