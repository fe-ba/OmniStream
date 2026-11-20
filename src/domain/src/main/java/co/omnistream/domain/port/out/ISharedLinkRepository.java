package co.omnistream.domain.port.out;

import co.omnistream.domain.model.sharing.SharedLink;
import java.util.Optional;

/** Driven port: defined by the domain, implemented by secondary adapters. */
public interface ISharedLinkRepository {
  void save(SharedLink e);

  Optional<SharedLink> findByToken(String token);
}
