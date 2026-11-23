package co.omnistream.domain.port.out.memory;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.port.dto.LocalCredentials;
import org.junit.jupiter.api.Test;

class InMemoryIdentityProviderTest {

  private static final LinkedIdentity ANA = new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, "ana");

  @Test
  void recognizesAConfiguredUser() {
    InMemoryIdentityProvider provider = new InMemoryIdentityProvider().add("ana", ANA);

    assertThat(provider.supports(new LocalCredentials("local", "ana", "x"))).isTrue();
    assertThat(provider.verify(new LocalCredentials("local", "ana", "x"))).contains(ANA);
  }

  @Test
  void refusesAnUnknownUserOrAnotherProvider() {
    InMemoryIdentityProvider provider = new InMemoryIdentityProvider().add("ana", ANA);

    assertThat(provider.verify(new LocalCredentials("local", "luis", "x"))).isEmpty();
    assertThat(provider.supports(new LocalCredentials("oidc", "ana", "x"))).isFalse();
    assertThat(provider.verify(new LocalCredentials("oidc", "ana", "x"))).isEmpty();
  }
}
