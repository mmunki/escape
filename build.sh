#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

java_major() {
    "$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n1
}

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] && [ "$(java_major "$JAVA_HOME")" -ge 25 ]; then
    selected="$JAVA_HOME"
else
    selected=""
    for candidate in \
        /usr/lib/jvm/temurin-25-jdk-amd64 \
        /usr/lib/jvm/temurin-26-jdk-amd64 \
        /usr/lib/jvm/java-25-openjdk-amd64 \
        /usr/lib/jvm/java-26-openjdk-amd64; do
        if [ -x "$candidate/bin/java" ] && [ "$(java_major "$candidate")" -ge 25 ]; then
            selected="$candidate"
            break
        fi
    done
fi

if [ -z "$selected" ]; then
    echo "error: a JDK 25 or newer is required (set JAVA_HOME)" >&2
    exit 1
fi

export JAVA_HOME="$selected"
echo "using JAVA_HOME=$JAVA_HOME"
exec ./gradlew build --no-daemon "$@"
