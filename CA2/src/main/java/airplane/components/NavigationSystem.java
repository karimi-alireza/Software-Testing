package airplane.components;

public class NavigationSystem {
    private boolean routeAvailable;
    private final String destination;

    public NavigationSystem(String destination) {
        this(destination, true);
    }

    public NavigationSystem(String destination, boolean routeAvailable) {
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Destination must not be blank.");
        }
        this.destination = destination;
        this.routeAvailable = routeAvailable;
    }

    public boolean isRouteAvailable() {
        return routeAvailable;
    }

    public String getDestination() {
        return destination;
    }

    public void setRouteAvailable(boolean routeAvailable) {
        this.routeAvailable = routeAvailable;
    }
}
