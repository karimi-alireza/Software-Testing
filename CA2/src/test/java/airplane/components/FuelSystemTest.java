package airplane.components;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class FuelSystemTest {
    @ParameterizedTest(name = "initial fuel {0}, minimum {1}: enough={2}")
    @CsvSource({"0, 0, true", "0, 20, false", "19.99, 20, false", "20, 20, true", "20.01, 20, true"})
    void takeoffThresholdIncludesEquality(double initial, double minimum, boolean expected) {
        FuelSystem fuel = new FuelSystem(initial, minimum);

        assertEquals(expected, fuel.hasEnoughFuel());
        assertEquals(initial, fuel.getRemainingFuel());
    }

    @ParameterizedTest(name = "reject fuel values {0}, {1}")
    @CsvSource({"-0.01, 20", "100, -0.01", "-0.01, -0.01",
            "-4.9E-324, 20", "100, -4.9E-324", "-0.0001, 20", "100, -0.0001"})
    void constructorRejectsNegativeFuelValues(double initial, double minimum) {
        assertThrows(IllegalArgumentException.class, () -> new FuelSystem(initial, minimum));
    }

    @ParameterizedTest(name = "reject non-finite initial fuel {0}")
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void constructorRejectsNonFiniteInitialFuel(double initial) {
        assertThrows(IllegalArgumentException.class, () -> new FuelSystem(initial, 20.0));
    }

    @ParameterizedTest(name = "reject non-finite minimum fuel {0}")
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void constructorRejectsNonFiniteMinimumFuel(double minimum) {
        assertThrows(IllegalArgumentException.class, () -> new FuelSystem(100.0, minimum));
    }

    @ParameterizedTest(name = "consume {0}, remaining {1}")
    @CsvSource({"0.01, 19.99", "19.99, 0.01", "20, 0"})
    void validConsumptionIncludesExactRemainingFuel(double amount, double expected) {
        FuelSystem fuel = new FuelSystem(20.0, 10.0);

        fuel.consumeFuel(amount);

        assertEquals(expected, fuel.getRemainingFuel(), 1.0e-10);
    }

    @ParameterizedTest(name = "reject invalid fuel amount {0}")
    @ValueSource(doubles = {-1.0, -Double.MIN_VALUE, -0.0, 0.0,
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void invalidConsumptionPreservesFuel(double amount) {
        FuelSystem fuel = new FuelSystem(20.0, 10.0);

        assertThrows(IllegalArgumentException.class, () -> fuel.consumeFuel(amount));

        assertEquals(20.0, fuel.getRemainingFuel());
    }

    @Test
    void consumingMoreThanRemainingFuelFailsWithoutDeductingFuel() {
        FuelSystem fuel = new FuelSystem(20.0, 10.0);

        assertThrows(IllegalStateException.class, () -> fuel.consumeFuel(Math.nextUp(20.0)));

        assertEquals(20.0, fuel.getRemainingFuel());
    }

    @Test
    void consumptionCanMoveFuelBelowTakeoffThreshold() {
        FuelSystem fuel = new FuelSystem(20.0, 20.0);

        fuel.consumeFuel(0.01);

        assertFalse(fuel.hasEnoughFuel());
    }

    @ParameterizedTest(name = "exactly consume finite extreme {0}")
    @ValueSource(doubles = {Double.MIN_VALUE, Double.MAX_VALUE})
    void finitePositiveExtremesCanBeConsumedExactly(double amount) {
        FuelSystem fuel = new FuelSystem(amount, 0.0);

        fuel.consumeFuel(amount);

        assertEquals(0.0, fuel.getRemainingFuel());
    }

    @Test
    void smallestRepresentableShortfallFailsTakeoffThreshold() {
        FuelSystem fuel = new FuelSystem(Math.nextDown(20.0), 20.0);

        assertFalse(fuel.hasEnoughFuel());
    }

    @Test
    void smallestRepresentableSurplusPassesTakeoffThreshold() {
        FuelSystem fuel = new FuelSystem(Math.nextUp(20.0), 20.0);

        assertTrue(fuel.hasEnoughFuel());
    }
}
