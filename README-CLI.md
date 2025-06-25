# Gatling Log Parser CLI

A command-line tool for parsing Gatling binary simulation.log files and outputting all data in CSV format.

## Overview

This CLI uses Gatling's internal binary log parser to extract all performance test data from simulation.log files and outputs it as CSV to stdout. It leverages the same parser that Gatling uses internally, ensuring compatibility and staying in sync with Gatling versions.

## Building

### Build Standalone Executable

```bash
sbt "gatling-app/stage"
```

This creates a standalone executable at `gatling-app/target/universal/stage/bin/gatling-simulation-parse`.

### Build for Development

```bash
sbt "gatling-app/compile"
```

## Running

### Standalone CLI (Recommended)

After building, you can run the CLI directly:

```bash
./gatling-app/target/universal/stage/bin/gatling-simulation-parse simulation.log
```

Or use the convenient wrapper script:

```bash
./gatling-simulation-parse simulation.log
```

### SBT Development Mode

```bash
sbt "gatling-app/runMain io.gatling.app.LogParserCli <absolute-path-to-simulation.log>"
```

**Note:** Use absolute paths with SBT as it runs from a different working directory.

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
./gatling-simulation-parse simulation.log | grep "^request.*,OK," | wc -l
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
./gatling-simulation-parse simulation.log | grep "^request.*,OK," | wc -l
```

Expected output: `97`

### Alternative Testing Methods

**Using the staged binary directly:**
```bash
./gatling-app/target/universal/stage/bin/gatling-simulation-parse simulation.log | grep "^request.*,OK," | wc -l
```

**Using SBT (requires absolute path):**
```bash
sbt "gatling-app/runMain io.gatling.app.LogParserCli $(pwd)/simulation.log" 2>&1 | grep "\[info\] request.*,OK," | wc -l
```