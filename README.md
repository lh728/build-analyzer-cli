# Build Analyzer CLI

A lightweight command-line tool that analyzes Maven (and later other build tools)
logs to highlight build performance bottlenecks – slow modules, phases, plugins
and tests.

The goal is to make build performance **transparent** using only log files –
no changes to `pom.xml`, no extra agents, no commercial services.

---

## Status

> Work in progress 

Planned milestones:

1. **Maven Reactor Summary parser (MVP)**
    - Read a Maven log file
    - Extract per-module build times
    - Show the slowest modules in a text table
    - Optional: output a structured JSON report

2. **Deeper Maven analysis**
    - Phase / plugin breakdown
    - Test execution breakdown (Surefire/Failsafe)
    - Basic “build health” hints (e.g. modules taking > X% of total time)

3. **CI integration & dashboards**
    - Stable JSON schema for dashboards
    - Integration examples for Jenkins / GitHub Actions
    - (Maybe) a small web UI on top of the JSON reports

---

## Planned CLI usage

> Commands below describe the **target design** 

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

Planned JSON shape (v1):

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

## Tech stack (planned)

- Java 17
- Maven
- Lightweight CLI argument parser
- JSON library for reports
- JUnit for tests

---

## Development notes

- The project is still in an early design phase; interfaces and outputs may change.
- Maven will be supported first; other build tools (e.g. Gradle) may be added later.
