# Local database setup

Laundry-Hub exposes its Docker PostgreSQL 16 database on host port **5433**.
This avoids connecting to an existing PostgreSQL installation on port 5432.
PostgreSQL inside the container still listens on port 5432.

Run these commands from the repository's `code` directory:

```powershell
docker compose up -d db
docker compose ps
mvn clean test
mvn spring-boot:run
```

If the database container already exists, `docker compose up -d db` recreates
it with the updated port mapping and preserves its existing `pgdata` volume.
Do not delete the volume to apply a port change.

The application's default database URL is
`jdbc:postgresql://localhost:5433/laundryhub`, with development username
`laundry` and password `laundry`. These credentials belong to the Docker
database, independently of any PostgreSQL installation on Windows.

An existing `DB_URL` environment variable overrides the application's default.
For local Docker development, ensure it uses the URL above. Deployment should
continue to set `DB_URL`, `DB_USER`, and `DB_PASSWORD` for its own database.

After startup, open http://localhost:8080/swagger-ui.html.
Authentication requirements depend on the current security configuration.
