package co.omnistream.domain.model.access;

import java.util.Objects;

/**
 * How an identity provider recognizes a user: who issued the identity and the user's identifier
 * there. A user can have several, for example a local account and, in version 2.0, an OIDC one.
 *
 * <p>The password never enters the domain: the local identity adapter stores it, and the domain
 * only keeps {@code ("local", username)}.
 *
 * @param issuer who recognizes the user: {@value #LOCAL_ISSUER}, {@value #SHARED_LINK_ISSUER} or
 *     the URL of an OIDC provider
 * @param subject the user's identifier for that issuer
 */
public record LinkedIdentity(String issuer, String subject) {

  /** Issuer of the accounts with a username and password kept by OmniStream itself. */
  public static final String LOCAL_ISSUER = "local";

  /** Issuer of the guests: they are recognized by the shared link that created them. */
  public static final String SHARED_LINK_ISSUER = "shared-link";

  public LinkedIdentity {
    issuer = notBlank(issuer, "issuer");
    subject = notBlank(subject, "subject");
  }

  /** Whether OmniStream itself checks this identity's password. */
  public boolean isLocal() {
    return LOCAL_ISSUER.equals(issuer);
  }

  private static String notBlank(String value, String name) {
    String stripped = Objects.requireNonNull(value, name).strip();
    if (stripped.isEmpty()) {
      throw new IllegalArgumentException("An identity needs a " + name);
    }
    return stripped;
  }
}
