package co.omnistream.infrastructure.identity;

import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.port.dto.Credentials;
import co.omnistream.domain.port.dto.LocalCredentials;
import co.omnistream.domain.port.out.ICredentialRegistry;
import co.omnistream.domain.port.out.IIdentityProvider;
import co.omnistream.infrastructure.database.Database;
import co.omnistream.infrastructure.database.Sql;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Secondary adapter that keeps the local accounts: a username with a salted password hash (ADR
 * 0015).
 *
 * <p>It is both the {@link IIdentityProvider} that checks a password and the {@link
 * ICredentialRegistry} that stores it. The password never leaves this class: the domain only sees
 * {@code ("local", username)}.
 */
public final class LocalIdentityAdapter implements IIdentityProvider, ICredentialRegistry {

  /** Hash of the passwords. PBKDF2 is in every JDK and is slow on purpose. */
  private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

  private static final String PREFIX = "pbkdf2-sha256";
  private static final int ITERATIONS = 210_000;
  private static final int KEY_BITS = 256;
  private static final int SALT_BYTES = 16;

  private final Database database;
  private final SecureRandom random = new SecureRandom();

  public LocalIdentityAdapter(Database database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  @Override
  public boolean supports(Credentials c) {
    return c instanceof LocalCredentials local
        && LinkedIdentity.LOCAL_ISSUER.equals(local.provider());
  }

  /**
   * The identity of the account if the password matches; empty when the username is unknown or the
   * password is wrong, so nobody can tell the two cases apart.
   */
  @Override
  public Optional<LinkedIdentity> verify(Credentials c) {
    if (!(c instanceof LocalCredentials local) || !supports(local)) {
      return Optional.empty();
    }
    String username = local.username().strip();
    return withConnection(connection ->
            Sql.one(
                connection,
                "SELECT hash FROM credentials WHERE username = ?",
                row -> row.getString("hash"),
                username))
        .filter(hash -> matches(local.password(), hash))
        .map(hash -> new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, username));
  }

  @Override
  public LinkedIdentity register(String username, String password) {
    String name = required(username, "username");
    String secret = required(password, "password");
    withConnection(connection -> {
      if (Sql.count(connection, "SELECT COUNT(*) FROM credentials WHERE username = ?", 0, name)
          > 0) {
        throw new IllegalArgumentException("The username " + name + " is already registered");
      }
      Sql.update(
          connection, "INSERT INTO credentials (username, hash) VALUES (?, ?)", name, hash(secret));
      return null;
    });
    return new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, name);
  }

  @Override
  public void changePassword(LinkedIdentity i, String newPassword) {
    Objects.requireNonNull(i, "i");
    if (!i.isLocal()) {
      throw new IllegalArgumentException("Only a local identity has a password: " + i.issuer());
    }
    String secret = required(newPassword, "password");
    int changed =
        withConnection(connection ->
            Sql.update(
                connection,
                "UPDATE credentials SET hash = ? WHERE username = ?",
                hash(secret),
                i.subject()));
    if (changed == 0) {
      throw new ResourceNotFound("No local account named " + i.subject());
    }
  }

  /** A new hash with a fresh salt, in a text the next verification can parse. */
  String hash(String password) {
    byte[] salt = new byte[SALT_BYTES];
    random.nextBytes(salt);
    byte[] key = pbkdf2(password, salt, ITERATIONS);
    Base64.Encoder encoder = Base64.getEncoder();
    return PREFIX
        + "$"
        + ITERATIONS
        + "$"
        + encoder.encodeToString(salt)
        + "$"
        + encoder.encodeToString(key);
  }

  /** Whether {@code password} produces the stored hash. */
  static boolean matches(String password, String stored) {
    Objects.requireNonNull(password, "password");
    String[] parts = Objects.requireNonNull(stored, "stored").split("\\$");
    if (parts.length != 4 || !PREFIX.equals(parts[0])) {
      return false;
    }
    int iterations;
    byte[] salt;
    byte[] expected;
    try {
      iterations = Integer.parseInt(parts[1]);
      Base64.Decoder decoder = Base64.getDecoder();
      salt = decoder.decode(parts[2]);
      expected = decoder.decode(parts[3]);
    } catch (IllegalArgumentException e) {
      return false;
    }
    return MessageDigest.isEqual(expected, pbkdf2(password, salt, iterations));
  }

  private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
    try {
      return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
    } catch (java.security.spec.InvalidKeySpecException e) {
      throw new IllegalArgumentException("Not a valid password", e);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every JDK has " + ALGORITHM, e);
    } finally {
      spec.clearPassword();
    }
  }

  private static String required(String value, String name) {
    String stripped = Objects.requireNonNull(value, name).strip();
    if (stripped.isEmpty()) {
      throw new IllegalArgumentException("A password account needs a " + name);
    }
    return stripped;
  }

  private <T> T withConnection(Function<Connection, T> work) {
    Connection connection = database.connection();
    try {
      return work.apply(connection);
    } finally {
      database.release(connection);
    }
  }
}
