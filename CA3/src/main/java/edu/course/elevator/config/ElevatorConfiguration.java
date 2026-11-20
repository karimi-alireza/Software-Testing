package edu.course.elevator.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ElevatorConfiguration {
    public Properties load() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("elevator.properties")) {
            if (input == null) throw new IOException("elevator.properties not found");
            properties.load(input);
        }
        return properties;
    }
}
