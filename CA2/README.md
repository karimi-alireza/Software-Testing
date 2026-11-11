# Airplane Simulator

1. A new aircraft starts in `GROUNDED` state.
2. `prepareForTakeoff()` checks that the engine is operational, enough fuel is
   available, and a route is available. On success, the state becomes `READY`.
3. `takeOff()` starts the engine, retracts the landing gear, and changes the
   state to `IN_FLIGHT`. If startup or retraction fails, it becomes `ABORTED`
   and attempts to deploy the gear and stop the engine.
4. `cruise(amount)` consumes fuel. If the engine is no longer operational, the
   aircraft makes an emergency landing. A fuel-consumption failure also causes
   an emergency landing and is reported as a `FlightException`. An exception
   while reading engine status also triggers emergency landing and a
   `FlightException` preserving that query exception as its cause.
5. `land()` and `emergencyLand()` deploy the landing gear, stop the engine,
   and change the state to `LANDED` only if both commands succeed. Shutdown is
   attempted even if deployment fails; incomplete landing remains `IN_FLIGHT`.

An invalid lifecycle operation (for example, calling `takeOff()` before
preparation) throws `FlightException`.

- `FlightException` is an unchecked exception.
- Fuel constructor inputs must be finite and nonnegative. Consumption and
  cruise inputs must be finite and positive. Invalid numeric input throws
  `IllegalArgumentException` before changing fuel or flight state.
- If recovery also fails, the initiating exception remains the cause of the
  `FlightException`; recovery failures are retained as suppressed exceptions.
  If both recovery commands fail, shutdown is suppressed under deployment.
- `Engine.fail()` can simulate an engine failure during a flight.
- `NavigationSystem.setRouteAvailable(...)` can change route availability.
- Mockito and JUnit 5 dependencies are already configured with test scope in `pom.xml`.

## Run the console demo

From this folder, with Java 17+ and Maven installed:

```sh
mvn -q compile
java --class-path target/classes scripts/FlightDemo.java
```

The demo runs the actual simulator with real components and prints the aircraft
status, engine state, gear state and remaining fuel. It shows a normal flight,
an engine failure, and insufficient fuel followed by emergency landing. This is
a console program; the project has no graphical interface. The application JAR
has no `Main-Class`, so use the command above to launch the demo.

## Completed CA2 test suite

Requires Java 17+ and Maven. From this folder:

```sh
mvn clean verify
```

Maven downloads the pinned test/build dependencies on the first run. `verify`
runs all **134 cases** (71 test methods, including parameterized cases), packages
the application, and generates coverage. There are no disabled tests.

- JUnit results: `target/surefire-reports/`
- Coverage: `target/site/jacoco/index.html` (HTML) and `jacoco.xml` (machine-readable)
- Mockito-focused tests only: `mvn -Dtest=AircraftTest,AircraftRecoveryTest test`
- Optional sensitivity check after a successful build: `python3 scripts/check-test-sensitivity.py`

The sensitivity script uses 19 faults in temporary project copies, runs Maven
offline, and fails if a fault escapes assertion detection. It requires Python 3
and an already populated Maven cache. For a custom cache, pass
`--maven-repo /path/to/cache` to the script, and use the same
`-Dmaven.repo.local=/path/to/cache` in Maven commands.

Tests use Mockito's subclass mock maker: these non-final collaborators do not
need dynamic JVM agent attachment. Mockito and JUnit remain test-scoped.
Following the assessment, production validation and failure recovery were fixed
at the user's request. The aircraft under test remains real; production contains
no test-specific switches or mock behavior.

Read the submission materials:

- [Requirements and traceability](docs/REQUIREMENTS.md): every assignment requirement, required Mockito API, and behavioral test mapping.
- [Test design and explanations](docs/TEST-REPORT.md): mock/stub answers, design decisions, oral-assessment preparation, and known gaps.
- [Verification results](docs/VERIFICATION.md): executed checks and coverage/sensitivity evidence.
- [Submission checklist](docs/SUBMISSION.md): team, GitHub visibility, deadline, commit hash, and eLearn handoff.

The regression suite includes the formerly missing boundaries, persistent engine
failure, non-finite input rejection and compound recovery failures. Run the two
independent numeric checks after building:

```sh
java --class-path target/classes scripts/NonFiniteFuelProbe.java
java --class-path target/classes scripts/CheckFiniteFuelContract.java
```

The former-corruption probe now rejects all four examples, and the finite-input
checker reports **12 passed / 0 failed**. Both exit with code 0. Equivalent input
cases also run as ordinary Maven tests; no failing diagnostic is excluded to keep
the build green. The [domain review](docs/DOMAIN-REVIEW.md) explains the adopted
recovery policy and the remaining limits of this small simulator.
