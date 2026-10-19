package de.insulink.api.user;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.util.UUID;

/**
 * Runs the eraser against a real (in-memory, Postgres-flavoured) database: the
 * tables are found through the catalogue, so this is the only way to see that
 * every table with a {@code user_id} is emptied for that user and no one else.
 */
final class AccountEraserTest {
  private static final UUID ERASED = UUID.randomUUID();
  private static final UUID KEPT = UUID.randomUUID();

  private JdbcTemplate database;
  private AccountEraser eraser;

  @BeforeEach
  void createTables() throws Exception {
    var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
      + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    database = new JdbcTemplate(dataSource);
    database.execute("CREATE TABLE \"users\" (id UUID PRIMARY KEY)");
    database.execute("CREATE TABLE glucose_entries (id UUID, user_id UUID)");
    database.execute("CREATE TABLE pumps (id UUID, user_id UUID)");
    database.execute("CREATE TABLE app_openings (id UUID)");
    for (var user : new UUID[]{ERASED, KEPT}) {
      database.update("INSERT INTO \"users\" VALUES (?)", user);
      database.update("INSERT INTO glucose_entries VALUES (?, ?)", UUID.randomUUID(), user);
      database.update("INSERT INTO pumps VALUES (?, ?)", UUID.randomUUID(), user);
    }
    database.update("INSERT INTO app_openings VALUES (?)", UUID.randomUUID());
    eraser = construct(dataSource);
  }

  /** The constructor is private for Spring; the test builds it the same way. */
  private AccountEraser construct(DataSource dataSource) throws Exception {
    Constructor<AccountEraser> constructor = AccountEraser.class
      .getDeclaredConstructor(DataSource.class, PlatformTransactionManager.class);
    constructor.setAccessible(true);
    return constructor.newInstance(dataSource,
      new DataSourceTransactionManager(dataSource));
  }

  private int rows(String table, UUID userId) {
    var column = table.equals("\"users\"") ? "id" : "user_id";
    return database.queryForObject("SELECT count(*) FROM " + table + " WHERE "
      + column + " = ?", Integer.class, userId);
  }

  @Test
  void everyTableWithAUserIdIsEmptiedForThatUser() {
    eraser.erase(ERASED).join();
    Assertions.assertEquals(0, rows("\"users\"", ERASED));
    Assertions.assertEquals(0, rows("glucose_entries", ERASED));
    Assertions.assertEquals(0, rows("pumps", ERASED));
  }

  @Test
  void otherUsersAndUnrelatedTablesAreLeftAlone() {
    eraser.erase(ERASED).join();
    Assertions.assertEquals(1, rows("\"users\"", KEPT));
    Assertions.assertEquals(1, rows("glucose_entries", KEPT));
    Assertions.assertEquals(1, database.queryForObject(
      "SELECT count(*) FROM app_openings", Integer.class));
  }
}
