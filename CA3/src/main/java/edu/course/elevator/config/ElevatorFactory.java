package edu.course.elevator.config;

import edu.course.elevator.events.ElevatorEventPublisher;
import edu.course.elevator.hardware.*;
import edu.course.elevator.model.Elevator;
import edu.course.elevator.service.ElevatorService;

import java.io.IOException;
import java.util.Properties;


public class ElevatorFactory {
    public ElevatorService createService() throws IOException {
        Properties config = new ElevatorConfiguration().load();
        int initialFloor = Integer.parseInt(config.getProperty("initial.floor", "1"));
        Elevator elevator = new Elevator(initialFloor, 0, 10);
        Motor motor;
        if ("legacy".equalsIgnoreCase(config.getProperty("motor.type", "legacy")))
            motor = new LegacyMotorAdapter(new LegacyLiftMotor());
        else
            motor = new RecordingMotor();
        SimpleDoor door = new SimpleDoor();
        InMemoryFloorSensor sensor = new InMemoryFloorSensor(initialFloor);
        return new ElevatorService(elevator, motor, door, sensor, new ElevatorEventPublisher());
    }
}
