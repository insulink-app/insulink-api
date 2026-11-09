package de.insulink.api.database;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A repository whose entity carries a {@code userId}. {@link #deleteAllOfUser}
 * is one bulk statement without Hibernate's per-row count check, so two
 * overlapping replace-all syncs of the same user no longer fail with a
 * {@code StaleStateException} when the other request already removed a row.
 */
@NoRepositoryBean
public interface UserOwnedRepository<T, ID extends Serializable>
  extends DatabaseRepository<T, ID> {
  @Async
  @Modifying
  @Transactional
  @Query("delete from #{#entityName} entity where entity.userId = :userId")
  CompletableFuture<Integer> deleteAllOfUser(@Param("userId") UUID userId);
}
