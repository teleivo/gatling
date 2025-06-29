#!/bin/bash

# Build script for Gatling Log Parser CLI native binary using GraalVM

set -e

GRAALVM_VERSION="21.0.2"
ARCH=$(uname -m)
OS=$(uname -s | tr '[:upper:]' '[:lower:]')

# Map architecture names
case "$ARCH" in
    x86_64)
        ARCH="amd64"
        ;;
    aarch64)
        ARCH="aarch64"
        ;;
    *)
        echo "Unsupported architecture: $ARCH"
        exit 1
        ;;
esac

# Download GraalVM if not present
GRAALVM_DIR="./graalvm-ce-java21-${GRAALVM_VERSION}"
if [ ! -d "$GRAALVM_DIR" ]; then
    echo "Downloading GraalVM ${GRAALVM_VERSION}..."
    URL="https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAALVM_VERSION}/graalvm-community-jdk-${GRAALVM_VERSION}_${OS}-${ARCH}_bin.tar.gz"
    curl -L "$URL" | tar xz
    mv "graalvm-community-openjdk-${GRAALVM_VERSION}"* "$GRAALVM_DIR"
fi

# Set JAVA_HOME and PATH
export JAVA_HOME="$(pwd)/$GRAALVM_DIR"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Using GraalVM at: $JAVA_HOME"
java -version

# Build the native image
echo "Building native image..."
sbt "project gatling-log-parser-cli" "assembly"

# Get the JAR path
JAR_PATH=$(find gatling-log-parser-cli/target/scala-2.13 -name "*-assembly.jar" | head -1)

if [ -z "$JAR_PATH" ]; then
    echo "Error: Assembly JAR not found. Please run 'sbt \"project gatling-log-parser-cli\" assembly' first"
    exit 1
fi

echo "Building native image from: $JAR_PATH"

# Build native image
native-image \
    -jar "$JAR_PATH" \
    -H:Name=gatling-log-parser \
    -H:+ReportExceptionStackTraces \
    --no-fallback \
    --enable-https \
    --enable-all-security-services \
    --install-exit-handlers

echo "Native binary created: ./gatling-log-parser"
echo ""
echo "To test the binary, run:"
echo "  ./gatling-log-parser --help"
echo "  ./gatling-log-parser simulation.log"