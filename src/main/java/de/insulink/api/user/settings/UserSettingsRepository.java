package de.insulink.api.user.settings;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserSettingsRepository extends DatabaseRepository<UserSettings, UUID> {
}