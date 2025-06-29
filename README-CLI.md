# Gatling Log Parser CLI

A fast command-line tool to turn Gatling binary `simulation.log` files (starting
with [3.12+](https://github.com/gatling/gatling/issues/4596)) into a CSV.

## Overview

This CLI uses Gatling's internal binary log parser to extract all performance test data from
`simulation.log` files and outputs it as CSV to stdout. Built as an integrated SBT module,
it uses Gatling's internal LogFileReader for maximum compatibility and performance.

## Quick Start

### Option 1: Run with SBT (Recommended)

```sh
sbt "project gatling-log-parser-cli" "run simulation.log"
```

### Option 2: Build Native Binary (Fast Startup)

```sh
# Build the native binary (requires Docker)
./build-native-binary.sh

# Run the native binary
./gatling-log-parser simulation.log
```

### Option 3: JAR (Advanced)

```sh
sbt "project gatling-log-parser-cli" package
java -cp "gatling-log-parser-cli/target/scala-2.13/gatling-log-parser-cli*.jar:$(sbt --error 'export gatling-log-parser-cli/Runtime/fullClasspath' | tail -1)" io.gatling.logparser.GatlingLogParserCli simulation.log
```

## Development

### Building for Development

Compile and test with SBT (fast iteration):

```sh
sbt "project gatling-log-parser-cli" compile
sbt "project gatling-log-parser-cli" run simulation.log
```

Run tests:

```sh
sbt "project gatling-log-parser-cli" test
```

Format code (automatic during compilation):

```sh
sbt "project gatling-log-parser-cli" spotlessApply
```

## Building

### Native Binary (Recommended for Distribution)

Build a standalone native binary with fast startup:

```sh
./build-native-binary.sh
```

This creates:
- **Native binary**: `./gatling-log-parser`
- **Fast startup**: Sub-second startup time
- **Self-contained**: No JVM or dependencies required
- **Cross-platform**: Builds Linux binaries on any platform via Docker

**Requirements**: Docker (for consistent Linux builds)

### JAR (For Development)

Build a package JAR:

```sh
sbt "project gatling-log-parser-cli" package
```

This creates:
- **JAR file**: `gatling-log-parser-cli/target/scala-2.13/gatling-log-parser-cli-*.jar`
- **Integrated**: Uses Gatling's internal components directly
- **Dependencies**: Requires Gatling dependencies on classpath

### Assembly JAR (Fat JAR)

Build a self-contained JAR with all dependencies:

```sh
sbt "project gatling-log-parser-cli" assembly
```

This creates:
- **Assembly JAR**: `gatling-log-parser-cli/target/gatling-log-parser-cli-*-assembly.jar`
- **Self-contained**: Includes all dependencies
- **Requires JVM**: Java 17+ required to run

## Running

### Native Binary (Fastest)

```sh
# After building with ./build-native-binary.sh
./gatling-log-parser simulation.log
./gatling-log-parser --debug simulation.log
```

### Using SBT (Development)

```sh
sbt "project gatling-log-parser-cli" "run simulation.log"
sbt "project gatling-log-parser-cli" "run --debug simulation.log"
```

### Using Assembly JAR

```sh
sbt "project gatling-log-parser-cli" assembly
java -jar gatling-log-parser-cli/target/*-assembly.jar simulation.log
```

### Using Package JAR (Advanced)

```sh
sbt "project gatling-log-parser-cli" package
java -cp "gatling-log-parser-cli/target/scala-2.13/gatling-log-parser-cli*.jar:$(sbt --error 'export gatling-log-parser-cli/Runtime/fullClasspath' | tail -1)" io.gatling.logparser.GatlingLogParserCli simulation.log
```

## Command Line Options

- `--debug`: Enable debug logging output. By default, only clean CSV data is output to stdout.
- `<simulation.log>`: Path to the Gatling binary simulation log file

## Output Format

The CLI outputs CSV data with the following schema:

### CSV Headers
```
record_type,scenario_name,group_hierarchy,request_name,status,start_timestamp,end_timestamp,response_time_ms,error_message,event_type,duration_ms,cumulated_response_time_ms,is_incoming
```

### Record Types

**Request Records** (`record_type=request`):
- `group_hierarchy`: Pipe-separated group names (e.g., "Group1|SubGroup")
- `request_name`: HTTP request name/URL
- `status`: "OK" or "KO"
- `start_timestamp`/`end_timestamp`: Unix timestamps in milliseconds
- `response_time_ms`: Response time in milliseconds
- `error_message`: Error description (if any)
- `is_incoming`: "true" for unmatched incoming events, "false" for regular requests

**User Records** (`record_type=user`):
- `scenario_name`: Name of the scenario
- `event_type`: "start" or "end"
- `start_timestamp`: Unix timestamp in milliseconds

**Group Records** (`record_type=group`):
- `group_hierarchy`: Pipe-separated group names
- `status`: "OK" or "KO"
- `start_timestamp`/`end_timestamp`: Unix timestamps in milliseconds
- `duration_ms`: Group duration in milliseconds
- `cumulated_response_time_ms`: Sum of response times

**Error Records** (`record_type=error`):
- `error_message`: Error description
- `start_timestamp`: Unix timestamp in milliseconds

## Installation

### Option 1: Native Binary (Recommended for Production)

1. Install Docker
2. Clone the Gatling repository
3. Build: `./build-native-binary.sh`
4. Run: `./gatling-log-parser simulation.log`

**Benefits**: 
- ⚡ Fast startup (sub-second)
- 📦 Self-contained (no JVM required)
- 🚀 Small binary size
- 🐧 Works on any Linux system

### Option 2: Use SBT directly (Recommended for Development)

1. Install SBT and Java 17+
2. Clone the Gatling repository  
3. Run: `sbt "project gatling-log-parser-cli" "run simulation.log"`

**Benefits**: 
- 🔧 Easy development and testing
- 🔄 Fast iteration cycles
- 🐛 Better debugging support

### Option 3: Build JAR from Source

1. Install SBT and Java 17+
2. Clone the Gatling repository
3. Build: `sbt "project gatling-log-parser-cli" assembly`
4. Run: `java -jar gatling-log-parser-cli/target/*-assembly.jar simulation.log`

**Benefits**: 
- ☕ Works with any Java 17+ installation
- 📋 Self-contained JAR with all dependencies
- 🔧 Good for CI/CD pipelines

## Performance Comparison

| Method | Startup Time | Memory Usage | Distribution |
|--------|--------------|--------------|---------------|
| Native Binary | ~50ms | ~20MB | Single file |
| SBT | ~5-10s | ~200MB | Full project |
| Assembly JAR | ~2-3s | ~100MB | Single JAR (~45MB) |
| Package JAR | ~2-3s | ~100MB | JAR + dependencies |

