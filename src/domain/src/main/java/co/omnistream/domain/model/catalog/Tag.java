package co.omnistream.domain.model.catalog;

/** Value object. */
public record Tag(String name, TagSource source) {
  public String normalized() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public boolean isAutomatic() {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
