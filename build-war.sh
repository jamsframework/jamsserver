#!/usr/bin/env bash
# Headless WAR build for jams-cloud-server (NetBeans/Ant web project).
#
# Builds dist/jams-cloud-server.war without opening the IDE. Requires the ant and
# CopyLibs task that ship with Apache NetBeans, plus a JDK. The EE (javax.*) APIs are
# supplied from lib/ext at compile time, so no Payara/GlassFish install is needed to build.
#
# Overridable via environment:
#   NB_HOME    NetBeans install (…/Contents/Resources/netbeans)
#   JAVA_HOME  JDK used to compile (target is Java 8)
set -euo pipefail

NB_HOME="${NB_HOME:-/Applications/Apache NetBeans.app/Contents/Resources/netbeans}"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export JAVA_HOME

ANT="$NB_HOME/extide/ant/bin/ant"
COPYLIBS="$NB_HOME/java/ant/extra/org-netbeans-modules-java-j2seproject-copylibstask.jar"

for f in "$ANT" "$COPYLIBS"; do
  [ -e "$f" ] || { echo "Not found: $f (set NB_HOME to your NetBeans install)" >&2; exit 1; }
done

cd "$(dirname "$0")"
exec "$ANT" -f build.xml \
  -Dlibs.CopyLibs.classpath="$COPYLIBS" \
  -Dplatforms.JDK_1.8.home="$JAVA_HOME" \
  -Dj2ee.server.home= -Dj2ee.platform.classpath= \
  -Dlibs.javaee-endorsed-api-6.0.classpath= -Dendorsed.classpath= \
  clean dist
