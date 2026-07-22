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

### External access

Port 8080 is published on `0.0.0.0`, so the server is reachable from other hosts
as `http://<host-ip>:8080/jamscloud/webresources` (the desktop client connects to
this base URL). The Payara admin console (4848) is bound to localhost only.

- **On a Linux server** this works out of the box; just open port 8080 in the
  firewall.
- **On macOS via Colima** the published port is forwarded to the host on all
  interfaces, so `http://<mac-lan-ip>:8080/jamscloud/…` works from other machines.
  Colima must be running (`colima start`); it does not survive a reboot unless you
  enable it as a service (`brew services start colima`).
- **No TLS by default:** login uses HTTP Basic auth, so credentials travel in
  cleartext. Only expose the plain-HTTP setup on a trusted network. TLS is
  available as an optional add-on (see below).

### Optional: HTTPS with Caddy

TLS is **opt-in** and does not affect the default HTTP deployment. A separate
compose file adds a [Caddy](https://caddyserver.com/) reverse proxy that
terminates HTTPS and proxies to the server internally; the app port (8080) is no
longer published — only Caddy is exposed (80/443). Use it *instead* of the
default file:

```
cp .env.example .env          # set SERVER_NAME / CADDY_TLS (and the passwords)
docker compose -f docker-compose.tls.yml up -d --build
```

Two modes, selected via `.env` (see `docker/Caddyfile`):

- **Self-signed** (`CADDY_TLS=tls internal`) — Caddy issues a certificate from
  its own internal CA. Needs no extra open ports, but clients must trust that CA:
  the browser will warn, and the **Java desktop client rejects it** unless the CA
  is imported into a Java truststore (or bundled with the client). Fine for a
  controlled set of users; the CA lives in the `caddy-data` volume.
- **Let's Encrypt** (`CADDY_TLS=` empty, `SERVER_NAME=<public-host>`) — a real,
  publicly-trusted certificate, issued and renewed automatically. This needs the
  host reachable from the internet on **ports 80/443** (that is what the ACME
  challenge uses — not the application port). No certificate paperwork; if 80/443
  cannot be opened, use the self-signed mode instead.

## Configuration reference

| Environment variable | Purpose |
|----------------------|---------|
| `MYSQL_HOST/PORT/DB/USER/PASSWORD` | JPA datasource (`jdbc/jamsserver`) |
| `DATABASE_URL/USER/PASSWORD`       | Flyway migrations at startup |
| `UPLOAD_DIRECTORY/TMP_DIRECTORY/EXEC_DIRECTORY` | server working dirs |
| `SERVER_MAX_MEM`                   | heap for a model run (default `8g`) |

Without environment variables the server falls back to a `settings.properties`
file in its working directory — see `config/settings.properties.sample`.

## Security / production hardening

- **Passwords** are stored salted+hashed (PBKDF2, see `PasswordHasher`). The admin
  account is created on first start from `ADMIN_LOGIN` / `ADMIN_PASSWORD`. If
  `ADMIN_PASSWORD` is empty a random one is generated and logged once — change it
  after first login.
- **Login** sends credentials in an `Authorization: Basic` header (POST).
- **Payara admin** (`4848`) is published to `127.0.0.1` only.
- **Secrets** live in `.env` (git-ignored). For deployment use Docker/host
  secrets and set strong `MYSQL_*` / `ADMIN_PASSWORD`.
- **TLS**: terminate HTTPS in front of the app, since the container speaks plain
  HTTP. The app is published on `0.0.0.0:8080` for direct external access (see
  *External access* above); for TLS, switch the binding back to
  `127.0.0.1:8080:8080` and put a reverse proxy before it, e.g. Caddy (automatic
  HTTPS):

  ```
  cloud.example.org {
      reverse_proxy 127.0.0.1:8080
  }
  ```
