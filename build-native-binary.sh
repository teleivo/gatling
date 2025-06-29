#!/bin/bash

# Build native binary for Gatling Log Parser CLI
# This script builds a standalone native binary using GraalVM

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
cd "$SCRIPT_DIR"

echo "=== Gatling Log Parser Native Binary Builder ==="
echo

# Check for Docker
if ! command -v docker &> /dev/null; then
    echo "Error: Docker is required but not installed."
    echo "Please install Docker: https://docs.docker.com/get-docker/"
    exit 1
fi

# Step 1: Build assembly JAR
echo "Step 1: Building assembly JAR..."
sbt "project gatling-log-parser-cli" "clean" "assembly"

# Find the assembly JAR
ASSEMBLY_JAR=$(find gatling-log-parser-cli/target -name "*-assembly.jar" | head -1)
if [ -z "$ASSEMBLY_JAR" ]; then
    echo "Error: Assembly JAR not found"
    exit 1
fi
echo "Found assembly JAR: $ASSEMBLY_JAR"

# Step 2: Build native image using Docker
echo
echo "Step 2: Building native image using Docker..."
ASSEMBLY_JAR_BASENAME=$(basename "$ASSEMBLY_JAR")

# Build and extract the native image
docker run --rm \
    -v "$PWD/gatling-log-parser-cli/target:/project" \
    -v "$PWD:/output" \
    -w /project \
    --entrypoint=/bin/sh \
    ghcr.io/graalvm/native-image-community:21-muslib \
    -c "native-image -jar '$ASSEMBLY_JAR_BASENAME' -H:Name=gatling-log-parser -H:+ReportExceptionStackTraces --no-fallback --static --libc=musl && cp gatling-log-parser /output/"

# Step 3: Verify binary was created
if [ -f "./gatling-log-parser" ]; then
    chmod +x ./gatling-log-parser
    
    echo
    echo "=== Build Complete ==="
    echo "Native binary created: ./gatling-log-parser"
    echo
    echo "Binary details:"
    file ./gatling-log-parser
    ls -lh ./gatling-log-parser
    echo
    echo "To test the binary:"
    echo "  ./gatling-log-parser --help"
    echo "  ./gatling-log-parser simulation.log"
else
    echo "Error: Native binary was not created"
    echo "Check if Docker is running and try again"
    exit 1
fi