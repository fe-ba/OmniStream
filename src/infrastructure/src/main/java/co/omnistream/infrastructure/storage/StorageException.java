package co.omnistream.infrastructure.storage;

/** A file operation failed, or the path is outside the library. */
public final class StorageException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }

  public StorageException(String message) {
    super(message);
  }
}
