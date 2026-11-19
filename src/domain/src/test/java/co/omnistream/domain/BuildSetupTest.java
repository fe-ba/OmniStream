package co.omnistream.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Checks that the build runs the tests with JUnit Jupiter and AssertJ on Java 25 (ADR 0012). Remove
 * it once the domain has real tests.
 */
class BuildSetupTest {

  @Test
  void runsOnJava25OrNewer() {
    assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(25);
  }
}
