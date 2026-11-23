package co.omnistream.infrastructure.persistence;

import co.omnistream.infrastructure.database.Database;
import co.omnistream.infrastructure.database.PersistenceException;
import co.omnistream.infrastructure.database.Sql;
import java.nio.file.Path;

/**
 * JDBC adapter of the local profile: it persists in a SQLite file (ADR 0015) and backs the catalog
 * up with {@code VACUUM INTO}, which writes a consistent copy without stopping the application.
 */
public final class SqlitePersistenceAdapter extends JdbcPersistenceAdapter {

  public SqlitePersistenceAdapter(Database database) {
    super(database);
  }

  /** A SQLite database in {@code file}; it is created, with its tables, if it does not exist. */
  public static SqlitePersistenceAdapter at(Path file) {
    return new SqlitePersistenceAdapter(Database.sqlite(file));
  }

  @Override
  protected void writeDump(Path destination) {
    String quoted = destination.toAbsolutePath().toString().replace("'", "''");
    var connection = database().connection();
    try {
      Sql.execute(connection, "VACUUM INTO '" + quoted + "'");
    } catch (PersistenceException e) {
      throw new PersistenceException("Cannot back up the SQLite database", e);
    } finally {
      database().release(connection);
    }
  }
}
