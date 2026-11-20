package co.omnistream.domain.exception;

/** Domain exception. */
public final class AccessDenied extends DomainException {
  private static final long serialVersionUID = 1L;

  public AccessDenied(String message) {
    super(message);
  }
}
