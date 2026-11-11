import airplane.Aircraft;
import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;
import airplane.exceptions.FlightException;

import java.util.Locale;

/** Console demonstration using the actual simulator and real components. */
public class FlightDemo {
    private final Engine engine = new Engine();
    private final FuelSystem fuel = new FuelSystem(100.0, 20.0);
    private final LandingGear gear = new LandingGear();
    private final Aircraft aircraft = new Aircraft(
            engine, fuel, gear, new NavigationSystem("Shiraz"));

    public static void main(String[] args) {
        System.out.println("NORMAL FLIGHT");
        FlightDemo normal = new FlightDemo();
        normal.show("Created");
        normal.aircraft.prepareForTakeoff();
        normal.show("Prepared");
        normal.aircraft.takeOff();
        normal.show("Takeoff");
        normal.aircraft.cruise(25.0);
        normal.show("Cruise: use 25");
        normal.aircraft.land();
        normal.show("Landing");

        System.out.println("\nENGINE FAILURE DURING FLIGHT");
        FlightDemo engineFailure = new FlightDemo();
        engineFailure.startFlight();
        engineFailure.show("Takeoff");
        engineFailure.engine.fail();
        engineFailure.show("Engine failed");
        engineFailure.aircraft.cruise(5.0);
        engineFailure.show("Emergency landing");

        System.out.println("\nINSUFFICIENT FUEL DURING FLIGHT");
        FlightDemo fuelFailure = new FlightDemo();
        fuelFailure.startFlight();
        fuelFailure.show("Takeoff");
        try {
            fuelFailure.aircraft.cruise(101.0);
        } catch (FlightException expected) {
            System.out.println("Reported: " + expected.getMessage());
            System.out.println("Cause: " + expected.getCause().getMessage());
        }
        fuelFailure.show("Emergency landing");
    }

    private void startFlight() {
        aircraft.prepareForTakeoff();
        aircraft.takeOff();
    }

    private void show(String step) {
        System.out.printf(Locale.ROOT,
                "%-18s status=%-9s engineRunning=%-5s gearDeployed=%-5s fuel=%.1f%n",
                step, aircraft.getStatus(), engine.isRunning(), gear.isDeployed(),
                fuel.getRemainingFuel());
    }
}
