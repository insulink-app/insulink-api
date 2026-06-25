package de.insulink.api.pump;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PumpRepository extends DatabaseRepository<Pump, UUID> {
}