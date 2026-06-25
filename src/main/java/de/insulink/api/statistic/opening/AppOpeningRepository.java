package de.insulink.api.statistic.opening;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AppOpeningRepository extends DatabaseRepository<AppOpening, UUID> {
}