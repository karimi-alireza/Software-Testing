import airplane.Aircraft;
import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;

/** Exploratory reproduction of an unresolved starter-code validation gap.
 * Run after building: java --class-path target/classes scripts/NonFiniteFuelProbe.java
 * A nonzero exit signals failure to reproduce the documented behavior.
 */
public class NonFiniteFuelProbe {
    public static void main(String[] args) {
        FuelSystem fuel = new FuelSystem(20.0, 10.0);
        fuel.consumeFuel(Double.NaN);
        expectNaN("consumeFuel(NaN)", fuel.getRemainingFuel());

        FuelSystem infinite = new FuelSystem(Double.POSITIVE_INFINITY, 10.0);
        infinite.consumeFuel(Double.POSITIVE_INFINITY);
        expectNaN("infinity - infinity", infinite.getRemainingFuel());

        FuelSystem invalidInitial = new FuelSystem(Double.NaN, 10.0);
        expectNaN("NaN initial fuel accepted", invalidInitial.getRemainingFuel());

        FuelSystem flightFuel = new FuelSystem(20.0, 10.0);
        Aircraft aircraft = new Aircraft(new Engine(), flightFuel,
                new LandingGear(), new NavigationSystem("Shiraz"));
        aircraft.prepareForTakeoff();
        aircraft.takeOff();
        aircraft.cruise(Double.NaN);
        expectNaN("cruise(NaN), status=" + aircraft.getStatus(), flightFuel.getRemainingFuel());
        System.out.println("Known validation gap reproduced; these outcomes are defects, not acceptance criteria.");
    }

    private static void expectNaN(String label, double actual) {
        if (!Double.isNaN(actual)) {
            throw new AssertionError("Documented gap no longer reproduces: " + label);
        }
        System.out.println(label + ": remaining=" + actual);
    }
}
