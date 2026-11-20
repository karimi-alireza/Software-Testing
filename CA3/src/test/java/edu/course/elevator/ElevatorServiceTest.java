package edu.course.elevator;

import edu.course.elevator.events.ElevatorEventPublisher;
import edu.course.elevator.hardware.InMemoryFloorSensor;
import edu.course.elevator.hardware.RecordingMotor;
import edu.course.elevator.hardware.SimpleDoor;
import edu.course.elevator.model.Elevator;
import edu.course.elevator.model.ElevatorState;
import edu.course.elevator.service.ElevatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ElevatorServiceTest {
    private Elevator elevator;
    private RecordingMotor motor;
    private SimpleDoor door;
    private InMemoryFloorSensor sensor;
    private ElevatorEventPublisher publisher;
    private List<String> receivedEvents;
    private ElevatorService service;

    @BeforeEach
    void setUp() {
        elevator = new Elevator(1, 0, 10);
        motor = new RecordingMotor();
        door = new SimpleDoor();
        sensor = new InMemoryFloorSensor(1);
        publisher = new ElevatorEventPublisher();
        receivedEvents = new ArrayList<>();
        publisher.subscribe(receivedEvents::add);
        service = new ElevatorService(elevator, motor, door, sensor, publisher);
    }

    @Test
    void testMoveOpenCloseAndNotifyEverything() {
        service.moveTo(4);
        assertEquals(4, elevator.getCurrentFloor());
        assertEquals(4, motor.getLastDestination());
        assertEquals(ElevatorState.IDLE, elevator.getState());
        assertTrue(receivedEvents.contains("ARRIVED:4"));

        service.openDoor();
        assertTrue(door.isOpen());
        assertEquals(ElevatorState.DOOR_OPEN, elevator.getState());
        assertTrue(receivedEvents.contains("DOOR_OPENED"));

        service.closeDoor();
        assertFalse(door.isOpen());
        assertEquals(ElevatorState.IDLE, elevator.getState());
        assertTrue(receivedEvents.contains("DOOR_CLOSED"));
    }

    @Test
    void testMoveToFloorFromFixtureFile() throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream("floors.csv");
        assertNotNull(stream);
        String firstDataLine;
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(stream))) {
            firstDataLine = reader.lines().filter(line -> !line.startsWith("#")).findFirst().orElseThrow();
        }
        int floor = Integer.parseInt(firstDataLine.split(",")[0].trim());
        service.moveTo(floor);
        assertEquals(floor, elevator.getCurrentFloor());
    }

    @Test
    void testMoveToHardCodedFloor() {
        service.moveTo(7);
        assertEquals(7, elevator.getCurrentFloor());
    }

    @Test
    void testSensorReadingWithConditionalVerification() {
        int observedFloor = service.getCurrentFloor();
        if (observedFloor == 1) {
            assertEquals(1, observedFloor);
        } else {
            assertEquals(0, observedFloor);
        }
    }

    @Test
    void testSeveralDestinationsInOneMethod() {
        assertEquals(2, moveAndRead(2));
        assertEquals(6, moveAndRead(6));
        assertEquals(9, moveAndRead(9));
    }

    private int moveAndRead(int floor) {
        service.moveTo(floor);
        return elevator.getCurrentFloor();
    }

    @Test
    void testRejectsOutOfRangeFloor() {
        assertThrows(IllegalArgumentException.class, () -> service.moveTo(11));
    }

    @Test
    void testDoorMustBeClosedBeforeMoving() {
        service.openDoor();
        assertThrows(IllegalStateException.class, () -> service.moveTo(3));
    }
}
