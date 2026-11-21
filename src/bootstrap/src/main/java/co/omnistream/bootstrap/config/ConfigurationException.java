package co.omnistream.bootstrap.config;

/** The configuration cannot be read or has an invalid value; the message names the key. */
public final class ConfigurationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ConfigurationException(String message) {
    super(message);
  }

  public ConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
