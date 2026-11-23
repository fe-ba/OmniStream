package co.omnistream.domain.port.out.contract;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.out.IUserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behaviour every {@link IUserRepository} must share (ADR 0018). */
public abstract class UserRepositoryContract {

  protected abstract IUserRepository createRepository();

  @Test
  void savesAndFindsAUserByEveryKey() {
    IUserRepository repository = createRepository();
    User user = PortFixtures.user("ana");

    repository.save(user);

    assertThat(repository.findById(user.toSnapshot().id())).contains(user);
    assertThat(repository.findByUsername("ana")).contains(user);
    assertThat(repository.findByIdentity(new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, "ana")))
        .contains(user);
    assertThat(repository.findByUsername("nobody")).isEmpty();
    assertThat(repository.findById(new co.omnistream.domain.model.access.UserId(UUID.randomUUID())))
        .isEmpty();
  }

  @Test
  void updatesTheSavedUser() {
    IUserRepository repository = createRepository();
    User user = PortFixtures.user("ana");
    repository.save(user);
    ItemId resource = new ItemId(UUID.randomUUID());

    user.changeRole(Role.ADMIN);
    user.grant(new Permission(resource, PermissionLevel.WRITE));
    user.link(new LinkedIdentity("oidc", "ana@example.com"));
    repository.save(user);

    User saved = repository.findById(user.toSnapshot().id()).orElseThrow();
    assertThat(saved.isAdmin()).isTrue();
    assertThat(saved.toSnapshot().permissions())
        .containsExactly(new Permission(resource, PermissionLevel.WRITE));
    assertThat(repository.findByIdentity(new LinkedIdentity("oidc", "ana@example.com")))
        .contains(saved);
    assertThat(repository.findAll()).containsExactly(saved);
  }

  @Test
  void keepsGuestsAndFindsThemByTheirSharedLinkIdentity() {
    IUserRepository repository = createRepository();
    User guest = User.createGuest(new ItemId(UUID.randomUUID()), PermissionLevel.READ);
    String subject = guest.toSnapshot().id().value().toString();

    repository.save(guest);

    assertThat(
            repository.findByIdentity(
                new LinkedIdentity(LinkedIdentity.SHARED_LINK_ISSUER, subject)))
        .contains(guest);
    assertThat(repository.findByUsername("guest-" + subject)).contains(guest);
  }

  @Test
  void listsEveryUserOnce() {
    IUserRepository repository = createRepository();
    User ana = PortFixtures.user("ana");
    User luis = PortFixtures.user("luis");
    repository.save(ana);
    repository.save(luis);
    repository.save(ana);

    assertThat(repository.findAll()).containsExactlyInAnyOrder(ana, luis);
  }
}
