package airplane;

import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;
import airplane.exceptions.FlightException;
import airplane.models.FlightStatus;

import java.util.Objects;


public class Aircraft {
    private final Engine engine;
    private final FuelSystem fuelSystem;
    private final LandingGear landingGear;
    private final NavigationSystem navigationSystem;

    private FlightStatus status = FlightStatus.GROUNDED;

    public Aircraft(
            Engine engine,
            FuelSystem fuelSystem,
            LandingGear landingGear,
            NavigationSystem navigationSystem) {

        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.fuelSystem = Objects.requireNonNull(fuelSystem, "fuelSystem must not be null");
        this.landingGear = Objects.requireNonNull(landingGear, "landingGear must not be null");
        this.navigationSystem = Objects.requireNonNull(navigationSystem,
                "navigationSystem must not be null");
    }

    public void prepareForTakeoff() {
        ensureStatusIs(FlightStatus.GROUNDED, FlightStatus.LANDED);

        if (!engine.isOperational()) {
            throw new FlightException("Cannot prepare for takeoff: engine is not operational.");
        }
        if (!fuelSystem.hasEnoughFuel()) {
            throw new FlightException("Cannot prepare for takeoff: insufficient fuel.");
        }
        if (!navigationSystem.isRouteAvailable()) {
            throw new FlightException("Cannot prepare for takeoff: route is unavailable.");
        }

        status = FlightStatus.READY;
    }

    public void takeOff() {
        ensureStatusIs(FlightStatus.READY);

        try {
            engine.start();
            landingGear.retract();
            status = FlightStatus.IN_FLIGHT;
        } catch (RuntimeException exception) {
            status = FlightStatus.ABORTED;
            throw new FlightException("Takeoff failed.", exception);
        }
    }

    public void cruise(double fuelAmount) {
        ensureStatusIs(FlightStatus.IN_FLIGHT);

        if (fuelAmount <= 0) {
            throw new IllegalArgumentException("Fuel amount must be greater than zero.");
        }

        if (!engine.isOperational()) {
            emergencyLand();
            return;
        }

        try {
            fuelSystem.consumeFuel(fuelAmount);
        } catch (RuntimeException exception) {
            emergencyLand();
            throw new FlightException("A problem occurred while consuming fuel.", exception);
        }
    }

    public void land() {
        ensureStatusIs(FlightStatus.IN_FLIGHT);
        completeLanding();
    }

    public void emergencyLand() {
        ensureStatusIs(FlightStatus.IN_FLIGHT);
        completeLanding();
    }

    private void completeLanding() {
        landingGear.deploy();
        engine.stop();
        status = FlightStatus.LANDED;
    }

    private void ensureStatusIs(FlightStatus... allowedStatuses) {
        for (FlightStatus allowedStatus : allowedStatuses) {
            if (status == allowedStatus) {
                return;
            }
        }

        throw new FlightException(
                "Operation is not allowed while aircraft status is " + status + ".");
    }

    public FlightStatus getStatus() {
        return status;
    }
}
