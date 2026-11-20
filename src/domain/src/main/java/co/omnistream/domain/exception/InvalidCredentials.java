package co.omnistream.domain.exception;

/** Domain exception. */
public final class InvalidCredentials extends DomainException {
  private static final long serialVersionUID = 1L;

  public InvalidCredentials(String message) {
    super(message);
  }
}
