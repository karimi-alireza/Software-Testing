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
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Regression tests for cleanup commands, partial effects and compound failures. */
@ExtendWith(MockitoExtension.class)
class AircraftRecoveryTest {
    private Engine engine;
    private FuelSystem fuel;
    private LandingGear gear;
    private Aircraft aircraft;

    @BeforeEach
    void createFreshAircraft() {
        engine = mock(Engine.class);
        fuel = mock(FuelSystem.class);
        gear = mock(LandingGear.class);
        NavigationSystem navigation = new NavigationSystem("Shiraz");
        aircraft = new Aircraft(engine, fuel, gear, navigation);
        when(engine.isOperational()).thenReturn(true);
        when(fuel.hasEnoughFuel()).thenReturn(true);
        aircraft.prepareForTakeoff();
    }

    enum FailureSource { FUEL, ENGINE_QUERY }

    @ParameterizedTest(name = "{0}: gear recovery failure keeps original cause")
    @EnumSource(FailureSource.class)
    void recoveryGearFailurePreservesOriginalFailureAndStillStopsEngine(FailureSource source) {
        aircraft.takeOff();
        RuntimeException original = failNextCruise(source);
        RuntimeException recovery = new IllegalStateException("gear deployment failed");
        doThrow(recovery).when(gear).deploy();

        FlightException thrown = assertThrows(FlightException.class, () -> aircraft.cruise(5.0));

        assertSame(original, thrown.getCause());
        assertArrayEquals(new Throwable[]{recovery}, original.getSuppressed());
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(gear).deploy();
        verify(engine).stop();
    }

    @ParameterizedTest(name = "{0}: shutdown recovery failure keeps original cause")
    @EnumSource(FailureSource.class)
    void recoveryShutdownFailurePreservesOriginalFailure(FailureSource source) {
        aircraft.takeOff();
        RuntimeException original = failNextCruise(source);
        RuntimeException recovery = new IllegalStateException("shutdown failed");
        doThrow(recovery).when(engine).stop();

        FlightException thrown = assertThrows(FlightException.class, () -> aircraft.cruise(5.0));

        assertSame(original, thrown.getCause());
        assertArrayEquals(new Throwable[]{recovery}, original.getSuppressed());
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(gear).deploy();
        verify(engine).stop();
    }

    @ParameterizedTest(name = "{0}: both recovery failures are retained")
    @EnumSource(FailureSource.class)
    void bothRecoveryFailuresAreRetainedBelowOriginalCause(FailureSource source) {
        aircraft.takeOff();
        RuntimeException original = failNextCruise(source);
        RuntimeException deployment = new IllegalStateException("gear deployment failed");
        RuntimeException shutdown = new IllegalStateException("shutdown failed");
        doThrow(deployment).when(gear).deploy();
        doThrow(shutdown).when(engine).stop();

        FlightException thrown = assertThrows(FlightException.class, () -> aircraft.cruise(5.0));

        assertSame(original, thrown.getCause());
        assertArrayEquals(new Throwable[]{deployment}, original.getSuppressed());
        assertArrayEquals(new Throwable[]{shutdown}, deployment.getSuppressed());
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(engine).stop();
    }

    @Test
    void normalLandingRetainsBothFailuresAndAttemptsShutdown() {
        aircraft.takeOff();
        RuntimeException deployment = new IllegalStateException("gear deployment failed");
        RuntimeException shutdown = new IllegalStateException("shutdown failed");
        doThrow(deployment).when(gear).deploy();
        doThrow(shutdown).when(engine).stop();

        RuntimeException thrown = assertThrows(IllegalStateException.class, aircraft::land);

        assertSame(deployment, thrown);
        assertArrayEquals(new Throwable[]{shutdown}, thrown.getSuppressed());
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(engine).stop();
    }

    @Test
    void abortedTakeoffRetainsStartupAndBothCleanupFailures() {
        RuntimeException startup = new IllegalStateException("startup failed");
        RuntimeException deployment = new IllegalStateException("gear deployment failed");
        RuntimeException shutdown = new IllegalStateException("shutdown failed");
        doThrow(startup).when(engine).start();
        doThrow(deployment).when(gear).deploy();
        doThrow(shutdown).when(engine).stop();

        FlightException thrown = assertThrows(FlightException.class, aircraft::takeOff);

        assertSame(startup, thrown.getCause());
        assertArrayEquals(new Throwable[]{deployment}, startup.getSuppressed());
        assertArrayEquals(new Throwable[]{shutdown}, deployment.getSuppressed());
        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());
        verify(gear, never()).retract();
        verify(engine).stop();
    }

    @Test
    void recoveryReusingOriginalExceptionDoesNotSelfSuppress() {
        aircraft.takeOff();
        RuntimeException shared = failNextCruise(FailureSource.FUEL);
        doThrow(shared).when(gear).deploy();

        FlightException thrown = assertThrows(FlightException.class, () -> aircraft.cruise(5.0));

        assertSame(shared, thrown.getCause());
        assertEquals(0, shared.getSuppressed().length);
        verify(engine).stop();
    }

    @Test
    void landingCommandsReusingSameExceptionDoNotSelfSuppress() {
        aircraft.takeOff();
        RuntimeException shared = new IllegalStateException("shared hardware failure");
        doThrow(shared).when(gear).deploy();
        doThrow(shared).when(engine).stop();

        assertSame(shared, assertThrows(IllegalStateException.class, aircraft::land));

        assertEquals(0, shared.getSuppressed().length);
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(engine).stop();
    }

    private RuntimeException failNextCruise(FailureSource source) {
        RuntimeException failure = new IllegalStateException(source + " failed");
        switch (source) {
            case FUEL -> doThrow(failure).when(fuel).consumeFuel(5.0);
            case ENGINE_QUERY -> when(engine.isOperational()).thenThrow(failure);
        }
        return failure;
    }
}
