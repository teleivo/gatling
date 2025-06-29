#!/bin/bash

# Build native binary for Gatling Log Parser CLI

set -e

echo "Building Gatling Log Parser CLI native binary..."

# First, build the assembly JAR
echo "Building assembly JAR..."
sbt "project gatling-log-parser-cli" "assembly"

# Find the assembly JAR
JAR_PATH=$(find target/scala-2.13 -name "*-assembly.jar" | head -1)

if [ -z "$JAR_PATH" ]; then
    echo "Error: Assembly JAR not found"
    exit 1
fi

echo "Found JAR: $JAR_PATH"

# Download GraalVM using Coursier if not available
if ! command -v native-image &> /dev/null; then
    echo "Native-image not found. Installing GraalVM via Coursier..."
    
    # Install Coursier if not available
    if ! command -v cs &> /dev/null; then
        echo "Installing Coursier..."
        curl -fL https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux.gz | gzip -d > cs
        chmod +x cs
        ./cs setup -y
        export PATH="$HOME/.local/share/coursier/bin:$PATH"
    fi
    
    # Install GraalVM
    cs java --jvm graalvm-community:21.0.2 --setup
    eval "$(cs java --jvm graalvm-community:21.0.2 --env)"
fi

# Build native image
echo "Building native image..."
native-image \
    -jar "$JAR_PATH" \
    -H:Name=gatling-log-parser \
    -H:+ReportExceptionStackTraces \
    --no-fallback \
    --enable-https \
    --enable-all-security-services \
    --install-exit-handlers \
    -H:+AddAllCharsets \
    --verbose

echo ""
echo "Success! Native binary created: ./gatling-log-parser"
echo ""
echo "To test it:"
echo "  ./gatling-log-parser --help"
echo "  ./gatling-log-parser simulation.log"