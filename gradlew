#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -n "${JAVA_HOME:-}" ]]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA="$(command -v java || true)"
fi

if [[ -z "$JAVA" || ! -x "$JAVA" ]]; then
    echo "Java was not found. Set JAVA_HOME or add java to PATH." >&2
    exit 1
fi

exec "$JAVA" \
    -Dgradle.user.home="$SCRIPT_DIR/gradle-home" \
    -classpath "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain "$@"
