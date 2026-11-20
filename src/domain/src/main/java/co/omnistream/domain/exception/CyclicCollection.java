package co.omnistream.domain.exception;

/** Domain exception. */
public final class CyclicCollection extends DomainException {
  private static final long serialVersionUID = 1L;

  public CyclicCollection(String message) {
    super(message);
  }
}
