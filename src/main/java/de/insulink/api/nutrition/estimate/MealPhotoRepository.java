package de.insulink.api.nutrition.estimate;

import de.insulink.api.database.DatabaseRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MealPhotoRepository extends DatabaseRepository<MealPhoto, UUID> {
}
