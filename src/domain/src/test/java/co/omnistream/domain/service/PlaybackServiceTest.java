package co.omnistream.domain.service;

import static co.omnistream.domain.model.access.AccessFixtures.item;
import static co.omnistream.domain.model.access.AccessFixtures.user;
import static co.omnistream.domain.model.catalog.CatalogFixtures.fingerprint;
import static co.omnistream.domain.service.ServiceTestDoubles.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.FileRejected;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.Collection;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaFile;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.playback.PlaybackSession;
import co.omnistream.domain.model.playback.SessionId;
import co.omnistream.domain.model.playback.SessionStatus;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.dto.MediaStream;
import co.omnistream.domain.port.dto.StreamProfile;
import co.omnistream.domain.service.ServiceTestDoubles.InMemoryCatalog;
import co.omnistream.domain.service.ServiceTestDoubles.InMemoryFileStorage;
import co.omnistream.domain.service.ServiceTestDoubles.InMemorySessions;
import co.omnistream.domain.service.ServiceTestDoubles.InMemoryUsers;
import co.omnistream.domain.service.ServiceTestDoubles.MutableClock;
import co.omnistream.domain.service.ServiceTestDoubles.StubTranscoder;
import co.omnistream.domain.service.stream.DirectStream;
import co.omnistream.domain.service.stream.TranscodedStream;
import co.omnistream.domain.snapshot.PlaybackSessionSnapshot;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlaybackServiceTest {

  private static final StreamProfile DIRECT =
      new StreamProfile(Optional.empty(), Optional.empty(), Optional.empty());
  private static final StreamProfile TRANSCODE_720P =
      new StreamProfile(Optional.of("h264"), Optional.of(1200), Optional.of("720p"));

  private final MutableClock clock = new MutableClock(NOW);
  private final InMemoryUsers users = new InMemoryUsers();
  private final InMemorySessions sessionRepository = new InMemorySessions();
  private final InMemoryCatalog catalogRepository = new InMemoryCatalog();
  private final InMemoryFileStorage storage = new InMemoryFileStorage();
  private final StubTranscoder transcoder = new StubTranscoder();

  private PlaybackService service;
  private UserId adminId;
  private UserId anaId;
  private UserId luisId;

  @BeforeEach
  void setUp() {
    service =
        new PlaybackService(
            users,
            new AccessPolicy(),
            clock,
            sessionRepository,
            catalogRepository,
            List.of(new DirectStream(storage), new TranscodedStream(transcoder)));

    User admin = user("admin", Role.ADMIN);
    User ana = user("ana", Role.USER);
    User luis = user("luis", Role.USER);
    users.save(admin);
    users.save(ana);
    users.save(luis);

    adminId = admin.toSnapshot().id();
    anaId = ana.toSnapshot().id();
    luisId = luis.toSnapshot().id();
  }

  @Test
  void openSessionStartsActiveSessionForAuthorizedUserAndRejectsUnauthorizedOrMissing() {
    ItemId movieId = ingestVideo("Movie", "/library/movie.mp4", 'a', Duration.ofMinutes(90), "raw");
    CatalogIndex catalog = catalogRepository.getCatalog();
    Collection folder = catalog.createCollection("Shared", NOW);
    catalog.addToCollection(folder.toSnapshot().id(), movieId, NOW);
    catalogRepository.save(catalog);

    User ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(folder.toSnapshot().id(), PermissionLevel.READ));
    users.save(ana);

    User guest = User.createGuest(folder.toSnapshot().id(), PermissionLevel.READ);
    users.save(guest);

    PlaybackSessionSnapshot anaSession = service.openSession(anaId, movieId);
    PlaybackSessionSnapshot guestSession = service.openSession(guest.toSnapshot().id(), movieId);

    assertThat(anaSession.fileId()).isEqualTo(movieId);
    assertThat(anaSession.userId()).isEqualTo(anaId);
    assertThat(anaSession.position()).isEqualTo(Duration.ZERO);
    assertThat(anaSession.totalDuration()).isEqualTo(Duration.ofMinutes(90));
    assertThat(anaSession.status()).isEqualTo(SessionStatus.ACTIVE);
    assertThat(anaSession.startedAt()).isEqualTo(NOW);
    assertThat(sessionRepository.findById(anaSession.id())).isPresent();
    assertThat(guestSession.userId()).isEqualTo(guest.toSnapshot().id());

    assertThatThrownBy(() -> service.openSession(luisId, movieId)).isInstanceOf(AccessDenied.class);
    assertThatThrownBy(() -> service.openSession(adminId, item()))
        .isInstanceOf(ResourceNotFound.class);
    assertThatThrownBy(() -> service.openSession(adminId, folder.toSnapshot().id()))
        .isInstanceOf(ResourceNotFound.class);

    User luis = users.findById(luisId).orElseThrow();
    luis.grant(new Permission(movieId, PermissionLevel.READ));
    luis.deactivate();
    users.save(luis);
    assertThatThrownBy(() -> service.openSession(luisId, movieId)).isInstanceOf(AccessDenied.class);
  }

  @Test
  void resumeFromLastPositionStartsAtPreviousUnfinishedLatestSessionWithoutReopeningIt() {
    ItemId movieId =
        ingestVideo("Series", "/library/series.mp4", 'b', Duration.ofMinutes(100), "series");
    ItemId photoId = ingestImage("Photo", "/library/photo.png", 'c', "png-bytes");

    User ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(movieId, PermissionLevel.READ));
    ana.grant(new Permission(photoId, PermissionLevel.READ));
    users.save(ana);

    PlaybackSessionSnapshot initial = service.resumeFromLastPosition(anaId, movieId);
    assertThat(initial.position()).isEqualTo(Duration.ZERO);

    service.recordProgress(anaId, initial.id(), Duration.ofMinutes(12));
    service.finishSession(anaId, initial.id());

    clock.advance(Duration.ofMinutes(5));
    PlaybackSessionSnapshot resumed = service.resumeFromLastPosition(anaId, movieId);
    assertThat(resumed.id()).isNotEqualTo(initial.id());
    assertThat(resumed.position()).isEqualTo(Duration.ofMinutes(12));
    assertThat(resumed.status()).isEqualTo(SessionStatus.ACTIVE);
    assertThat(sessionRepository.findById(initial.id()).orElseThrow().toSnapshot().status())
        .isEqualTo(SessionStatus.FINISHED);

    service.recordProgress(anaId, resumed.id(), Duration.ofMinutes(96));
    clock.advance(Duration.ofMinutes(5));
    PlaybackSessionSnapshot completedRestart = service.resumeFromLastPosition(anaId, movieId);
    assertThat(completedRestart.position()).isEqualTo(Duration.ZERO);

    PlaybackSessionSnapshot firstZeroDuration = service.openSession(anaId, photoId);
    service.finishSession(anaId, firstZeroDuration.id());
    clock.advance(Duration.ofMinutes(5));
    PlaybackSessionSnapshot secondZeroDuration = service.resumeFromLastPosition(anaId, photoId);
    assertThat(secondZeroDuration.position()).isEqualTo(Duration.ZERO);
  }

  @Test
  void requestStreamSelectsStrategyKeepsPausedAndRevalidatesSessionAndAccess() throws IOException {
    byte[] raw = "video-payload".getBytes(StandardCharsets.UTF_8);
    ItemId movieId =
        ingestVideo("Clip", "/library/clip.mp4", 'd', Duration.ofMinutes(20), "video-payload");
    ItemId songId = ingestAudio("Song", "/library/song.mp3", 'e', Duration.ofMinutes(3), "audio");

    User ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(movieId, PermissionLevel.READ));
    ana.grant(new Permission(songId, PermissionLevel.READ));
    users.save(ana);

    PlaybackSessionSnapshot session = service.openSession(anaId, movieId);
    service.recordProgress(anaId, session.id(), Duration.ofMinutes(4));
    service.pauseSession(anaId, session.id());

    MediaStream direct = service.requestStream(anaId, session.id(), DIRECT);
    try (InputStream data = direct.data()) {
      assertThat(direct.transcoded()).isFalse();
      assertThat(direct.mime()).isEqualTo("video/mp4");
      assertThat(direct.sizeBytes()).contains((long) raw.length);
      assertThat(data.readAllBytes()).isEqualTo(raw);
    }
    assertThat(sessionRepository.findById(session.id()).orElseThrow().toSnapshot().status())
        .isEqualTo(SessionStatus.PAUSED);

    MediaStream transcoded = service.requestStream(anaId, session.id(), TRANSCODE_720P);
    try (InputStream data = transcoded.data()) {
      assertThat(transcoded.transcoded()).isTrue();
      assertThat(transcoded.sizeBytes()).isEmpty();
      assertThat(transcoder.lastPosition).isEqualTo(Duration.ofMinutes(4));
      assertThat(data.readAllBytes()).isNotEmpty();
    }

    transcoder.supportedTypes = Set.of(MediaType.AUDIO);
    assertThatThrownBy(() -> service.requestStream(anaId, session.id(), TRANSCODE_720P))
        .isInstanceOf(FileRejected.class);

    ana = users.findById(anaId).orElseThrow();
    ana.revoke(new Permission(movieId, PermissionLevel.READ));
    users.save(ana);
    assertThatThrownBy(() -> service.requestStream(anaId, session.id(), DIRECT))
        .isInstanceOf(AccessDenied.class);

    ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(movieId, PermissionLevel.READ));
    users.save(ana);
    CatalogIndex catalog = catalogRepository.getCatalog();
    catalog.delete(movieId, NOW);
    catalogRepository.save(catalog);
    assertThatThrownBy(() -> service.requestStream(anaId, session.id(), DIRECT))
        .isInstanceOf(ResourceNotFound.class);

    PlaybackSessionSnapshot songSession = service.openSession(anaId, songId);
    service.finishSession(anaId, songSession.id());
    assertThatThrownBy(() -> service.requestStream(anaId, songSession.id(), DIRECT))
        .isInstanceOf(AccessDenied.class);
  }

  @Test
  void progressPauseResumeAndFinishFollowLifecycleAndResilienceRules() {
    ItemId movieId =
        ingestVideo("Film", "/library/film.mp4", 'f', Duration.ofMinutes(10), "film-bytes");
    User ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(movieId, PermissionLevel.READ));
    users.save(ana);

    PlaybackSessionSnapshot session = service.openSession(anaId, movieId);

    service.recordProgress(anaId, session.id(), Duration.ofMinutes(6));
    assertThat(sessionRepository.findById(session.id()).orElseThrow().toSnapshot().position())
        .isEqualTo(Duration.ofMinutes(6));

    service.recordProgress(anaId, session.id(), Duration.ofMinutes(2));
    assertThat(sessionRepository.findById(session.id()).orElseThrow().toSnapshot().position())
        .isEqualTo(Duration.ofMinutes(2));

    service.recordProgress(anaId, session.id(), Duration.ofMinutes(25));
    assertThat(sessionRepository.findById(session.id()).orElseThrow().toSnapshot().position())
        .isEqualTo(Duration.ofMinutes(10));

    assertThatThrownBy(() -> service.recordProgress(anaId, session.id(), Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);

    service.pauseSession(anaId, session.id());
    service.pauseSession(anaId, session.id());
    assertThat(sessionRepository.findById(session.id()).orElseThrow().toSnapshot().status())
        .isEqualTo(SessionStatus.PAUSED);

    service.resumeSession(anaId, session.id());
    service.resumeSession(anaId, session.id());
    assertThat(sessionRepository.findById(session.id()).orElseThrow().toSnapshot().status())
        .isEqualTo(SessionStatus.ACTIVE);

    service.recordProgress(anaId, session.id(), Duration.ofMinutes(3));
    service.finishSession(anaId, session.id());
    service.finishSession(anaId, session.id());

    // Late progress and pause on FINISHED session are idempotent no-ops (ADR 0029).
    service.recordProgress(anaId, session.id(), Duration.ofMinutes(8));
    service.pauseSession(anaId, session.id());

    PlaybackSession saved = sessionRepository.findById(session.id()).orElseThrow();
    assertThat(saved.toSnapshot().status()).isEqualTo(SessionStatus.FINISHED);
    assertThat(saved.toSnapshot().position()).isEqualTo(Duration.ofMinutes(3));

    assertThatThrownBy(() -> service.resumeSession(anaId, session.id()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void sessionOperationsEnforceStrictOwnershipEvenAgainstAdminsAndDeactivatedOwners() {
    ItemId movieId =
        ingestVideo("Private", "/library/private.mp4", '1', Duration.ofMinutes(15), "private");
    User ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(movieId, PermissionLevel.READ));
    users.save(ana);

    SessionId sessionId = service.openSession(anaId, movieId).id();
    SessionId missing = new SessionId(UUID.randomUUID());

    for (UserId intruder : List.of(luisId, adminId)) {
      assertThatThrownBy(() -> service.requestStream(intruder, sessionId, DIRECT))
          .isInstanceOf(AccessDenied.class);
      assertThatThrownBy(() -> service.recordProgress(intruder, sessionId, Duration.ofMinutes(1)))
          .isInstanceOf(AccessDenied.class);
      assertThatThrownBy(() -> service.pauseSession(intruder, sessionId))
          .isInstanceOf(AccessDenied.class);
      assertThatThrownBy(() -> service.resumeSession(intruder, sessionId))
          .isInstanceOf(AccessDenied.class);
      assertThatThrownBy(() -> service.finishSession(intruder, sessionId))
          .isInstanceOf(AccessDenied.class);
    }

    assertThatThrownBy(() -> service.pauseSession(anaId, missing))
        .isInstanceOf(ResourceNotFound.class);

    User owner = users.findById(anaId).orElseThrow();
    owner.deactivate();
    users.save(owner);
    assertThatThrownBy(() -> service.pauseSession(anaId, sessionId))
        .isInstanceOf(AccessDenied.class);
  }

  private ItemId ingestVideo(
      String name, String rawPath, char seed, Duration duration, String payload) {
    return ingest(name, rawPath, MediaType.VIDEO, seed, duration, payload);
  }

  private ItemId ingestAudio(
      String name, String rawPath, char seed, Duration duration, String payload) {
    return ingest(name, rawPath, MediaType.AUDIO, seed, duration, payload);
  }

  private ItemId ingestImage(String name, String rawPath, char seed, String payload) {
    return ingest(name, rawPath, MediaType.IMAGE, seed, Duration.ZERO, payload);
  }

  private ItemId ingest(
      String name, String rawPath, MediaType type, char seed, Duration duration, String payload) {
    Path path = Path.of(rawPath);
    byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
    storage.put(path, bytes);
    MediaFile file =
        MediaFile.create(name, path, fingerprint(seed), type, bytes.length, duration, NOW);
    CatalogIndex catalog = catalogRepository.getCatalog();
    catalog.ingest(file, NOW);
    catalogRepository.save(catalog);
    return file.toSnapshot().id();
  }
}
