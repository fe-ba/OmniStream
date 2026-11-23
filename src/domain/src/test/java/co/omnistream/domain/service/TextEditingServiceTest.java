package co.omnistream.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.event.CatalogEvent;
import co.omnistream.domain.event.FileModified;
import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.FileRejected;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.exception.VersionConflict;
import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.CatalogId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.IndexingStatus;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.policy.TextFilePolicy;
import co.omnistream.domain.port.dto.TextDocument;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.IFileStorage;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class TextEditingServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-22T18:00:00Z");

  /** Files kept as bytes; the fingerprint is the SHA-256 of the content, as a real adapter does. */
  private static final class Storage implements IFileStorage {
    final Map<Path, byte[]> files = new HashMap<>();
    int writes;

    String text(Path p) {
      return new String(files.get(p), StandardCharsets.UTF_8);
    }

    @Override
    public InputStream read(Path path) {
      return new ByteArrayInputStream(files.get(path));
    }

    @Override
    public void write(Path path, InputStream data) {
      try {
        files.put(path, data.readAllBytes());
        writes++;
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public Fingerprint computeFingerprint(Path path) {
      return sha256(files.get(path));
    }

    @Override
    public void delete(Path path) {
      throw new AssertionError("not used");
    }

    @Override
    public boolean exists(Path path) {
      throw new AssertionError("not used");
    }

    @Override
    public List<Path> list(Path root) {
      throw new AssertionError("not used");
    }

    @Override
    public Path copy(Path source, Path destination) {
      throw new AssertionError("not used");
    }

    @Override
    public long freeSpace() {
      throw new AssertionError("not used");
    }
  }

  /** The catalog as a snapshot with optimistic locking, keeping the events of every save. */
  private static final class Catalog implements ICatalogRepository {
    CatalogSnapshot stored;
    final List<CatalogEvent> events = new ArrayList<>();
    boolean failNextSave;

    Catalog(CatalogSnapshot stored) {
      this.stored = stored;
    }

    MediaFileSnapshot file(ItemId id) {
      return stored.files().stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow();
    }

    @Override
    public CatalogIndex getCatalog() {
      return CatalogIndex.fromSnapshot(stored);
    }

    @Override
    public void save(CatalogIndex c) {
      CatalogSnapshot s = c.toSnapshot();
      if (failNextSave || s.version() != stored.version()) {
        failNextSave = false;
        throw new VersionConflict("The catalog changed");
      }
      events.addAll(c.pullEvents());
      stored = new CatalogSnapshot(s.id(), s.name(), s.version() + 1, s.files(), s.collections());
    }

    @Override
    public Optional<MediaFileSnapshot> findFile(ItemId id) {
      throw new AssertionError("not used");
    }

    @Override
    public Optional<MediaFileSnapshot> findByFingerprint(Fingerprint h) {
      throw new AssertionError("not used");
    }

    @Override
    public List<MediaFileSnapshot> findByText(String txt) {
      throw new AssertionError("not used");
    }

    @Override
    public List<MediaFileSnapshot> findByCriteria(co.omnistream.domain.port.dto.SearchCriteria c) {
      throw new AssertionError("not used");
    }
  }

  private static final class Users implements IUserRepository {
    private final Map<UserId, User> users = new HashMap<>();

    @Override
    public void save(User u) {
      users.put(u.toSnapshot().id(), User.fromSnapshot(u.toSnapshot()));
    }

    @Override
    public Optional<User> findById(UserId id) {
      return Optional.ofNullable(users.get(id)).map(u -> User.fromSnapshot(u.toSnapshot()));
    }

    @Override
    public Optional<User> findByUsername(String username) {
      throw new AssertionError("not used");
    }

    @Override
    public Optional<User> findByIdentity(LinkedIdentity i) {
      throw new AssertionError("not used");
    }

    @Override
    public List<User> findAll() {
      throw new AssertionError("not used");
    }
  }

  private static final class UnitOfWork implements IUnitOfWork {
    int runs;

    @Override
    public <T> T execute(Supplier<T> work) {
      runs++;
      return work.get();
    }
  }

  static Fingerprint sha256(byte[] content) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
      return new Fingerprint("SHA-256", HexFormat.of().formatHex(digest));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final String SETLIST = "# Setlist\n\n1. Intro\n2. Canción\n";

  private final Storage storage = new Storage();
  private final Users users = new Users();
  private final UnitOfWork unitOfWork = new UnitOfWork();
  private final ItemId band = new ItemId(UUID.randomUUID());
  private final ItemId setlist = new ItemId(UUID.randomUUID());
  private final ItemId song = new ItemId(UUID.randomUUID());
  private final ItemId latin = new ItemId(UUID.randomUUID());
  private final Catalog catalog;
  private final TextEditingService service;

  TextEditingServiceTest() {
    MediaFileSnapshot setlistFile =
        stored(setlist, "setlist.md", SETLIST.getBytes(StandardCharsets.UTF_8));
    MediaFileSnapshot songFile = stored(song, "song.mp3", new byte[] {1, 2, 3});
    MediaFileSnapshot latinFile =
        stored(latin, "letras.txt", "Canción".getBytes(StandardCharsets.ISO_8859_1));
    catalog =
        new Catalog(
            new CatalogSnapshot(
                new CatalogId(UUID.randomUUID()),
                "library",
                1,
                List.of(setlistFile, songFile, latinFile),
                List.of(new CollectionSnapshot(band, "Band", "", List.of(setlist, song, latin)))));
    service =
        new TextEditingService(
            users,
            new AccessPolicy(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            catalog,
            storage,
            unitOfWork,
            new TextFilePolicy());
  }

  private MediaFileSnapshot stored(ItemId id, String name, byte[] content) {
    Path path = Path.of("/library/" + name);
    storage.files.put(path, content);
    return new MediaFileSnapshot(
        id,
        name,
        path,
        sha256(content),
        MediaType.DOCUMENT,
        content.length,
        Duration.ZERO,
        IndexingStatus.INDEXED,
        NOW.minusSeconds(3600),
        List.of(),
        Set.of());
  }

  private UserId user(String name, PermissionLevel onBand) {
    User user = User.create(name, Role.USER, new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, name));
    if (onBand != null) {
      user.grant(new Permission(band, onBand));
    }
    users.save(user);
    return user.toSnapshot().id();
  }

  private UserId writer() {
    return user("ana", PermissionLevel.WRITE);
  }

  private UserId reader() {
    return user("luis", PermissionLevel.READ);
  }

  private Path setlistPath() {
    return catalog.file(setlist).path();
  }

  // ------------------------------------------------------------------ open

  @Test
  void opensTheTextWithItsFingerprint() {
    TextDocument doc = service.open(writer(), setlist);

    assertThat(doc.file()).isEqualTo(setlist);
    assertThat(doc.name()).isEqualTo("setlist.md");
    assertThat(doc.text()).isEqualTo(SETLIST);
    assertThat(doc.fingerprint()).isEqualTo(catalog.file(setlist).fingerprint());
    assertThat(doc.editable()).isTrue();
  }

  @Test
  void aReaderOpensItReadOnly() {
    assertThat(service.open(reader(), setlist).editable()).isFalse();
  }

  @Test
  void someoneWhoCannotSeeTheFileCannotOpenIt() {
    UserId pedro = user("pedro", null);

    assertThatThrownBy(() -> service.open(pedro, setlist)).isInstanceOf(AccessDenied.class);
  }

  @Test
  void onlyTextFilesOpen() {
    assertThatThrownBy(() -> service.open(writer(), song))
        .isInstanceOf(FileRejected.class)
        .hasMessageContaining("song.mp3");
  }

  @Test
  void aTextFileThatIsNotUtf8DoesNotOpen() {
    assertThatThrownBy(() -> service.open(writer(), latin))
        .isInstanceOf(FileRejected.class)
        .hasMessageContaining("UTF-8");
  }

  @Test
  void anUnknownFileIsNotFound() {
    User admin = User.create("admin", Role.ADMIN, new LinkedIdentity("local", "admin"));
    users.save(admin);

    assertThatThrownBy(() -> service.open(admin.toSnapshot().id(), new ItemId(UUID.randomUUID())))
        .isInstanceOf(ResourceNotFound.class);
  }

  @Test
  void aDeactivatedUserCannotOpen() {
    User marta = User.create("marta", Role.USER, new LinkedIdentity("local", "marta"));
    marta.grant(new Permission(band, PermissionLevel.WRITE));
    marta.deactivate();
    users.save(marta);

    assertThatThrownBy(() -> service.open(marta.toSnapshot().id(), setlist))
        .isInstanceOf(ResourceNotFound.class);
  }

  // ------------------------------------------------------------------ save

  @Test
  void savingWritesTheFileAndRecordsItsNewContent() {
    UserId ana = writer();
    TextDocument opened = service.open(ana, setlist);
    String edited = SETLIST + "3. Encore\n";

    TextDocument saved = service.save(ana, setlist, edited, opened.fingerprint());

    byte[] bytes = edited.getBytes(StandardCharsets.UTF_8);
    assertThat(storage.text(setlistPath())).isEqualTo(edited);
    assertThat(saved.fingerprint()).isEqualTo(sha256(bytes));
    assertThat(saved.text()).isEqualTo(edited);
    assertThat(saved.editable()).isTrue();
    assertThat(catalog.file(setlist).fingerprint()).isEqualTo(sha256(bytes));
    assertThat(catalog.file(setlist).sizeBytes()).isEqualTo(bytes.length);
    assertThat(catalog.events).hasSize(1);
    assertThat(catalog.events.get(0)).isInstanceOf(FileModified.class);
    assertThat(catalog.events.get(0).item()).isEqualTo(setlist);
    assertThat(catalog.events.get(0).occurredAt()).isEqualTo(NOW);
    assertThat(unitOfWork.runs).isEqualTo(1);
  }

  @Test
  void eachSaveIsBasedOnThePreviousOne() {
    UserId ana = writer();
    TextDocument first =
        service.save(ana, setlist, SETLIST + "a", service.open(ana, setlist).fingerprint());

    TextDocument second = service.save(ana, setlist, SETLIST + "ab", first.fingerprint());

    assertThat(storage.text(setlistPath())).isEqualTo(SETLIST + "ab");
    assertThat(catalog.file(setlist).fingerprint()).isEqualTo(second.fingerprint());
  }

  @Test
  void aReaderCannotSave() {
    UserId luis = reader();
    Fingerprint basedOn = service.open(luis, setlist).fingerprint();

    assertThatThrownBy(() -> service.save(luis, setlist, "mine", basedOn))
        .isInstanceOf(AccessDenied.class);
    assertThat(storage.text(setlistPath())).isEqualTo(SETLIST);
    assertThat(catalog.events).isEmpty();
  }

  @Test
  void savingTheStoredTextAgainChangesNothingWhateverItIsBasedOn() {
    UserId ana = writer();
    Fingerprint old = new Fingerprint("SHA-256", "0".repeat(64));

    TextDocument doc = service.save(ana, setlist, SETLIST, old);

    assertThat(doc.fingerprint()).isEqualTo(catalog.file(setlist).fingerprint());
    assertThat(storage.writes).isZero();
    assertThat(catalog.events).isEmpty();
  }

  @Test
  void anotherTextBasedOnAnOldFingerprintIsAConflict() {
    UserId ana = writer();
    Fingerprint old = service.open(ana, setlist).fingerprint();
    service.save(ana, setlist, SETLIST + "changed elsewhere", old);

    assertThatThrownBy(() -> service.save(ana, setlist, SETLIST + "mine", old))
        .isInstanceOf(VersionConflict.class);
    assertThat(storage.text(setlistPath())).isEqualTo(SETLIST + "changed elsewhere");
  }

  @Test
  void ifTheCatalogCannotBeSavedTheFileIsWrittenBack() {
    UserId ana = writer();
    Fingerprint basedOn = service.open(ana, setlist).fingerprint();
    catalog.failNextSave = true;

    assertThatThrownBy(() -> service.save(ana, setlist, "lost", basedOn))
        .isInstanceOf(VersionConflict.class);
    assertThat(storage.text(setlistPath())).isEqualTo(SETLIST);
    assertThat(catalog.file(setlist).fingerprint()).isEqualTo(basedOn);
  }

  @Test
  void aTextOfMoreThanOneMebibyteIsRejected() {
    UserId ana = writer();
    Fingerprint basedOn = service.open(ana, setlist).fingerprint();
    String huge = "x".repeat(TextFilePolicy.MAX_BYTES + 1);

    assertThatThrownBy(() -> service.save(ana, setlist, huge, basedOn))
        .isInstanceOf(FileRejected.class);
    assertThat(storage.writes).isZero();
  }

  @Test
  void onlyTextFilesAreSaved() {
    UserId ana = writer();

    assertThatThrownBy(() -> service.save(ana, song, "not audio", sha256(new byte[] {1, 2, 3})))
        .isInstanceOf(FileRejected.class);
    assertThat(storage.writes).isZero();
  }
}
