package airplane.components;

public class Engine {
    private boolean operational;
    private boolean running;

    public Engine() {
        this(true);
    }

    public Engine(boolean operational) {
        this.operational = operational;
        this.running = false;
    }

    public boolean isOperational() {
        return operational;
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (!operational) {
            throw new IllegalStateException("The engine is not operational.");
        }
        running = true;
    }

    public void stop() {
        running = false;
    }

    /**
     * Simulates an engine failure during operation.
     */
    public void fail() {
        operational = false;
        running = false;
    }
}
