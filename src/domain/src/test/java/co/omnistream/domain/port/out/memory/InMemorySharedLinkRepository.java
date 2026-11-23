package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.model.sharing.SharedLink;
import co.omnistream.domain.port.out.ISharedLinkRepository;
import co.omnistream.domain.snapshot.SharedLinkSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** In-memory shared link repository for the domain tests (ADR 0018). */
public final class InMemorySharedLinkRepository implements ISharedLinkRepository {

  private final Map<String, SharedLinkSnapshot> byToken = new LinkedHashMap<>();

  @Override
  public void save(SharedLink link) {
    Objects.requireNonNull(link, "link");
    byToken.put(link.toSnapshot().token(), link.toSnapshot());
  }

  @Override
  public Optional<SharedLink> findByToken(String token) {
    Objects.requireNonNull(token, "token");
    return Optional.ofNullable(byToken.get(token)).map(SharedLink::fromSnapshot);
  }
}
