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
            recoverAfterFailure(exception, this::deployGearAndStopEngine);
            throw new FlightException("Takeoff failed.", exception);
        }
    }

    public void cruise(double fuelAmount) {
        ensureStatusIs(FlightStatus.IN_FLIGHT);

        if (!Double.isFinite(fuelAmount) || fuelAmount <= 0) {
            throw new IllegalArgumentException("Fuel amount must be finite and greater than zero.");
        }

        boolean operational;
        try {
            operational = engine.isOperational();
        } catch (RuntimeException exception) {
            recoverAfterFailure(exception, this::emergencyLand);
            throw new FlightException("Cannot read engine status.", exception);
        }
        if (!operational) {
            emergencyLand();
            return;
        }

        try {
            fuelSystem.consumeFuel(fuelAmount);
        } catch (RuntimeException exception) {
            recoverAfterFailure(exception, this::emergencyLand);
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
        deployGearAndStopEngine();
        status = FlightStatus.LANDED;
    }

    /** Both recovery commands are attempted; a failed command cannot report a completed landing. */
    private void deployGearAndStopEngine() {
        RuntimeException failure = null;
        try {
            landingGear.deploy();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            engine.stop();
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                addSuppressedIfDifferent(failure, exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void recoverAfterFailure(RuntimeException failure, Runnable recovery) {
        try {
            recovery.run();
        } catch (RuntimeException recoveryFailure) {
            addSuppressedIfDifferent(failure, recoveryFailure);
        }
    }

    private void addSuppressedIfDifferent(RuntimeException failure, RuntimeException additionalFailure) {
        // A collaborator may throw the same instance twice; Java forbids self-suppression.
        if (failure != additionalFailure) {
            failure.addSuppressed(additionalFailure);
        }
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
