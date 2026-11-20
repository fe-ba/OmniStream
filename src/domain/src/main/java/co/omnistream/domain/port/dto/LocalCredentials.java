package co.omnistream.domain.port.dto;

/** Data transfer object of the ports. */
public final class LocalCredentials extends Credentials {
  private final String username;
  private final String password;

  public LocalCredentials(String provider, String username, String password) {
    super(provider);
    this.username = username;
    this.password = password;
  }

  public String username() {
    return username;
  }

  public String password() {
    return password;
  }
}
