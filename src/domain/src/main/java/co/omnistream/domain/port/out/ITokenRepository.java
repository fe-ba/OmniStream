package co.omnistream.domain.port.out;

import co.omnistream.domain.port.dto.SessionToken;
import java.util.Optional;

/** Driven port: defined by the domain, implemented by secondary adapters. */
public interface ITokenRepository {
  void save(SessionToken t);

  Optional<SessionToken> find(String value);

  void delete(String value);
}
