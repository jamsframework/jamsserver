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
# Git, a JDK (build-time only) and curl/ca-certificates (needed by the Docker
# installer; curl is often missing on a minimal Debian install)
sudo apt update
sudo apt install -y git openjdk-17-jdk curl ca-certificates
#   RHEL/Rocky:  sudo dnf install -y git java-17-openjdk-devel curl ca-certificates

# Maven needs JAVA_HOME, which Debian does not set automatically. Point it at the
# JDK and persist it for future logins:
export JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")
echo "export JAVA_HOME=$JAVA_HOME" >> ~/.bashrc

# Docker Engine + Compose plugin (official convenience script; installs Docker CE
# so you get the "docker compose" v2 plugin — do not use Debian's docker.io)
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
# result: target/jamsserver.war
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
DATA_DIR=/home/jamscloud    # host dir for all persistent data (see below)
```

If you leave `ADMIN_PASSWORD` empty, a random one is generated and written to the
server log once (see step 7).

### Prepare the data directory

All persistent data is bind-mounted from `DATA_DIR` on the host, so put it on a
partition with plenty of free space (the model-execution dirs can grow large):

- `${DATA_DIR}/data` — uploads, temp and per-job model-execution dirs
- `${DATA_DIR}/db`   — the MySQL database

Bind-mounted host directories do **not** inherit ownership from the image, so
create them once with the right owners before the first start (the server runs as
uid 1000, MySQL as uid 999 inside the containers):

```bash
sudo mkdir -p /home/jamscloud/data /home/jamscloud/db
sudo chown -R 1000:1000 /home/jamscloud/data   # payara user
sudo chown -R 999:999   /home/jamscloud/db     # mysql user
```

(Adjust the path if you changed `DATA_DIR`.) Check free space with
`df -h /home/jamscloud`.

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
docker compose down                   # stop and remove containers (keeps data)
```

All persistent data lives on the host under `DATA_DIR` (`/home/jamscloud` by
default): `data/` (uploads + model runs) and `db/` (MySQL). Because it is
bind-mounted, it survives `docker compose down`, restarts and reboots
(`restart: unless-stopped` brings the containers back after a reboot). To wipe
everything for a fresh start, stop the stack and delete those directories:

```bash
docker compose down
sudo rm -rf /home/jamscloud/data /home/jamscloud/db   # DANGER: deletes all data
```

**Update to a new version:**

```bash
cd ~/jams/jams        && git pull && ./mvnw install -DskipTests
cd ~/jams/jamsserver && git pull && ./mvnw clean package -DskipTests
docker compose up -d --build
```

**Backups:** back up `DATA_DIR` (`/home/jamscloud` — both `data/` and `db/`)
regularly if the data matters. For a consistent database dump you can also use
`docker compose exec db mysqldump -ujams -p"$MYSQL_PASSWORD" jamsserver > backup.sql`.

---

## 10. Optional: HTTPS

The steps above run plain HTTP on port 8080. To terminate HTTPS with a bundled
Caddy reverse proxy instead, see the *Optional: HTTPS with Caddy* section in
`README.md` — it uses `docker-compose.tls.yml` and supports both a self-signed
internal CA and automatic Let's Encrypt certificates.
