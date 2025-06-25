# Gatling Log Parser CLI

A command-line tool for parsing Gatling binary simulation.log files and outputting all data in CSV format.

## Overview

This CLI uses Gatling's internal binary log parser to extract all performance test data from simulation.log files and outputs it as CSV to stdout. It leverages the same parser that Gatling uses internally, ensuring compatibility and staying in sync with Gatling versions.

## Building

```bash
sbt "gatling-app/compile"
```

## Running

### Basic Usage

```bash
sbt "gatling-app/runMain io.gatling.app.LogParserCli <path-to-simulation.log>"
```

### Example

```bash
sbt "gatling-app/runMain io.gatling.app.LogParserCli ./simulation.log"
```

### Save Output to File

```bash
sbt "gatling-app/runMain io.gatling.app.LogParserCli ./simulation.log" > results.csv 2>/dev/null
```

### Count Successful Requests

```bash
sbt "gatling-app/runMain io.gatling.app.LogParserCli ./simulation.log" 2>&1 | grep "\[info\] request.*,OK," | wc -l
```

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

## Testing

The provided `simulation.log` contains 97 successful HTTP requests, which can be verified with:

```bash
sbt "gatling-app/runMain io.gatling.app.LogParserCli ./simulation.log" 2>&1 | grep "\[info\] request.*,OK," | wc -l
```

Expected output: `97`