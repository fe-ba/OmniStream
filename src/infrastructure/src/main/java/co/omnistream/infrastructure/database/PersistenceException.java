package co.omnistream.infrastructure.database;

/** A database operation failed. It wraps the {@link java.sql.SQLException} of JDBC. */
public final class PersistenceException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public PersistenceException(String message, Throwable cause) {
    super(message, cause);
  }

  public PersistenceException(String message) {
    super(message);
  }
}
