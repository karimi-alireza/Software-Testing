package airplane.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EngineTest {
    @Test
    void defaultEngineIsOperationalAndStopped() {
        Engine engine = new Engine();
        assertTrue(engine.isOperational());
        assertFalse(engine.isRunning());
    }

    @Test
    void startingOperationalEngineMakesItRun() {
        Engine engine = new Engine();
        engine.start();
        assertTrue(engine.isRunning());
    }

    @Test
    void nonoperationalEngineCannotStart() {
        Engine engine = new Engine(false);
        assertThrows(IllegalStateException.class, engine::start);
        assertFalse(engine.isRunning());
    }

    @Test
    void stoppingRunningEngineKeepsItOperational() {
        Engine engine = new Engine();
        engine.start();
        engine.stop();
        assertFalse(engine.isRunning());
        assertTrue(engine.isOperational());
    }

    @Test
    void failureStopsRunningEngineAndPreventsRestart() {
        Engine engine = new Engine();
        engine.start();
        engine.fail();
        assertFalse(engine.isRunning());
        assertFalse(engine.isOperational());
        assertThrows(IllegalStateException.class, engine::start);
    }
}
