package co.omnistream.domain.port.dto;

/** Data transfer object of the ports. */
public abstract class Credentials {
  private final String provider;

  protected Credentials(String provider) {
    this.provider = provider;
  }

  public String provider() {
    return provider;
  }
}
