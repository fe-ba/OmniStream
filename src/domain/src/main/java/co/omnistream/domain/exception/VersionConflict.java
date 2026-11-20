package co.omnistream.domain.exception;

/** Domain exception. */
public final class VersionConflict extends DomainException {
  private static final long serialVersionUID = 1L;

  public VersionConflict(String message) {
    super(message);
  }
}
