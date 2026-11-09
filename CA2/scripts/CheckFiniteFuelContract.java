import airplane.Aircraft;
import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;
import airplane.models.FlightStatus;

/**
 * Checks a proposed finite-only fuel contract against the unchanged starter.
 * This policy is not explicit in the assignment. Failures are reported openly;
 * this diagnostic is not part of the specified finite-input regression suite.
 * Run: java --class-path target/classes scripts/CheckFiniteFuelContract.java
 * Exits 1 if any check fails; exits 0 only if every check passes.
 */
public class CheckFiniteFuelContract {
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            expectRejected("initialFuel=" + value,
                    () -> new FuelSystem(value, 20.0), () -> { });
            expectRejected("minimumTakeoffFuel=" + value,
                    () -> new FuelSystem(100.0, value), () -> { });

            FuelSystem fuel = new FuelSystem(100.0, 20.0);
            expectRejected("consumeFuel(" + value + ")", () -> fuel.consumeFuel(value),
                    () -> require(fuel.getRemainingFuel() == 100.0, "fuel changed after rejection"));

            FuelSystem flightFuel = new FuelSystem(100.0, 20.0);
            Engine engine = new Engine();
            LandingGear gear = new LandingGear();
            Aircraft aircraft = new Aircraft(engine, flightFuel, gear, new NavigationSystem("Shiraz"));
            aircraft.prepareForTakeoff();
            aircraft.takeOff();
            expectRejected("cruise(" + value + ")", () -> aircraft.cruise(value), () -> {
                require(flightFuel.getRemainingFuel() == 100.0, "flight fuel changed after rejection");
                require(aircraft.getStatus() == FlightStatus.IN_FLIGHT, "status changed after rejection");
                require(engine.isRunning(), "engine stopped after rejection");
                require(!gear.isDeployed(), "gear deployed after rejection");
            });
        }
        System.out.printf("Finite-only contract: %d passed, %d failed%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void expectRejected(String label, Runnable exercise, Runnable assertUnchanged) {
        try {
            exercise.run();
        } catch (IllegalArgumentException expected) {
            try {
                assertUnchanged.run();
                passed++;
                System.out.println("PASS: " + label + " rejected without state changes");
            } catch (AssertionError failure) {
                fail(label, failure.getMessage());
            }
            return;
        } catch (RuntimeException other) {
            fail(label, "expected IllegalArgumentException; got " + other.getClass().getSimpleName());
            return;
        }
        fail(label, "input accepted; expected IllegalArgumentException");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void fail(String label, String reason) {
        failed++;
        System.out.println("FAIL: " + label + ": " + reason);
    }
}
