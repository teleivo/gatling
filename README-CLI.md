# Gatling Log Parser CLI

A user-friendly command-line tool for parsing Gatling binary simulation.log files and outputting all data in CSV format.

## Overview

This CLI uses Gatling's internal binary log parser to extract all performance test data from simulation.log files and outputs it as CSV to stdout. It leverages the same parser that Gatling uses internally, ensuring compatibility and staying in sync with Gatling versions.

## Quick Start

1. **Build the CLI:**
   ```bash
   sbt "project gatling-app" assembly
   ```

2. **Run with a simulation log:**
   ```bash
   ./gatling-simulation-parse simulation.log > results.csv
   ```

That's it! The CLI handles all the Java arguments automatically.

## Building

### Build Fat JAR (Recommended)

```bash
sbt "project gatling-app" assembly
```

This creates a standalone JAR at `gatling-app/target/gatling-simulation-parse.jar` and copies it to the root directory.

### Build for Development

```bash
sbt "project gatling-app" compile
```

## Running

### User-Friendly Wrapper Script (Recommended)

After building, use the wrapper script that handles all Java arguments automatically:

```bash
./gatling-simulation-parse simulation.log
```

### Portable Version

For distribution, you can use the portable version (JAR + script):

```bash
./gatling-simulation-parse-portable simulation.log
```

### Direct JAR Execution

If you prefer to run the JAR directly:

```bash
java --add-opens=java.base/java.lang=ALL-UNNAMED -jar gatling-simulation-parse.jar simulation.log
```

### SBT Development Mode

```bash
sbt "project gatling-app" "runMain io.gatling.app.LogParserCli simulation.log"
```

### Usage Examples

**Basic usage (clean CSV output):**
```bash
./gatling-simulation-parse simulation.log
```

**With debug logging enabled:**
```bash
./gatling-simulation-parse --debug simulation.log
```

**Save output to file:**
```bash
./gatling-simulation-parse simulation.log > results.csv
```

**Count successful requests:**
```bash
./gatling-simulation-parse simulation.log 2>/dev/null | grep "^request.*,OK," | wc -l
```

### Command Line Options

- `--debug`: Enable debug logging output. By default, only clean CSV data is output to stdout, with debug logs suppressed for clean CSV processing.
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

```sh
grep request results.csv | head -2
```

> [!NOTE]
> A request has no scenario name. In the OSS dashboard you can also not see anything about
> scenarios. Maybe scenarios are a pure enterprise features when it comes to visualization.
> The requests are grouped by the HTTP/request name in the details page. That name also shows up in
> the assertions on the global page.

**User Records** (`record_type=user`):
- `scenario_name`: Name of the scenario
- `event_type`: "start" or "end"
- `start_timestamp`: Unix timestamp in milliseconds

```sh
head -1 results.csv;grep user results.csv | head -2
```

**Group Records** (`record_type=group`):
- `group_hierarchy`: Pipe-separated group names
- `status`: "OK" or "KO"
- `start_timestamp`/`end_timestamp`: Unix timestamps in milliseconds
- `duration_ms`: Group duration in milliseconds
- `cumulated_response_time_ms`: Sum of response times

```sh
head -1 results.csv;grep group, results.csv | head -1
```

**Error Records** (`record_type=error`):
- `error_message`: Error description
- `start_timestamp`: Unix timestamp in milliseconds

> [!NOTE]
> Have not seen any yet.

## Requirements

- SBT (for building and running)
- Java 17+ with `--add-opens=java.base/java.lang=ALL-UNNAMED` (automatically configured)

## Technical Details

- Uses Gatling's internal `LogFileReader` from `gatling-charts` module
- Streams records as CSV output without loading entire file into memory
- Handles string caching and binary format parsing automatically
- CSV values are properly escaped for special characters (quotes, commas, newlines)
- Skips version compatibility checks to allow parsing logs from different Gatling versions
- **Clean Output**: By default, suppresses all debug logging to ensure clean CSV output suitable for piping and processing
- **Debug Mode**: Use `--debug` flag to enable full logging for troubleshooting

## Testing

The provided `simulation.log` contains 97 successful HTTP requests, which can be verified with:

```bash
./gatling-simulation-parse simulation.log 2>/dev/null | grep "^request.*,OK," | wc -l
```

Expected output: `97`

### Alternative Testing Methods

**Using the portable version:**
```bash
./gatling-simulation-parse-portable simulation.log 2>/dev/null | grep "^request.*,OK," | wc -l
```

**Using the JAR directly:**
```bash
java --add-opens=java.base/java.lang=ALL-UNNAMED -jar gatling-simulation-parse.jar simulation.log 2>/dev/null | grep "^request.*,OK," | wc -l
```

**Using SBT:**
```bash
sbt "project gatling-app" "runMain io.gatling.app.LogParserCli simulation.log" 2>&1 | grep -E '^request.*,OK,' | wc -l
```

## Distribution

To distribute the CLI tool, provide users with:

1. **gatling-simulation-parse.jar** - The fat JAR containing all dependencies
2. **gatling-simulation-parse-portable** - The wrapper script that handles Java arguments

Users can then run:
```bash
chmod +x gatling-simulation-parse-portable
./gatling-simulation-parse-portable simulation.log > results.csv
```
