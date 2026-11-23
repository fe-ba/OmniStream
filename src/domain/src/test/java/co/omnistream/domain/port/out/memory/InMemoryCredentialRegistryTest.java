package co.omnistream.domain.port.out.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.LinkedIdentity;
import org.junit.jupiter.api.Test;

class InMemoryCredentialRegistryTest {

  private final InMemoryCredentialRegistry registry = new InMemoryCredentialRegistry();

  @Test
  void registersALocalIdentityAndKeepsThePassword() {
    LinkedIdentity identity = registry.register("  ana  ", "secreto");

    assertThat(identity).isEqualTo(new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, "ana"));
    assertThat(registry.passwords()).containsEntry("ana", "secreto");
  }

  @Test
  void refusesToRegisterTheSameUsernameTwice() {
    registry.register("ana", "secreto");

    assertThatThrownBy(() -> registry.register("ana", "otra"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void changesThePasswordOfAKnownAccount() {
    LinkedIdentity identity = registry.register("ana", "secreto");

    registry.changePassword(identity, "nueva");

    assertThat(registry.passwords()).containsEntry("ana", "nueva");
  }

  @Test
  void refusesToChangeThePasswordOfAnUnknownAccount() {
    LinkedIdentity identity = new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, "nobody");

    assertThatThrownBy(() -> registry.changePassword(identity, "nueva"))
        .isInstanceOf(ResourceNotFound.class);
  }

  @Test
  void refusesToChangeThePasswordOfANonLocalIdentity() {
    LinkedIdentity identity = new LinkedIdentity("oidc", "ana");

    assertThatThrownBy(() -> registry.changePassword(identity, "nueva"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
