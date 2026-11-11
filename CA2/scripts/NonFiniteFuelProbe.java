import airplane.Aircraft;
import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;

/** Regression probe for the four numeric-corruption examples found in the review.
 * Run after building: java --class-path target/classes scripts/NonFiniteFuelProbe.java
 * Exits unsuccessfully if a formerly corrupting input is accepted or changes state.
 */
public class NonFiniteFuelProbe {
    public static void main(String[] args) {
        FuelSystem fuel = new FuelSystem(20.0, 10.0);
        expectRejected("consumeFuel(NaN)", () -> fuel.consumeFuel(Double.NaN));
        require(fuel.getRemainingFuel() == 20.0, "invalid consumption changed fuel");

        expectRejected("infinite initial fuel", () -> new FuelSystem(Double.POSITIVE_INFINITY, 10.0));

        expectRejected("NaN initial fuel", () -> new FuelSystem(Double.NaN, 10.0));

        FuelSystem flightFuel = new FuelSystem(20.0, 10.0);
        Aircraft aircraft = new Aircraft(new Engine(), flightFuel,
                new LandingGear(), new NavigationSystem("Shiraz"));
        aircraft.prepareForTakeoff();
        aircraft.takeOff();
        expectRejected("cruise(NaN)", () -> aircraft.cruise(Double.NaN));
        require(flightFuel.getRemainingFuel() == 20.0, "invalid cruise changed fuel");
        require(aircraft.getStatus() == airplane.models.FlightStatus.IN_FLIGHT,
                "invalid cruise changed flight status");
        System.out.println("PASS: all 4 former corruption scenarios rejected without fuel/status changes.");
    }

    private static void expectRejected(String label, Runnable exercise) {
        try {
            exercise.run();
        } catch (IllegalArgumentException expected) {
            System.out.println("PASS: " + label + " rejected");
            return;
        }
        throw new AssertionError(label + " was accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
