package co.omnistream.domain.exception;

/** Domain exception. */
public final class FileRejected extends DomainException {
  private static final long serialVersionUID = 1L;

  public FileRejected(String message) {
    super(message);
  }
}
