package co.omnistream.domain.port.out;

import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import java.util.Optional;

/** Driven port: defined by the domain, implemented by secondary adapters. */
public interface IUserRepository {
  void save(User u);

  Optional<User> findById(UserId id);

  Optional<User> findByUsername(String username);

  /**
   * The user with that linked identity. Guests have one too, {@code ("shared-link", <their id>)},
   * so this works the same for every user.
   */
  Optional<User> findByIdentity(LinkedIdentity i);
}
