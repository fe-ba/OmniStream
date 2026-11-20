package co.omnistream.domain.exception;

/** Domain exception. */
public final class ExpiredLink extends DomainException {
  private static final long serialVersionUID = 1L;

  public ExpiredLink(String message) {
    super(message);
  }
}
