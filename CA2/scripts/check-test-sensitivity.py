#!/usr/bin/env python3
"""Check representative faults in disposable copies; never edit the working SUT.

Usage: python3 scripts/check-test-sensitivity.py [--maven-repo /path/to/cache]
This is a small sensitivity experiment, not an exhaustive mutation score.
"""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--maven-repo", type=Path)
args = parser.parse_args()
project = Path(__file__).resolve().parents[1]
mutations = [
    ("missing engine start", "airplane/Aircraft.java", "            engine.start();", "            // omitted start"),
    ("gear deploys during takeoff", "airplane/Aircraft.java", "landingGear.retract();", "landingGear.deploy();"),
    ("cruise fails to deduct fuel", "airplane/Aircraft.java", "fuelSystem.consumeFuel(fuelAmount);", "// omitted consumption"),
    ("failed engine keeps flying", "airplane/Aircraft.java", "            emergencyLand();\n            return;", "            return;"),
    ("fuel failure is swallowed", "airplane/Aircraft.java", 'throw new FlightException("A problem occurred while consuming fuel.", exception);', "// swallowed failure"),
    ("takeoff ignores state guard", "airplane/Aircraft.java", "        ensureStatusIs(FlightStatus.READY);", "        // omitted state guard"),
    ("exact takeoff fuel is rejected", "airplane/components/FuelSystem.java", "remainingFuel >= minimumTakeoffFuel", "remainingFuel > minimumTakeoffFuel"),
    ("zero fuel consumption is accepted", "airplane/components/FuelSystem.java", "amount <= 0", "amount < 0"),
    ("tiny negative initial fuel is accepted", "airplane/components/FuelSystem.java", "initialFuel < 0", "initialFuel < -0.001"),
    ("tiny negative minimum fuel is accepted", "airplane/components/FuelSystem.java", "minimumTakeoffFuel < 0", "minimumTakeoffFuel < -0.001"),
    ("failed start silently repairs engine", "airplane/components/Engine.java",
     'throw new IllegalStateException("The engine is not operational.");',
     'operational = true;\n            throw new IllegalStateException("The engine is not operational.");'),
    ("non-finite initial fuel is accepted", "airplane/components/FuelSystem.java", "!Double.isFinite(initialFuel)", "false"),
    ("non-finite minimum fuel is accepted", "airplane/components/FuelSystem.java", "!Double.isFinite(minimumTakeoffFuel)", "false"),
    ("non-finite consumption is accepted", "airplane/components/FuelSystem.java", "!Double.isFinite(amount)", "false"),
    ("non-finite cruise is accepted", "airplane/Aircraft.java", "!Double.isFinite(fuelAmount)", "false"),
    ("aborted takeoff skips cleanup", "airplane/Aircraft.java", "recoverAfterFailure(exception, this::deployGearAndStopEngine);", "// omitted takeoff cleanup"),
    ("engine query failure skips emergency landing", "airplane/Aircraft.java",
     'recoverAfterFailure(exception, this::emergencyLand);\n            throw new FlightException("Cannot read engine status.", exception);',
     'throw new FlightException("Cannot read engine status.", exception);'),
    ("gear failure skips engine shutdown", "airplane/Aircraft.java",
     'failure = exception;\n        }\n        try {\n            engine.stop();',
     'throw exception;\n        }\n        try {\n            engine.stop();'),
    ("recovery masks original failure", "airplane/Aircraft.java",
     "addSuppressedIfDifferent(failure, recoveryFailure);", "throw recoveryFailure;"),
]

with tempfile.TemporaryDirectory(prefix="ca2-sensitivity-") as temporary:
    copy = Path(temporary) / "project"
    shutil.copytree(project, copy, ignore=shutil.ignore_patterns("target", ".git", ".idea", "__pycache__"))
    command = ["mvn", "-B", "-o"]
    if args.maven_repo:
        command.append(f"-Dmaven.repo.local={args.maven_repo.resolve()}")
    command += ["clean", "test"]

    def run_tests(label):
        result = subprocess.run(command, cwd=copy, capture_output=True, text=True, timeout=180)
        reports = list((copy / "target/surefire-reports").glob("TEST-*.xml"))
        roots = [ET.parse(p).getroot() for p in reports]
        failures = sum(int(r.get("failures", 0)) for r in roots)
        errors = sum(int(r.get("errors", 0)) for r in roots)
        tests = sum(int(r.get("tests", 0)) for r in roots)
        skipped = sum(int(r.get("skipped", 0)) for r in roots)
        if not reports:
            raise RuntimeError(f"{label}: no test results; build problem:\n{result.stdout}\n{result.stderr}")
        return result, failures, errors, tests, skipped

    result, failures, errors, baseline_tests, skipped = run_tests("baseline")
    if result.returncode != 0 or failures or errors or skipped:
        raise RuntimeError(f"Baseline must pass:\n{result.stdout}\n{result.stderr}")
    print("PASS: unmodified baseline", flush=True)

    for label, relative, old, new in mutations:
        path = copy / "src/main/java" / relative
        original = path.read_text()
        if original.count(old) != 1:
            raise RuntimeError(f"Mutation no longer matches uniquely: {label}")
        try:
            path.write_text(original.replace(old, new))
            result, failures, errors, tests, skipped = run_tests(label)
            if result.returncode == 0 or failures == 0 or errors != 0 or skipped or tests != baseline_tests:
                raise RuntimeError(f"Mutation was not caught by assertion failures: {label}\n{result.stdout}\n{result.stderr}")
            print(f"DETECTED: {label} ({failures} assertion failures)", flush=True)
        finally:
            path.write_text(original)
print(f"PASS: all {len(mutations)} representative faults detected", flush=True)
