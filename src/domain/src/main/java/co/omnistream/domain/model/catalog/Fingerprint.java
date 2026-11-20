package co.omnistream.domain.model.catalog;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Digest of a file's content, used to detect duplicates.
 *
 * <p>Files are fingerprinted with SHA-256, computed by the file storage adapter (coding
 * conventions, section 4). The algorithm name is kept in upper case and the digest in lower-case
 * hexadecimal, so two fingerprints of the same content are always equal, also as map keys.
 *
 * @param algorithm name of the digest algorithm, for example {@code SHA-256}
 * @param value the digest in hexadecimal
 */
public record Fingerprint(String algorithm, String value) {

  private static final Pattern HEXADECIMAL = Pattern.compile("[0-9a-f]+");

  public Fingerprint {
    Objects.requireNonNull(algorithm, "algorithm");
    Objects.requireNonNull(value, "value");
    algorithm = algorithm.strip().toUpperCase(Locale.ROOT);
    value = value.strip().toLowerCase(Locale.ROOT);
    if (algorithm.isEmpty()) {
      throw new IllegalArgumentException("A fingerprint needs an algorithm");
    }
    if (!HEXADECIMAL.matcher(value).matches()) {
      throw new IllegalArgumentException("A fingerprint must be hexadecimal: '" + value + "'");
    }
  }

  /** Whether both fingerprints use the same algorithm and come from the same content. */
  public boolean matches(Fingerprint other) {
    Objects.requireNonNull(other, "other");
    return algorithm.equals(other.algorithm) && value.equals(other.value);
  }
}
