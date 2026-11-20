package co.omnistream.domain.exception;

/** Domain exception. */
public final class ResourceNotFound extends DomainException {
  private static final long serialVersionUID = 1L;

  public ResourceNotFound(String message) {
    super(message);
  }
}
