# Build Analyzer CLI

A lightweight command-line tool that analyzes Maven (and later other build tools)
logs to highlight build performance bottlenecks – slow modules, phases, plugins,
tests.

The goal is to make build performance **transparent** using only log files:

- ❌ No changes to `pom.xml`
- ❌ No agents / profilers
- ❌ No commercial services
- ✅ Just parse the logs you already have

---

## Status

> **Phase 1 – Maven Reactor Summary MVP (DONE ✅)**  
> The project is still evolving. Interfaces and outputs may change.

Currently implemented:

- Parse a **single Maven build log**
- Extract per-module build times from **Reactor Summary**
- Compute:
   - Total build time
   - Sum of module times
   - Overhead (non-module time)
   - Share of each module in the whole build
- Show a **human-friendly text summary**
- Optional: output a **simple JSON report** (`totalSeconds + modules[]`)

Planned milestones:

1. **Maven Reactor Summary parser (MVP) – _current phase_**
   - ✅ Read a Maven log file
   - ✅ Extract per-module build times
   - ✅ Show the slowest modules in a text table
   - ✅ Optional JSON output for automation / dashboards

2. **Deeper Maven analysis**
   - Phase / plugin breakdown
   - Test execution breakdown (Surefire / Failsafe)
   - Basic “build health” hints  
     (e.g. modules taking > X% of total time, unstable module times)

3. **CI integration & dashboards**
   - Stable JSON schema for dashboards (v1)
   - Integration examples for Jenkins / GitHub Actions
   - (Maybe) a small web UI on top of the JSON reports

---

## Quick start (current CLI)

> Current implementation uses a simple Java CLI entrypoint.  
> A `ba` wrapper command may be added later.

### Build

```bash
mvn clean package
```

This produces a jar under `target/`.  
(You can also run the main class directly from your IDE while developing.)

### Run (text mode)

```bash
# From project root (log path is just an example)
java -cp target/classes com.buildanalyzer.cli.BuildAnalyzerCli sample-logs/build-parent.log
```

Example output (based on a 4-module Maven Reactor build):

```text
=== Build Analyzer CLI ===
Log file : sample-logs\build-parent.log

Total build time   : 8.294 s
Modules total time : 8.080 s (97.4% of build)
Other / overhead   : 0.214 s (2.6% of build)

Modules by time (share of whole build):
  1) core             4.637 s  (55.9% of build)
  2) service          1.648 s  (19.9% of build)
  3) webapp           1.548 s  (18.7% of build)
  4) parent-project   0.247 s  ( 3.0% of build)

Slowest module: core (4.637 s, 55.9% of build)
```

### Run (JSON mode)

Use `-j/--json` to get machine-readable output, and `-p/--pretty` to format it:

```bash
# compact JSON
java -cp target/classes com.buildanalyzer.cli.BuildAnalyzerCli \
  -j sample-logs/build-parent.log

# pretty-printed JSON
java -cp target/classes com.buildanalyzer.cli.BuildAnalyzerCli \
  -jp sample-logs/build-parent.log
```

Current JSON shape (v0, directly reflecting `BuildSummary`):

```json
{
  "totalSeconds": 8.294,
  "modules": [
    { "name": "parent-project", "seconds": 0.247 },
    { "name": "core",           "seconds": 4.637 },
    { "name": "service",        "seconds": 1.648 },
    { "name": "webapp",         "seconds": 1.548 }
  ]
}
```

> ⚠️ This is an early, minimal schema.  
> A more stable v1 schema (with tool metadata, build status, timestamps, etc.)
> is planned and documented below.

---

## Planned CLI UX (target design)

In the future, the CLI is intended to feel like a small “toolbox”:

Analyze a Maven build log and show a human-friendly summary:

```bash
ba analyze path/to/maven-build.log
```

Planned text output (sketch):

```text
Build Analyzer CLI 0.1  (Maven log)

Input log   : path/to/maven-build.log
Build tool  : Maven
Project     : parent-project
Status      : SUCCESS
Total time  : 2m 37s
Modules     : 4

Slowest modules (by duration)
------------------------------------------
#  Module           Time      Share
1  core             2m 33s    96.2%
2  webapp           1.73s     1.1%
3  service          1.63s     1.0%
4  parent-project   0.00s     0.0%
```

Get machine-readable JSON instead of text:

```bash
ba analyze path/to/maven-build.log --format json
```

Planned JSON shape (v1, **not implemented yet**):

```jsonc
{
  "schemaVersion": "1.0",
  "tool": {
    "name": "build-analyzer-cli",
    "version": "0.1"
  },
  "input": {
    "logPath": "path/to/maven-build.log",
    "buildTool": "maven"
  },
  "build": {
    "project": "parent-project",
    "status": "SUCCESS",
    "totalTimeSeconds": 157,
    "finishedAt": "2025-11-23T20:22:34Z"
  },
  "modules": [
    {
      "name": "core",
      "status": "SUCCESS",
      "timeSeconds": 153.0,
      "shareOfBuild": 0.962
    }
    // ...
  ]
}
```

---

## Tech stack

- Java 17
- Maven
- Simple custom CLI argument parser (`CliArgumentParser`)
- JSON: [Gson](https://github.com/google/gson) (for now)
- Testing: JUnit 5

---

## Development notes

- **Current focus**: Maven multi-module builds, using Reactor Summary + Total time.
- CI integration, aggregation over multiple builds, and richer JSON schemas are planned.
- The public API / JSON output should be considered **unstable** until v1 schema is finalized.
- Other build tools (e.g. Gradle) may be added later, once Maven support is solid.

---

## Roadmap (high-level)

Short-term:

- [x] Single-log analysis (Reactor Summary + Total time)
- [x] Text summary + JSON output
- [x] Basic CLI options: `-j/--json`, `-p/--pretty`
- [ ] Aggregate multiple logs (average / p95 / max per module)
- [ ] More robust parsing across different Maven log formats

Mid-term:

- [ ] Phase / plugin time breakdown
- [ ] Test execution breakdown (Surefire / Failsafe)
- [ ] “Build health” scoring / hints
- [ ] Stable v1 JSON schema

Long-term:

- [ ] CI examples (Jenkins, GitHub Actions, etc.)
- [ ] Simple web UI / dashboard on top of JSON reports
- [ ] Gradle & other build tools support
