# Gatling Log Parser CLI

A fast command-line tool to turn Gatling binary `simulation.log` files (starting
with [3.12+](https://github.com/gatling/gatling/issues/4596)) into a CSV file.

## Overview

This CLI uses Gatling's internal binary log parser to extract all performance test data from
`simulation.log` files and outputs it as CSV to a file next to the original log file. Built as an integrated SBT module,
it uses Gatling's internal LogFileReader for maximum compatibility and performance.

## Quick Start

1. Run the CLI directly with SBT

```sh
sbt "project gatling-log-parser-cli" "run simulation.log"
```

2. Process multiple simulation.log files in subdirectories

```sh
sbt "project gatling-log-parser-cli" "run --scan-subdirs /path/to/results/"
```

3. For production use, build a package JAR first

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

### JAR (For Distribution)

Build a package JAR:

```sh
sbt "project gatling-log-parser-cli" package
```

This creates:
- **JAR file**: `gatling-log-parser-cli/target/scala-2.13/gatling-log-parser-cli-*.jar`
- **Integrated**: Uses Gatling's internal components directly
- **Dependencies**: Requires Gatling dependencies on classpath

## Running

### Using SBT (Recommended)

**Single file:**
```sh
sbt "project gatling-log-parser-cli" "run simulation.log"
```

**Directory with subdirectories:**
```sh
sbt "project gatling-log-parser-cli" "run --scan-subdirs /path/to/results/"
```

### Using the JAR (Advanced)

```sh
sbt "project gatling-log-parser-cli" package
java -cp "gatling-log-parser-cli/target/scala-2.13/gatling-log-parser-cli*.jar:$(sbt --error 'export gatling-log-parser-cli/Runtime/fullClasspath' | tail -1)" io.gatling.logparser.GatlingLogParserCli simulation.log
```

## Command Line Options

- `--debug`: Enable debug logging output. By default, only essential log messages are shown.
- `--scan-subdirs`: Scan immediate subdirectories for simulation.log files when the input is a directory without a direct simulation.log file.
- `<path>`: Path to simulation.log file or directory to scan

## Output Format

The CLI creates a CSV file next to the input simulation.log file (e.g., `simulation.csv` for `simulation.log`) with the following schema:

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

### Option 1: Use SBT directly (Recommended)

1. Install SBT and Java 17+
2. Clone the Gatling repository  
3. Run: `sbt "project gatling-log-parser-cli" "run simulation.log"`

## Usage Examples

**Process a single simulation.log file:**
```sh
sbt "project gatling-log-parser-cli" "run /path/to/simulation.log"
```

**Process a directory containing simulation.log:**
```sh
sbt "project gatling-log-parser-cli" "run /path/to/gatling-results/"
```

**Process all simulation.log files in subdirectories:**
```sh
sbt "project gatling-log-parser-cli" "run --scan-subdirs /path/to/results/"
```

**Use case: Multiple test runs**
If you have a directory structure like:
```
results/
├── run1/
│   └── simulation.log
├── run2/
│   └── simulation.log
└── run3/
    └── simulation.log
```

Use `--scan-subdirs` to process all runs at once:
```sh
sbt "project gatling-log-parser-cli" "run --scan-subdirs results/"
```

This will create `simulation.csv` files next to each `simulation.log` file.

### Option 2: Build JAR from Source

1. Install SBT and Java 17+
2. Clone the Gatling repository
3. Build: `sbt "project gatling-log-parser-cli" package`
4. Run: `java -cp "gatling-log-parser-cli/target/scala-2.13/gatling-log-parser-cli*.jar:$(sbt --error 'export gatling-log-parser-cli/Runtime/fullClasspath' | tail -1)" io.gatling.logparser.GatlingLogParserCli simulation.log`

