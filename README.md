# Build Analyzer CLI

A lightweight command-line tool that analyzes Maven (and later other build tools)
logs to highlight build performance bottlenecks – slow modules, tests, and
overall build “shape”.

The goal is to make build performance **transparent** using only log files:

- ❌ No changes to `pom.xml`
- ❌ No agents / profilers
- ❌ No commercial services
- ✅ Just parse the logs you already have

---

## Status

> **Maven log analysis CLI (in progress)**  
> Interfaces and outputs may still change.

### Currently implemented

**Single build analysis**

- Parse a **single Maven build log**
- Extract per-module build times from **Reactor Summary**
- Compute:
    - Total build time (wall clock)
    - **Serial logs:** sum of module times, “overhead” (time not accounted for in modules), and share of each module
    - **Parallel logs (`-T`):** module ranking + parallel-friendly metrics (see below)
- Extract additional per-module metrics (best effort):
    - Test metrics (Surefire):
        - `testsRun`, `failures`, `errors`, `skipped`
        - `testTimeSeconds`
        - “test time as % of module time” (serial logs only)
    - Compilation workload:
        - `mainSourceFiles`, `testSourceFiles`
- Show a **human-friendly text summary**
- Optional: output a **JSON report** (directly serializing `BuildSummary`)

**Multiple builds / history**

- Analyze multiple logs at once and compute cross-build stats:
    - Average / min / max total build time
    - Per-module aggregated stats:
        - `averageSeconds`, `minSeconds`, `maxSeconds`, `buildCount`
        - `averageTestSeconds`, `minTestSeconds`, `maxTestSeconds`
        - `totalTestsRun`, `totalFailures`, `totalErrors`, `totalSkipped`
        - `averageMainSourceFiles`, `averageTestSourceFiles`
- Text report for aggregated builds:
    - Modules by **average total time**
    - Modules by **average test time**
    - Average compilation workload per build

**Maven wrapper: run + analyze in one go**

- `--clean-install` mode:
    - Run `mvn clean install` (or Maven Wrapper `mvnw/mvnw.cmd` if present)
    - Capture the entire build output to a log file under:
        - `<project>/.build-analyzer/logs/clean-install-YYYYMMDD-HHmmss.log`
    - Immediately parse that log with Build Analyzer CLI and show a report
    - **Note:** `--clean-install` rejects parallel Maven builds (`-T/--threads`) to keep per-module metrics reliable.
- Additional Maven args can be passed through using `--` (see usage below)

---

## Quick start

> Current implementation is a Java CLI.  
> A `ba` wrapper script/alias may be added later.

### Build

```bash
mvn clean package
```

This produces a JAR under `target/`, e.g.:

```text
target/build-analyzer-cli-1.0-SNAPSHOT.jar
```

You can also run the main class directly from your IDE while developing.

### Run (single log, text mode)

```bash
# From project root (log path is just an example)
java -jar target/build-analyzer-cli-1.0-SNAPSHOT.jar   sample-logs/build-parent.log
```

Example output (based on a 4-module Maven Reactor build):

```text
=== Build Analyzer CLI ===
Log file : sample-logs/build-parent.log

Total build time   : 8.294 s
Modules total time : 8.080 s (97.4% of build)
Other / overhead   : 0.214 s (2.6% of build)

Modules by time (share of whole build):
  1) core             4.637 s  (55.9% of build)
  2) service          1.648 s  (19.9% of build)
  3) webapp           1.548 s  (18.7% of build)
  4) parent-project   0.247 s  ( 3.0% of build)

Slowest module: core (4.637 s, 55.9% of build)

Test breakdown per module:
  parent-project: no tests detected
  core: tests 1 (F:0, E:0, S:0) in 0.064 s (1.4% of module time)
  service: tests 1 (F:0, E:0, S:0) in 0.075 s (4.6% of module time)
  webapp: tests 1 (F:0, E:0, S:0) in 0.064 s (4.1% of module time)

Compilation workload (source files):
  parent-project: main 0, test 0
  core          : main 1, test 1
  service       : main 1, test 1
  webapp        : main 1, test 1
```

### Run (single log, JSON mode)

Use `-j/--json` to get machine-readable output, and `-p/--pretty` to format it:

```bash
# compact JSON
java -jar target/build-analyzer-cli-1.0-SNAPSHOT.jar   -j sample-logs/build-parent.log

# pretty-printed JSON
java -jar target/build-analyzer-cli-1.0-SNAPSHOT.jar   -jp sample-logs/build-parent.log
```

Current JSON shape (v0, directly reflecting `BuildSummary`):

```jsonc
{
  "totalSeconds": 8.294,
  "modules": [
    {
      "name": "parent-project",
      "seconds": 0.247,
      "testsRun": 0,
      "failures": 0,
      "errors": 0,
      "skipped": 0,
      "testTimeSeconds": 0.0,
      "mainSourceFiles": 0,
      "testSourceFiles": 0
    },
    {
      "name": "core",
      "seconds": 4.637,
      "testsRun": 1,
      "failures": 0,
      "errors": 0,
      "skipped": 0,
      "testTimeSeconds": 0.064,
      "mainSourceFiles": 1,
      "testSourceFiles": 1
    }
    // ...
  ]
}
```

> ⚠️ This is an early, minimal schema directly tied to internal models.  
> For parallel logs (`-T`), **per-module** test/compile fields in this v0 JSON may be unreliable due to interleaved output.
> Prefer non-parallel logs for full fidelity.

---

## CLI usage

General form:

```text
build-analyzer [options] <maven-log-file>
build-analyzer --dir <log-directory>
build-analyzer --aggregate <glob-pattern>
build-analyzer --clean-install [<project-dir>] [-- <maven-args...>]
```

With the JAR:

```bash
java -jar target/build-analyzer-cli-1.0-SNAPSHOT.jar [args...]
```

### Modes

The CLI currently supports four modes:

1. **Single log (default)**

   Analyze a single Maven log file:

   ```bash
   build-analyzer sample-logs/build-parent.log
   ```

   This mode is selected automatically when you pass a positional argument and
   no other mode flags (`--dir/--aggregate/--clean-install`) are set.

2. **Directory aggregation (`--dir` / `-d`)**

   Analyze all `*.log` files directly under a directory:

   ```bash
   build-analyzer --dir ci-logs/
   ```

    - Lists all regular files in `ci-logs/` ending with `.log`
    - Parses each log as a Maven build (skipping invalid ones with a warning)
    - Prints aggregated statistics:
        - Overall build time stats (avg/min/max)
        - Per-module average/min/max time
        - Per-module average test time and total failures
        - Per-module average compilation workload

3. **Pattern aggregation (`--aggregate` / `-a`)**

   Analyze log files matching a glob pattern (applied to the file name part):

   ```bash
   build-analyzer --aggregate "ci-logs/build-*.log"
   ```

   Internally this:
    - Splits directory and pattern (e.g. `ci-logs/` + `build-*.log`)
    - Uses Java’s `DirectoryStream` glob support
    - Aggregates metrics across all matching logs

4. **Run Maven + analyze (`--clean-install` / `-C`)**

   Run `mvn clean install` (or Maven Wrapper if available), capture the output
   as a log, and analyze it:

   ```bash
   # In a Maven project directory
   build-analyzer --clean-install
   
   # Specify project directory explicitly
   build-analyzer --clean-install path/to/project
   
   # Pass extra arguments to Maven using `--`
   build-analyzer --clean-install path/to/project -- -DskipTests=false
   ```

   Behavior:

    - Determines the project directory (default = current directory `.`)
    - Validates that it contains a `pom.xml`
    - Chooses Maven command:
        - If `mvnw`/`mvnw.cmd` exists in the project → use wrapper
        - Otherwise:
            - On Windows: `mvn.cmd`
            - On Unix-like systems: `mvn`
    - Runs: `mvn clean install [extra-maven-args...]`
    - Captures combined stdout/stderr to:

      ```text
      <project>/.build-analyzer/logs/clean-install-YYYYMMDD-HHmmss.log
      ```

    - Streams output live to your console while building
    - If Maven exits with non-zero code:
        - Prints an error and the log path
        - Exits with the same code
    - If Maven succeeds:
        - Parses the captured log
        - Prints the same single-build report as in “single log” mode
        - (Or JSON if `-j/--json` is used)

   **Parallel builds:** `--clean-install` does **not** allow `-T/--threads`.
    - If `-T/--threads` is passed via `--`, the command exits with an error.
    - If parallel build is enabled via project config (e.g. `.mvn/maven.config`), it will also be rejected once detected from the captured log.
    - If you need `-T`, run Maven yourself and analyze the generated log via “single log” / “dir” / “aggregate”. (Output may degrade; see below.)

   The captured logs can later be aggregated, e.g.:

   ```bash
   build-analyzer --dir .build-analyzer/logs
   # or
   build-analyzer --aggregate ".build-analyzer/logs/clean-install-*.log"
   ```

### Common options

- `-j, --json`  
  Output JSON instead of text.

- `-p, --pretty`  
  Pretty-print JSON (requires `-j/--json`).

- `-d, --dir <dir>`  
  Aggregate all `.log` files directly under `<dir>`.

- `-a, --aggregate <pattern>`  
  Aggregate log files matching a glob pattern inside a directory
  (e.g. `ci-logs/build-*.log`).

- `-C, --clean-install [<project-dir>] [-- <maven-args...>]`  
  Run `clean install` in the given project directory (default: current dir),
  capture the build log, and analyze it.  
  Use `--` to pass additional arguments to Maven.  
  Note: `-T/--threads` is not supported in `--clean-install`.

---

## What data is extracted from Maven logs?

The current Maven parser (`MavenLogParser`) works purely on text logs and extracts:

- **Total build time**  
  From lines like:

  ```text
  [INFO] Total time:  8.294 s
  ```

  Supports seconds, milliseconds, minutes, and a few common textual variants.

- **Per-module build times**  
  From Reactor Summary:

  ```text
  [INFO] Reactor Summary for parent-project 1.0-SNAPSHOT:
  [INFO] parent-project ..................................... SUCCESS [  0.247 s]
  [INFO] core ............................................... SUCCESS [  4.637 s]
  [INFO] service ............................................ SUCCESS [  1.648 s]
  [INFO] webapp ............................................. SUCCESS [  1.548 s]
  ```

- **Per-module test metrics (Surefire)**  
  From Surefire output:

  ```text
  [INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.064 s -- in com.example.AppTest
  ```

  Aggregated per module into:

    - `testsRun`, `failures`, `errors`, `skipped`
    - `testTimeSeconds`
    - Test time as a percentage of module time (for text output, serial logs)

- **Per-module compilation workload**

  From compiler plugin output:

  ```text
  [INFO] Compiling 1 source file with javac [debug release 17] to target\classes
  [INFO] Compiling 1 source file with javac [debug release 17] to target\test-classes
  ```

  Counted separately as:

    - `mainSourceFiles`
    - `testSourceFiles`

These metrics are then:

- Rendered for a single log (`SingleBuildTextPrinter`)
- Aggregated across multiple logs (`BuildAggregator` + `AggregatedTextPrinter`)

### Parallel Maven builds (`-T`) and output degradation

Build Analyzer CLI is log-only. When Maven runs with `-T` (parallel build),
log lines from different modules can be interleaved, which makes **per-module**
attribution for tests/compilation unreliable.

Current behavior:

- **Still reliable (supported):**
    - Total build time from `Total time: ...` (**wall clock**)
    - Per-module durations from **Reactor Summary**
    - Slowest modules / hotspot ranking based on Reactor Summary

- **Degraded / disabled in text output for parallel logs:**
    - “Modules total time” and “overhead” (module times overlap under `-T`)
    - Per-module test breakdown (Surefire) and compilation workload attribution
    - Instead, the text report may show **build-wide totals** (aggregated across modules)
      and parallel-friendly metrics such as:
        - work sum (sum of module durations)
        - work / wall ratio
        - critical-path estimate (max module duration)

Recommendation:

- For full per-module fidelity, prefer non-parallel logs (`-T1` or no `-T`).
- For parallel CI builds, the tool is still useful to identify **critical-path candidates**
  and consistently slow modules via Reactor Summary rankings.

---

## Tech stack

- Java 17
- Maven
- Custom CLI argument parser (`CliArgumentParser`)
- JSON: [Gson](https://github.com/google/gson) (for now)
- Testing: JUnit 5 (for core parser & CLI behavior)


