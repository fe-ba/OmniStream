package co.omnistream.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.event.CollectionCreated;
import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.exception.VersionConflict;
import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.model.catalog.CatalogId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.playback.PlaybackSession;
import co.omnistream.domain.model.sharing.SharedLink;
import co.omnistream.domain.port.dto.AuditQuery;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.infrastructure.database.Database;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlitePersistenceAdapterTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  @TempDir Path temporary;

  private SqlitePersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new SqlitePersistenceAdapter(Database.sqlite(temporary.resolve("catalog.db")));
  }

  @AfterEach
  void tearDown() {
    adapter.database().close();
  }

  @Test
  void createsTheCatalogAndWritesItsEventsToTheOutbox() {
    CatalogIndex catalog = emptyCatalog();
    catalog.createCollection("Series", NOW);

    adapter.save(catalog);

    CatalogIndex loaded = adapter.getCatalog();
    assertThat(loaded.toSnapshot().name()).isEqualTo("Mi biblioteca");
    assertThat(loaded.toSnapshot().version()).isEqualTo(1);
    assertThat(loaded.toSnapshot().collections()).hasSize(1);
    List<DomainEvent> pending = adapter.fetchPending(10);
    assertThat(pending).hasSize(1);
    assertThat(pending.getFirst()).isInstanceOf(CollectionCreated.class);
  }

  @Test
  void refusesToSaveACatalogChangedSinceItWasLoaded() {
    CatalogIndex first = emptyCatalog();
    adapter.save(first);

    CatalogIndex loaded = adapter.getCatalog();
    CatalogIndex other = adapter.getCatalog();
    other.createCollection("Peliculas", NOW);
    adapter.save(other);

    assertThatThrownBy(() -> adapter.save(loaded)).isInstanceOf(VersionConflict.class);
  }

  @Test
  void storesUsersWithTheirPermissionsAndIdentities() {
    User user =
        User.create("ana", Role.USER, new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, "ana"));
    user.grant(
        new co.omnistream.domain.model.access.Permission(
            new ItemId(UUID.randomUUID()), PermissionLevel.READ));

    adapter.save(user);

    assertThat(adapter.findById(user.toSnapshot().id())).contains(user);
    assertThat(adapter.findByUsername("ana")).contains(user);
    assertThat(adapter.findByIdentity(new LinkedIdentity("local", "ana"))).contains(user);
    assertThat(adapter.findAll()).containsExactly(user);
  }

  @Test
  void storesSessionsAndCountsTheActiveOnes() {
    ItemId file = new ItemId(UUID.randomUUID());
    User guest =
        User.create("guest-user", Role.USER, new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, "g"));
    PlaybackSession session =
        PlaybackSession.start(file, guest.toSnapshot().id(), Duration.ofMinutes(2), NOW);

    adapter.save(session);

    assertThat(adapter.findById(session.toSnapshot().id())).contains(session);
    assertThat(adapter.findLastSession(guest.toSnapshot().id(), file)).contains(session);
    assertThat(adapter.countActive()).isEqualTo(1);
  }

  @Test
  void storesSharedLinks() {
    User owner =
        User.create("owner", Role.USER, new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, "owner"));
    User guest = User.createGuest(new ItemId(UUID.randomUUID()), PermissionLevel.READ);
    SharedLink link =
        SharedLink.create(
            guest.toSnapshot().permissions().iterator().next().resource(),
            guest.toSnapshot().id(),
            PermissionLevel.READ,
            owner.toSnapshot().id(),
            Duration.ofDays(1),
            NOW);

    adapter.save(link);

    assertThat(adapter.findByToken(link.toSnapshot().token())).contains(link);
  }

  @Test
  void appendsAndQueriesTheAuditTrail() {
    User actor =
        User.create("luis", Role.ADMIN, new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, "luis"));
    AuditEntry entry =
        AuditEntry.of(actor.toSnapshot().id(), AuditAction.LOGGED_IN, NOW).saying("local");

    adapter.append(entry);

    assertThat(adapter.find(AuditQuery.latest(10))).containsExactly(entry);
    assertThat(adapter.find(AuditQuery.latest(10).about(new ItemId(UUID.randomUUID())))).isEmpty();
  }

  @Test
  void keepsTheLibraryPathInTheSettings() {
    Path library = temporary.resolve("library");

    assertThat(adapter.getLibraryPath()).isNotEqualTo(library);
    adapter.saveLibraryPath(library);

    assertThat(adapter.getLibraryPath()).isEqualTo(library);
  }

  @Test
  void registersChangeLogEntriesOnlyOnce() {
    CatalogIndex catalog = emptyCatalog();
    catalog.createCollection("Series", NOW);
    CollectionCreated event = (CollectionCreated) catalog.pullEvents().getFirst();
    adapter.register(event);
    adapter.register(event);

    assertThat(adapter.hasChangesSince(NOW.minusSeconds(1))).isTrue();
    assertThat(adapter.changesSince(NOW.minusSeconds(1)).added()).containsExactly(event.item());
  }

  private static CatalogIndex emptyCatalog() {
    return CatalogIndex.fromSnapshot(
        new CatalogSnapshot(
            new CatalogId(UUID.randomUUID()), "Mi biblioteca", 0, List.of(), List.of()));
  }
}
