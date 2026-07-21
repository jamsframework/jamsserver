# JAMS Cloud Server on Payara 6 (Jakarta EE 10, JDK 17).
# The JDK 17 runtime also executes the uploaded JAMS models, so the old
# "models run on Java 8" problem disappears with this image.
#
# Build context is the repository root; run `./mvnw package` first so that
# target/jams-cloud-server.war exists.
FROM payara/server-full:6.2025.11-jdk17

# MySQL 8 JDBC driver at domain scope — the JTA connection pool created from
# WEB-INF/glassfish-resources.xml is a server resource and needs the driver on
# the domain classpath, not just inside the WAR.
ARG MYSQL_CONNECTOR_VERSION=8.4.0
ADD --chown=payara:payara \
    https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/${MYSQL_CONNECTOR_VERSION}/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar \
    ${PAYARA_DIR}/glassfish/domains/${DOMAIN_NAME}/lib/mysql-connector-j.jar

# Working directories for uploads / tmp / model execution. Created here and
# owned by the payara user; the named volume in docker-compose inherits this
# ownership on first initialization.
RUN mkdir -p /opt/payara/jams/upload /opt/payara/jams/tmp /opt/payara/jams/exec \
    && chown -R payara:payara /opt/payara/jams

# Auto-deployed on server start.
COPY --chown=payara:payara target/jams-cloud-server.war ${DEPLOY_DIR}/
