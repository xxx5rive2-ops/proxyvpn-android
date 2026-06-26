#!/bin/sh
##############################################################################
# Gradle wrapper script — downloads Gradle if needed and runs it.
##############################################################################
APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
DIRNAME=$(dirname "$0")
CLASSPATH="$DIRNAME/gradle/wrapper/gradle-wrapper.jar"

if [ -z "$JAVA_HOME" ]; then
    JAVACMD=java
else
    JAVACMD="$JAVA_HOME/bin/java"
fi

exec "$JAVACMD" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
