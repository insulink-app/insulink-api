package de.insulink.api.user.device;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserDeviceRepository extends DatabaseRepository<UserDevice, UUID> {
}