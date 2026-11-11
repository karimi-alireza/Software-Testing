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
        assertFalse(engine.isOperational(), "a rejected start must not repair the engine");
        assertThrows(IllegalStateException.class, engine::start);
        assertFalse(engine.isRunning());
        assertFalse(engine.isOperational());
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
        assertFalse(engine.isOperational(), "failure must persist after a rejected restart");
        assertFalse(engine.isRunning());
        assertThrows(IllegalStateException.class, engine::start);
        assertFalse(engine.isOperational());
        assertFalse(engine.isRunning());
    }
}
