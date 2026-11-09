package airplane;

import airplane.components.*;
import airplane.exceptions.FlightException;
import airplane.models.FlightStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Finite state-transition table, reached exclusively through the public API. */
class AircraftLifecycleTest {
    enum Operation {
        PREPARE(Aircraft::prepareForTakeoff), TAKE_OFF(Aircraft::takeOff),
        CRUISE(a -> a.cruise(1.0)), LAND(Aircraft::land), EMERGENCY_LAND(Aircraft::emergencyLand);

        final Consumer<Aircraft> invoke;
        Operation(Consumer<Aircraft> invoke) { this.invoke = invoke; }
    }

    @ParameterizedTest(name = "{0} rejects {1}")
    @CsvSource({
            "GROUNDED, TAKE_OFF", "GROUNDED, CRUISE", "GROUNDED, LAND", "GROUNDED, EMERGENCY_LAND",
            "READY, PREPARE", "READY, CRUISE", "READY, LAND", "READY, EMERGENCY_LAND",
            "IN_FLIGHT, PREPARE", "IN_FLIGHT, TAKE_OFF",
            "LANDED, TAKE_OFF", "LANDED, CRUISE", "LANDED, LAND", "LANDED, EMERGENCY_LAND",
            "ABORTED, PREPARE", "ABORTED, TAKE_OFF", "ABORTED, CRUISE", "ABORTED, LAND", "ABORTED, EMERGENCY_LAND"
    })
    void invalidOperationRejectsWithoutChangingAircraftOrComponents(FlightStatus initial, Operation operation) {
        Engine engine = new Engine();
        FuelSystem fuel = new FuelSystem(100.0, 20.0);
        LandingGear gear = new LandingGear();
        Aircraft aircraft = new Aircraft(engine, fuel, gear, new NavigationSystem("Shiraz"));
        reachState(aircraft, engine, initial);
        boolean wasRunning = engine.isRunning();
        boolean wasDeployed = gear.isDeployed();
        double remainingFuel = fuel.getRemainingFuel();

        FlightException thrown = assertThrows(FlightException.class, () -> operation.invoke.accept(aircraft));

        assertTrue(thrown.getMessage().contains(initial.name()));
        assertEquals(initial, aircraft.getStatus());
        assertEquals(wasRunning, engine.isRunning());
        assertEquals(wasDeployed, gear.isDeployed());
        assertEquals(remainingFuel, fuel.getRemainingFuel());
    }

    enum MissingComponent { ENGINE, FUEL, GEAR, NAVIGATION }

    @ParameterizedTest(name = "reject null {0}")
    @EnumSource(MissingComponent.class)
    void constructorRejectsEachMissingDependency(MissingComponent missing) {
        Engine engine = missing == MissingComponent.ENGINE ? null : new Engine();
        FuelSystem fuel = missing == MissingComponent.FUEL ? null : new FuelSystem(100.0, 20.0);
        LandingGear gear = missing == MissingComponent.GEAR ? null : new LandingGear();
        NavigationSystem navigation = missing == MissingComponent.NAVIGATION ? null : new NavigationSystem("Shiraz");

        assertThrows(NullPointerException.class, () -> new Aircraft(engine, fuel, gear, navigation));
    }

    private void reachState(Aircraft aircraft, Engine engine, FlightStatus target) {
        // Conditional setup, never conditional assertions or reflection into the SUT.
        switch (target) {
            case GROUNDED -> { }
            case READY -> aircraft.prepareForTakeoff();
            case IN_FLIGHT -> { aircraft.prepareForTakeoff(); aircraft.takeOff(); }
            case LANDED -> { aircraft.prepareForTakeoff(); aircraft.takeOff(); aircraft.land(); }
            case ABORTED -> {
                aircraft.prepareForTakeoff();
                engine.fail();
                assertThrows(FlightException.class, aircraft::takeOff);
            }
        }
        assertEquals(target, aircraft.getStatus(), "fixture must reach requested initial state");
    }
}
