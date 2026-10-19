package de.insulink.api.user;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Deletes an account and everything recorded for it, in one transaction.
 *
 * The tables are found by their {@code user_id} column rather than listed
 * here, so a table added later is erased too without anyone remembering to
 * add it. The names come from the database's own catalogue, never from input.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class AccountEraser {
  private final DataSource dataSource;
  private final PlatformTransactionManager transactionManager;

  public CompletableFuture<Void> erase(UUID userId) {
    return CompletableFuture.runAsync(() -> new TransactionTemplate(transactionManager)
      .executeWithoutResult(_ -> eraseRows(new JdbcTemplate(dataSource), userId)));
  }

  private void eraseRows(JdbcTemplate database, UUID userId) {
    for (var table : userTables(database)) {
      database.update("DELETE FROM \"" + table + "\" WHERE user_id = ?", userId);
    }
    database.update("DELETE FROM \"users\" WHERE id = ?", userId);
  }

  private List<String> userTables(JdbcTemplate database) {
    return database.queryForList("SELECT table_name FROM " +
      "information_schema.columns WHERE column_name = 'user_id' " +
      "AND table_schema = current_schema()", String.class);
  }
}
