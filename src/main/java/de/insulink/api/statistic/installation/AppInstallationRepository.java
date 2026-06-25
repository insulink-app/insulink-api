package de.insulink.api.statistic.installation;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AppInstallationRepository extends DatabaseRepository<AppInstallation, UUID> {
}