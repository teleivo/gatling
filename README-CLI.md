# Gatling Log Parser CLI

A fast, self-contained command-line tool to turn Gatling binary `simulation.log` files (starting
with [3.12+](https://github.com/gatling/gatling/issues/4596)) into a CSV.

## Overview

This CLI uses Gatling's internal binary log parser to extract all performance test data from
`simulation.log` files and outputs it as CSV to stdout. Built with Scala CLI and GraalVM Native
Image, it provides instant startup times while maintaining compatibility with Gatling's internal
format.

## Quick Start

1. Install [Scala CLI](https://scala-cli.virtuslab.org/)

2. Build the CLI

```sh
scala-cli package GatlingSimulationLogParser.scala --native-image --power --output glog
```

3. Run it

```sh
./glog simulation.log
```

## Development

### Building for Development

Compile and test with JVM (fast iteration):

```sh
scala-cli compile GatlingSimulationLogParser.scala
scala-cli run GatlingSimulationLogParser.scala -- simulation.log
```

Run tests:

```sh
scala-cli test GatlingSimulationLogParser.scala
```

Format code:

```sh
scala-cli fmt GatlingSimulationLogParser.scala
```

## Building

### Native Binary (Recommended for Distribution)

Build a self-contained native executable:

```sh
scala-cli package GatlingSimulationLogParser.scala --native-image --power --output glog
```

This creates:
- **Single executable file**: `glog` (~20MB)
- **No JVM dependency**: Runs on any compatible system
- **Instant startup**: <10ms vs 2-3s for JVM
- **Self-contained**: Like Go/Rust binaries

### JAR (For JVM environments)

Build a standalone JAR:

```sh
scala-cli package GatlingSimulationLogParser.scala --assembly --power --output glog.jar
```

## Running

### Native Binary (Recommended)

```sh
./glog simulation.log
```

### JVM (if using JAR)

```sh
java -jar glog.jar simulation.log
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

### Option 1: Download Pre-built Binary

TODO release to GH

1. Download the `glog` binary from releases
2. Make it executable: `chmod +x glog`
3. Run: `./glog simulation.log`

### Option 2: Build from Source

1. Install Scala CLI
2. Clone/download the source
3. Build: `scala-cli package GatlingSimulationLogParser.scala --native-image --power --output glog`
4. Run: `./glog simulation.log`

### Option 3: Use JVM Version

1. Install Java 17+
2. Build JAR: `scala-cli package GatlingSimulationLogParser.scala --assembly --power --output glog.jar`
3. Run: `java -jar glog.jar simulation.log`

