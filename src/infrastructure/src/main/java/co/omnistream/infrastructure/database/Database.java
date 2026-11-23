package co.omnistream.infrastructure.database;

import java.net.URI;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The JDBC connection of the persistence adapter and its unit of work.
 *
 * <p>Every query opens a connection and closes it, unless a transaction is running on the same
 * thread: then the query joins it. That is what {@link co.omnistream.domain.port.out.IUnitOfWork}
 * needs, and what lets the audit decorators save the change and its entry together (ADR 0025).
 *
 * <p>There is no pool: version 1.0 is a desktop application with one user at a time, and the server
 * profile gets its connections from PostgreSQL, which is fast enough to connect per operation.
 */
public final class Database {

  private static final System.Logger LOG = System.getLogger(Database.class.getName());

  private final String url;
  private final Optional<String> user;
  private final Optional<String> password;
  private final ThreadLocal<Transaction> current = new ThreadLocal<>();

  /** A database reached with its JDBC URL, for example {@code jdbc:sqlite:/tmp/omnistream.db}. */
  public Database(String url) {
    this(url, Optional.empty(), Optional.empty());
  }

  /** A database that needs a user and a password, as PostgreSQL usually does. */
  public Database(String url, Optional<String> user, Optional<String> password) {
    this.url = Objects.requireNonNull(url, "url");
    this.user = Objects.requireNonNull(user, "user");
    this.password = Objects.requireNonNull(password, "password");
    if (user.isPresent() != password.isPresent()) {
      throw new IllegalArgumentException("A database user goes with its password");
    }
  }

  /** The SQLite file of the local profile: it is created if it does not exist. */
  public static Database sqlite(Path file) {
    Objects.requireNonNull(file, "file");
    Path absolute = file.toAbsolutePath();
    if (absolute.getParent() != null) {
      createParent(absolute.getParent());
    }
    return new Database("jdbc:sqlite:" + absolute);
  }

  /** A PostgreSQL database: {@code jdbc:postgresql://host:5432/omnistream}. */
  public static Database postgres(URI jdbcUrl, Optional<String> user, Optional<String> password) {
    Objects.requireNonNull(jdbcUrl, "jdbcUrl");
    if (!jdbcUrl.toString().startsWith("jdbc:postgresql:")) {
      throw new IllegalArgumentException("Not a PostgreSQL URL: " + jdbcUrl);
    }
    return new Database(jdbcUrl.toString(), user, password);
  }

  /** The JDBC URL, so the backup can tell which database it is talking to. */
  public String url() {
    return url;
  }

  /** A connection that is part of the current transaction or a new one. */
  public Connection connection() {
    Transaction running = current.get();
    if (running != null) {
      return running.connection();
    }
    try {
      return open();
    } catch (SQLException e) {
      throw new PersistenceException("Cannot connect to " + url, e);
    }
  }

  /**
   * Gives back a connection taken with {@link #connection()}: it closes it unless it belongs to a
   * transaction, which will close it when it ends.
   */
  public void release(Connection connection) {
    Transaction running = current.get();
    if (running != null && running.connection() == connection) {
      return;
    }
    close(connection);
  }

  /** Runs {@code work} in one transaction: all of it is kept, or nothing. */
  public <T> T inTransaction(Supplier<T> work) {
    Objects.requireNonNull(work, "work");
    Transaction running = current.get();
    if (running != null) {
      running.join();
      try {
        return work.get();
      } finally {
        running.leave();
      }
    }
    Connection connection;
    try {
      connection = open();
      connection.setAutoCommit(false);
    } catch (SQLException e) {
      throw new PersistenceException("Cannot start a transaction on " + url, e);
    }
    Transaction transaction = new Transaction(connection);
    current.set(transaction);
    try {
      T result = work.get();
      try {
        connection.commit();
      } catch (SQLException e) {
        throw new PersistenceException("Cannot commit on " + url, e);
      }
      return result;
    } catch (RuntimeException | Error e) {
      rollback(connection, e);
      throw e;
    } finally {
      current.remove();
      try {
        connection.setAutoCommit(true);
      } catch (SQLException e) {
        LOG.log(System.Logger.Level.WARNING, "Could not restore autocommit", e);
      }
      close(connection);
    }
  }

  /** Closes the database. Every connection is opened per operation, so there is nothing to keep. */
  public void close() {
    current.remove();
  }

  private Connection open() throws SQLException {
    if (user.isPresent()) {
      return DriverManager.getConnection(url, user.get(), password.get());
    }
    return DriverManager.getConnection(url);
  }

  private static void rollback(Connection connection, Throwable cause) {
    try {
      connection.rollback();
    } catch (SQLException e) {
      cause.addSuppressed(e);
    }
  }

  private static void close(Connection connection) {
    try {
      connection.close();
    } catch (SQLException e) {
      LOG.log(System.Logger.Level.WARNING, "Could not close a database connection", e);
    }
  }

  private static void createParent(Path folder) {
    try {
      java.nio.file.Files.createDirectories(folder);
    } catch (java.io.IOException e) {
      throw new PersistenceException("Cannot create the database folder " + folder, e);
    }
  }

  /** One transaction: its connection and how many nested calls joined it. */
  private static final class Transaction {
    private final Connection connection;
    private int depth = 1;

    private Transaction(Connection connection) {
      this.connection = connection;
    }

    private Connection connection() {
      return connection;
    }

    private void join() {
      depth++;
    }

    private void leave() {
      depth--;
    }
  }
}
