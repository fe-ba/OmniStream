package co.omnistream.domain.port.out.contract;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.port.dto.SessionToken;
import co.omnistream.domain.port.out.ITokenRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behaviour every {@link ITokenRepository} must share (ADR 0018). */
public abstract class TokenRepositoryContract {

  private static final Instant FUTURE = Instant.parse("2100-01-01T00:00:00Z");
  private static final Instant PAST = Instant.parse("2000-01-01T00:00:00Z");

  protected abstract ITokenRepository createRepository();

  @Test
  void savesFindsAndDeletesAToken() {
    ITokenRepository repository = createRepository();
    SessionToken token = token("abc", FUTURE);

    repository.save(token);

    assertThat(repository.find("abc")).contains(token);
    assertThat(repository.find("unknown")).isEmpty();

    repository.delete("abc");

    assertThat(repository.find("abc")).isEmpty();
  }

  @Test
  void replacesATokenWithTheSameValue() {
    ITokenRepository repository = createRepository();
    SessionToken first = token("abc", FUTURE);
    SessionToken second = token("abc", FUTURE.plusSeconds(60));
    repository.save(first);

    repository.save(second);

    assertThat(repository.find("abc")).contains(second);
  }

  @Test
  void hidesAnExpiredToken() {
    ITokenRepository repository = createRepository();
    repository.save(token("old", PAST));

    assertThat(repository.find("old")).isEmpty();
  }

  @Test
  void dropsExpiredTokensWhenAnotherOneIsSaved() {
    ITokenRepository repository = createRepository();
    repository.save(token("old", PAST));

    repository.save(token("new", FUTURE));

    assertThat(repository.find("old")).isEmpty();
    assertThat(repository.find("new")).isPresent();
  }

  private static SessionToken token(String value, Instant expiresAt) {
    return new SessionToken(value, new UserId(UUID.randomUUID()), Role.USER, expiresAt);
  }
}
