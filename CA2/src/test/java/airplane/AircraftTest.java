package airplane;

import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;
import airplane.exceptions.FlightException;
import airplane.models.FlightStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Aircraft is always real; only its depended-on components are replaced. */
@ExtendWith(MockitoExtension.class)
class AircraftTest {
    private Engine engine;
    private FuelSystem fuel;
    private LandingGear gear;
    private NavigationSystem navigation;
    private Aircraft aircraft;

    @BeforeEach
    void createFreshAircraft() {
        engine = mock(Engine.class);
        fuel = mock(FuelSystem.class);
        gear = mock(LandingGear.class);
        navigation = mock(NavigationSystem.class);
        aircraft = new Aircraft(engine, fuel, gear, navigation);
    }

    @Test
    void newAircraftIsGroundedWithoutUsingComponents() {
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verifyNoInteractions(engine, fuel, gear, navigation);
    }

    @Test
    void preparationWithAllPreconditionsSatisfiedMakesAircraftReady() {
        allowPreparation();

        aircraft.prepareForTakeoff();

        assertEquals(FlightStatus.READY, aircraft.getStatus());
        verify(engine, never()).start();
        verifyNoInteractions(gear);
    }

    @ParameterizedTest(name = "preparation rejected: {0}")
    @CsvSource({
            "engine unavailable, false, true, true",
            "insufficient fuel, true, false, true",
            "route unavailable, true, true, false"
    })
    void failedPreconditionKeepsAircraftGrounded(
            String condition, boolean operational, boolean enoughFuel, boolean routeAvailable) {
        // Only stub queries reached by this decision-table row (strict stubbing).
        when(engine.isOperational()).thenReturn(operational);
        if (operational) {
            when(fuel.hasEnoughFuel()).thenReturn(enoughFuel);
            if (enoughFuel) {
                when(navigation.isRouteAvailable()).thenReturn(routeAvailable);
            }
        }

        assertThrows(FlightException.class, aircraft::prepareForTakeoff, condition);

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(engine, never()).start();
        verifyNoInteractions(gear);
    }

    @Test
    void takeoffStartsEngineOnceAndRetractsGear() {
        prepareAircraft();
        // Explicit for the assignment: void methods on ordinary mocks already do nothing.
        doNothing().when(engine).start();

        aircraft.takeOff();

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        InOrder takeoff = inOrder(engine, gear);
        takeoff.verify(engine, times(1)).start();
        takeoff.verify(gear, times(1)).retract();
    }

    @Test
    void engineStartFailureAbortsTakeoffAndPreservesCause() {
        prepareAircraft();
        RuntimeException failure = new IllegalStateException("starter failure");
        doThrow(failure).when(engine).start();

        FlightException thrown = assertThrows(FlightException.class, aircraft::takeOff);

        assertSame(failure, thrown.getCause());
        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());
        verify(gear, never()).retract();
        verify(gear).deploy();
        verify(engine).stop();
    }

    @Test
    void gearRetractionFailureAbortsTakeoffAndPreservesCause() {
        prepareAircraft();
        RuntimeException failure = new IllegalStateException("gear jammed");
        doThrow(failure).when(gear).retract();

        FlightException thrown = assertThrows(FlightException.class, aircraft::takeOff);

        assertSame(failure, thrown.getCause());
        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());
        verify(engine).start();
        verify(gear).deploy();
        verify(engine).stop();
    }

    @ParameterizedTest(name = "cruise consumes {0} fuel")
    @ValueSource(doubles = {Double.MIN_VALUE, 1.0, 12.5})
    void cruiseConsumesExactlyRequestedPositiveAmount(double amount) {
        startFlight();

        aircraft.cruise(amount);

        verify(fuel, times(1)).consumeFuel(amount);
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(gear, never()).deploy();
        verify(engine, never()).stop();
    }

    @ParameterizedTest(name = "reject invalid cruise amount {0}")
    @ValueSource(doubles = {-1.0, -Double.MIN_VALUE, -0.0, 0.0,
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void invalidCruiseAmountDoesNotConsumeFuelOrLand(double amount) {
        startFlight();

        assertThrows(IllegalArgumentException.class, () -> aircraft.cruise(amount));

        // Typed any() returns a safe primitive default; bare any() would unbox null.
        verify(fuel, never()).consumeFuel(any(Double.class));
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(gear, never()).deploy();
        verify(engine, never()).stop();
    }

    @Test
    void engineFailureDuringCruiseLandsWithoutConsumingFuel() {
        startFlight();
        // Stub: inject engine failure as an indirect input after takeoff.
        when(engine.isOperational()).thenReturn(false);

        assertDoesNotThrow(() -> aircraft.cruise(5.0));

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(gear).deploy();
        verify(engine).stop();
        verify(fuel, never()).consumeFuel(any(Double.class));
    }

    @Test
    void fuelConsumptionFailureLandsAndPreservesCause() {
        startFlight();
        RuntimeException failure = new IllegalStateException("fuel pump failure");
        doThrow(failure).when(fuel).consumeFuel(any(Double.class));

        FlightException thrown = assertThrows(FlightException.class, () -> aircraft.cruise(5.0));

        assertSame(failure, thrown.getCause());
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(gear).deploy();
        verify(engine).stop();
    }

    @Test
    void failedEngineQueryPropagatesBeforeReadinessChanges() {
        RuntimeException failure = new IllegalStateException("sensor failure");
        when(engine.isOperational()).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, aircraft::prepareForTakeoff));

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verifyNoInteractions(fuel, gear, navigation);
    }

    @Test
    void failedFuelQueryPropagatesBeforeReadinessChanges() {
        when(engine.isOperational()).thenReturn(true);
        RuntimeException failure = new IllegalStateException("fuel sensor failure");
        when(fuel.hasEnoughFuel()).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, aircraft::prepareForTakeoff));

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(engine, never()).start();
        verifyNoInteractions(gear, navigation);
    }

    @Test
    void failedRouteQueryPropagatesBeforeReadinessChanges() {
        when(engine.isOperational()).thenReturn(true);
        when(fuel.hasEnoughFuel()).thenReturn(true);
        RuntimeException failure = new IllegalStateException("route service failure");
        when(navigation.isRouteAvailable()).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, aircraft::prepareForTakeoff));

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(engine, never()).start();
        verifyNoInteractions(gear);
    }

    @Test
    void failedEngineQueryDuringCruiseLandsAndPreservesCauseWithoutConsumingFuel() {
        startFlight();
        RuntimeException failure = new IllegalStateException("engine sensor failure");
        when(engine.isOperational()).thenThrow(failure);

        FlightException thrown = assertThrows(FlightException.class, () -> aircraft.cruise(5.0));

        assertSame(failure, thrown.getCause());
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(fuel, never()).consumeFuel(any(Double.class));
        verify(gear).deploy();
        verify(engine).stop();
    }

    @Test
    void normalLandingDeploysGearAndStopsEngine() {
        startFlight();

        aircraft.land();

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        InOrder landing = inOrder(gear, engine);
        landing.verify(gear, times(1)).deploy();
        landing.verify(engine, times(1)).stop();
    }

    @Test
    void emergencyLandingDeploysGearAndStopsEngine() {
        startFlight();

        aircraft.emergencyLand();

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(gear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    @Test
    void gearDeploymentFailureIsNotReportedAsSuccessfulLanding() {
        startFlight();
        RuntimeException failure = new IllegalStateException("gear jammed");
        doThrow(failure).when(gear).deploy();

        assertSame(failure, assertThrows(IllegalStateException.class, aircraft::land));

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(engine).stop();
    }

    @Test
    void engineStopFailureIsNotReportedAsSuccessfulLanding() {
        startFlight();
        RuntimeException failure = new IllegalStateException("shutdown failed");
        doThrow(failure).when(engine).stop();

        assertSame(failure, assertThrows(IllegalStateException.class, aircraft::land));

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(gear).deploy();
    }

    @Test
    void emergencyGearFailureStillStopsEngineWithoutReportingSuccessfulLanding() {
        startFlight();
        when(engine.isOperational()).thenReturn(false);
        RuntimeException landingFailure = new IllegalStateException("emergency gear jammed");
        doThrow(landingFailure).when(gear).deploy();

        assertSame(landingFailure, assertThrows(IllegalStateException.class, () -> aircraft.cruise(5.0)));

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(gear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    @Test
    void emergencyShutdownFailurePropagatesWithoutReportingSuccessfulLanding() {
        startFlight();
        when(engine.isOperational()).thenReturn(false);
        RuntimeException landingFailure = new IllegalStateException("emergency shutdown failed");
        doThrow(landingFailure).when(engine).stop();

        assertSame(landingFailure, assertThrows(IllegalStateException.class, () -> aircraft.cruise(5.0)));

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(gear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    @Test
    void landedAircraftCanBePreparedForAnotherFlightUsingFreshComponentReadings() {
        startFlight();
        aircraft.land();
        // Assignment-required reset: isolate the second flight's readings and calls.
        // The real aircraft stays LANDED; reset does not reset its state.
        reset(engine, fuel, gear, navigation);
        allowPreparation();

        aircraft.prepareForTakeoff();
        aircraft.takeOff();

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(engine, times(1)).start();
        verify(gear, times(1)).retract();
    }

    @Test
    void landedAircraftRechecksFuelBeforeAnotherFlight() {
        startFlight();
        aircraft.land();
        when(fuel.hasEnoughFuel()).thenReturn(false);

        assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(engine, times(1)).start();
    }

    @Test
    void landedAircraftRechecksEngineBeforeAnotherFlight() {
        startFlight();
        aircraft.land();
        when(engine.isOperational()).thenReturn(false);

        assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(engine, times(1)).start();
        verify(gear, times(1)).retract();
    }

    @Test
    void landedAircraftRechecksRouteBeforeAnotherFlight() {
        startFlight();
        aircraft.land();
        when(navigation.isRouteAvailable()).thenReturn(false);

        assertThrows(FlightException.class, aircraft::prepareForTakeoff);

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(engine, times(1)).start();
        verify(gear, times(1)).retract();
    }

    private void allowPreparation() {
        when(engine.isOperational()).thenReturn(true);
        when(fuel.hasEnoughFuel()).thenReturn(true);
        when(navigation.isRouteAvailable()).thenReturn(true);
    }

    private void prepareAircraft() {
        allowPreparation();
        aircraft.prepareForTakeoff();
    }

    private void startFlight() {
        prepareAircraft();
        aircraft.takeOff();
    }
}
