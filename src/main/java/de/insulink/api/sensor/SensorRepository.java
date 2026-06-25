package de.insulink.api.sensor;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SensorRepository extends DatabaseRepository<Sensor, UUID> {
}