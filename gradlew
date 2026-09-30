#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export JAVA_HOME="/c/Users/mehboob.rehman/tools/jdk17.0.19_10"
"$JAVA_HOME/bin/java" -Dgradle.user.home="$SCRIPT_DIR/gradle-home" -classpath "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
