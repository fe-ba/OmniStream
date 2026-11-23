package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.snapshot.UserSnapshot;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** In-memory user repository for the domain tests (ADR 0018). */
public final class InMemoryUserRepository implements IUserRepository {

  private final Map<UserId, UserSnapshot> users = new LinkedHashMap<>();

  @Override
  public void save(User user) {
    Objects.requireNonNull(user, "user");
    users.put(user.toSnapshot().id(), user.toSnapshot());
  }

  @Override
  public Optional<User> findById(UserId id) {
    Objects.requireNonNull(id, "id");
    return Optional.ofNullable(users.get(id)).map(User::fromSnapshot);
  }

  @Override
  public Optional<User> findByUsername(String username) {
    Objects.requireNonNull(username, "username");
    return users.values().stream()
        .filter(user -> user.username().equals(username))
        .findFirst()
        .map(User::fromSnapshot);
  }

  @Override
  public Optional<User> findByIdentity(LinkedIdentity identity) {
    Objects.requireNonNull(identity, "identity");
    return users.values().stream()
        .filter(user -> user.identities().contains(identity))
        .findFirst()
        .map(User::fromSnapshot);
  }

  @Override
  public List<User> findAll() {
    return users.values().stream()
        .sorted(Comparator.comparing(UserSnapshot::username))
        .map(User::fromSnapshot)
        .toList();
  }
}
