# Installing the JAMS Cloud server on a host

Step-by-step guide to install and run the server on a fresh Linux host (e.g.
`xyz.uni-jena.de`). The whole service runs in Docker; you only need to build the
application `.war` once during setup. Repeat the same steps on each of your
servers — each host is self-contained (its own MySQL, users and jobs).

Commands below assume Debian/Ubuntu; equivalents for RHEL/Rocky are noted where
they differ. Run them as a normal user with `sudo` rights.

---

## 0. What gets installed

- **Docker + Compose** — runs the whole stack (`db` = MySQL 8, `server` = Payara 6
  on JDK 17, and optionally `caddy` for HTTPS).
- **A JDK (17+) and Git** — needed only to *build* the application once. The
  running service uses only Docker.

The model-execution JVM inside the container is Java 17, so uploaded models run
on Java 17 automatically — nothing extra to install for that.

Make sure the host has internet access (Docker Hub + Maven Central) and ~5 GB
free disk.

---

## 1. Install prerequisites

```bash
# Git and a JDK (build-time only)
sudo apt update
sudo apt install -y git openjdk-17-jdk
#   RHEL/Rocky:  sudo dnf install -y git java-17-openjdk-devel

# Docker Engine + Compose plugin (official convenience script)
curl -fsSL https://get.docker.com | sudo sh

# Run docker without sudo (log out/in afterwards for it to take effect)
sudo usermod -aG docker "$USER"

# Start Docker now and on every boot
sudo systemctl enable --now docker
```

Verify:

```bash
java -version            # should say 17 (or newer)
docker version           # client + server
docker compose version   # v2.x
```

---

## 2. Get the code

Two public repositories: the JAMS framework (needed to build the server) and the
server itself.

```bash
mkdir -p ~/jams && cd ~/jams
git clone https://github.com/jamsframework/jams.git
git clone https://github.com/jamsframework/jamsserver.git
```

---

## 3. Build the JAMS framework artifacts

The server depends on JAMS framework libraries that are not on Maven Central, so
build them once into your local Maven repo (`~/.m2`). The first run downloads
many dependencies and can take several minutes.

```bash
cd ~/jams/jams
./mvnw install -DskipTests
```

---

## 4. Build the server application

```bash
cd ~/jams/jamsserver
./mvnw clean package -DskipTests
# result: target/jams-cloud-server.war
```

---

## 5. Configure

Create a `.env` from the template and set **strong** secrets. `.env` is
git-ignored and stays only on this host.

```bash
cp .env.example .env
nano .env        # or your editor of choice
```

Set at least:

```
MYSQL_PASSWORD=<a strong db password>
MYSQL_ROOT_PASSWORD=<another strong password>
ADMIN_LOGIN=admin
ADMIN_PASSWORD=<the admin password you want>
SERVER_MAX_MEM=8g          # max heap per model run; size to the host's RAM
```

If you leave `ADMIN_PASSWORD` empty, a random one is generated and written to the
server log once (see step 7).

---

## 6. Launch

```bash
docker compose up -d --build
```

This builds the server image (baking in the `.war`), starts MySQL, waits for it
to be healthy, then starts the server. On first start Flyway creates the schema
and the admin account is created.

---

## 7. Verify

```bash
# Follow the startup until you see "was successfully deployed"
docker compose logs -f server
```

Health check (from the host):

```bash
curl http://localhost:8080/jamscloud/webresources/version
# -> prints the server version, e.g. 0.1.0.1
```

From another machine (port 8080 must be open in the firewall):

```bash
curl http://xyz.uni-jena.de:8080/jamscloud/webresources/version
```

If you left `ADMIN_PASSWORD` empty, find the generated password once:

```bash
docker compose logs server | grep "GENERATED password"
```

The desktop client connects to the base URL
`http://xyz.uni-jena.de:8080/jamscloud/webresources`.

---

## 8. Create users (optional)

Admin-side user management runs from the client project (`Controller` /
`UserController`, see the jams repo). You can bulk-create users from a `<users>`
XML file, which is repeatable (existing logins are skipped). This is done from a
machine with the client sources, pointed at the server URL — not on the server
itself.

---

## 9. Day-to-day operations

```bash
docker compose logs -f server         # view logs
docker compose ps                     # container status
docker compose stop                   # stop (keeps data)
docker compose start                  # start again
docker compose down                   # stop and remove containers (keeps volumes/data)
docker compose down -v                # ALSO delete the database volume (fresh start!)
```

Data (MySQL, uploads/exec) lives in named Docker volumes and survives restarts
and reboots (Docker is enabled as a service). `restart: unless-stopped` brings
the containers back automatically after a reboot.

**Update to a new version:**

```bash
cd ~/jams/jams        && git pull && ./mvnw install -DskipTests
cd ~/jams/jamsserver && git pull && ./mvnw clean package -DskipTests
docker compose up -d --build
```

**Backups:** back up the `db-data` volume (or use `mysqldump` against the `db`
container) regularly if the data matters.

---

## 10. Optional: HTTPS

The steps above run plain HTTP on port 8080. To terminate HTTPS with a bundled
Caddy reverse proxy instead, see the *Optional: HTTPS with Caddy* section in
`README.md` — it uses `docker-compose.tls.yml` and supports both a self-signed
internal CA and automatic Let's Encrypt certificates.
