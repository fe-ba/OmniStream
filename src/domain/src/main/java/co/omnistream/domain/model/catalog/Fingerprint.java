package co.omnistream.domain.model.catalog;

/** Value object. */
public record Fingerprint(String algorithm, String value) {
  public boolean matches(Fingerprint other) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
