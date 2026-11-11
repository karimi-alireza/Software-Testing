package airplane.components;

public class FuelSystem {
    private double remainingFuel;
    private final double minimumTakeoffFuel;

    public FuelSystem(double initialFuel, double minimumTakeoffFuel) {
        if (!Double.isFinite(initialFuel) || !Double.isFinite(minimumTakeoffFuel)
                || initialFuel < 0 || minimumTakeoffFuel < 0) {
            throw new IllegalArgumentException("Fuel values must be finite and nonnegative.");
        }
        this.remainingFuel = initialFuel;
        this.minimumTakeoffFuel = minimumTakeoffFuel;
    }

    public boolean hasEnoughFuel() {
        return remainingFuel >= minimumTakeoffFuel;
    }

    public void consumeFuel(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Fuel amount must be finite and greater than zero.");
        }
        if (amount > remainingFuel) {
            throw new IllegalStateException("Not enough fuel.");
        }
        remainingFuel -= amount;
    }

    public double getRemainingFuel() {
        return remainingFuel;
    }
}
