# JAMS Cloud Server

REST backend for JAMS Cloud (job/file/workspace/user management and server-side
model execution). Built with Maven, runs on **Payara 6** (Jakarta EE 10, JDK 17)
against **MySQL 8**.

The shared entities live in `jams-cloud-core` (jams repo) on the `javax.*` stack
so the desktop client stays unchanged; they are converted to `jakarta.*` at build
time by the Eclipse Transformer and bundled into the WAR.

## Prerequisites

- JDK 17+
- The JAMS framework artifacts in the local Maven repo. Build them once:
  ```
  cd ../jams && ./mvnw install -DskipTests
  ```
  (provides `org.jams:jams-api`, `jams-main`, `jams-cloud-core`).

## Build

```
./mvnw clean package
```

Produces `target/jams-cloud-server.war`.

## Run with Docker (recommended)

Brings up Payara 6 + MySQL 8:

```
./mvnw clean package
cp .env.example .env          # set the DB passwords
docker compose up --build
```

- Application: <http://localhost:8080/jamscloud/> (REST base `…/jamscloud/webresources`)
- Payara admin: <http://localhost:4848/>

Configuration is entirely environment-driven (see `docker-compose.yml`); MySQL
host/credentials, the Flyway migration URL and the upload/tmp/exec directories
all come from environment variables. Flyway creates the schema on first start.

## Configuration reference

| Environment variable | Purpose |
|----------------------|---------|
| `MYSQL_HOST/PORT/DB/USER/PASSWORD` | JPA datasource (`jdbc/jamsserver`) |
| `DATABASE_URL/USER/PASSWORD`       | Flyway migrations at startup |
| `UPLOAD_DIRECTORY/TMP_DIRECTORY/EXEC_DIRECTORY` | server working dirs |
| `SERVER_MAX_MEM`                   | heap for a model run (default `8g`) |

Without environment variables the server falls back to a `settings.properties`
file in its working directory — see `config/settings.properties.sample`.
