package co.omnistream.domain.model.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FingerprintTest {

  private static final String DIGEST =
      "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

  @Test
  void normalizesTheAlgorithmAndTheDigest() {
    Fingerprint fingerprint = new Fingerprint(" sha-256 ", DIGEST.toUpperCase() + " ");

    assertThat(fingerprint.algorithm()).isEqualTo("SHA-256");
    assertThat(fingerprint.value()).isEqualTo(DIGEST);
  }

  @Test
  void matchesAFingerprintOfTheSameContent() {
    Fingerprint stored = new Fingerprint("SHA-256", DIGEST);
    Fingerprint incoming = new Fingerprint("sha-256", DIGEST.toUpperCase());

    assertThat(stored.matches(incoming)).isTrue();
    assertThat(stored).isEqualTo(incoming);
  }

  @Test
  void doesNotMatchAnotherDigest() {
    Fingerprint stored = new Fingerprint("SHA-256", DIGEST);
    Fingerprint other = new Fingerprint("SHA-256", "ab" + DIGEST.substring(2));

    assertThat(stored.matches(other)).isFalse();
  }

  @Test
  void doesNotMatchTheSameDigestFromAnotherAlgorithm() {
    Fingerprint sha256 = new Fingerprint("SHA-256", DIGEST);
    Fingerprint other = new Fingerprint("SHA3-256", DIGEST);

    assertThat(sha256.matches(other)).isFalse();
  }

  @Test
  void rejectsADigestThatIsNotHexadecimal() {
    assertThatThrownBy(() -> new Fingerprint("SHA-256", "not-a-digest"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("hexadecimal");
  }

  @Test
  void rejectsAnEmptyDigest() {
    assertThatThrownBy(() -> new Fingerprint("SHA-256", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsABlankAlgorithm() {
    assertThatThrownBy(() -> new Fingerprint(" ", DIGEST))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("algorithm");
  }
}
