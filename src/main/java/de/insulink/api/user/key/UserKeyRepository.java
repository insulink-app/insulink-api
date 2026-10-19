package de.insulink.api.user.key;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserKeyRepository extends DatabaseRepository<UserKey, UUID> {
}
