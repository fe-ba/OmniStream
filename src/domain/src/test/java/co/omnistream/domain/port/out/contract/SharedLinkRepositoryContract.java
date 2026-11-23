package co.omnistream.domain.port.out.contract;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.sharing.SharedLink;
import co.omnistream.domain.port.out.ISharedLinkRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behaviour every {@link ISharedLinkRepository} must share (ADR 0018). */
public abstract class SharedLinkRepositoryContract {

  protected abstract ISharedLinkRepository createRepository();

  @Test
  void findsASavedLinkByItsToken() {
    ISharedLinkRepository repository = createRepository();
    SharedLink link = link();

    repository.save(link);

    assertThat(repository.findByToken(link.toSnapshot().token())).contains(link);
    assertThat(repository.findByToken("unknown")).isEmpty();
  }

  @Test
  void keepsTheRevokedStateOfASavedLink() {
    ISharedLinkRepository repository = createRepository();
    SharedLink link = link();
    repository.save(link);

    link.revoke();
    repository.save(link);

    assertThat(repository.findByToken(link.toSnapshot().token()))
        .map(saved -> saved.toSnapshot().revoked())
        .contains(true);
  }

  private static SharedLink link() {
    User owner = PortFixtures.user("owner");
    User guest = User.createGuest(new ItemId(UUID.randomUUID()), PermissionLevel.READ);
    ItemId collection = guest.toSnapshot().permissions().iterator().next().resource();
    return SharedLink.create(
        collection,
        guest.toSnapshot().id(),
        PermissionLevel.READ,
        owner.toSnapshot().id(),
        Duration.ofDays(1),
        PortFixtures.NOW);
  }
}
