package co.omnistream.domain.model.access;

/** Value object. */
public record LinkedIdentity(String issuer, String subject) {
  public boolean isLocal() {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
