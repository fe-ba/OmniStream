package co.omnistream.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.exception.VersionConflict;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.TextDocument;
import co.omnistream.domain.port.in.ITextEditing;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EditingRoomsTest {

  private static final ItemId FILE = new ItemId(UUID.randomUUID());
  private static final Fingerprint F1 = new Fingerprint("SHA-256", "1".repeat(64));
  private static final Fingerprint F2 = new Fingerprint("SHA-256", "2".repeat(64));
  private static final String TEXT = "# Setlist\n";

  /** A browser tab that remembers everything it was sent. */
  private static final class Tab implements EditingRooms.Peer {
    final UserId user = new UserId(UUID.randomUUID());
    final List<Map<String, Object>> messages = new ArrayList<>();
    final List<byte[]> frames = new ArrayList<>();
    int closedWith;
    String reason;

    @Override
    public UserId user() {
      return user;
    }

    @Override
    public void send(Map<String, Object> message) {
      messages.add(message);
    }

    @Override
    public void send(byte[] frame) {
      frames.add(frame);
    }

    @Override
    public void close(int code, String reason) {
      closedWith = code;
      this.reason = reason;
    }

    Map<String, Object> last() {
      return messages.get(messages.size() - 1);
    }
  }

  /** Text editing in memory: who may write, and what was saved. */
  private static final class Editing implements ITextEditing {
    final Set<UserId> writers = new java.util.HashSet<>();
    final List<String> saved = new ArrayList<>();
    final List<Fingerprint> basedOn = new ArrayList<>();
    RuntimeException failure;
    Fingerprint stored = F1;

    @Override
    public TextDocument open(UserId u, ItemId file) {
      if (failure != null) {
        throw failure;
      }
      return new TextDocument(file, "setlist.md", TEXT, stored, writers.contains(u));
    }

    @Override
    public TextDocument save(UserId u, ItemId file, String text, Fingerprint basedOn) {
      if (failure != null) {
        throw failure;
      }
      saved.add(text);
      this.basedOn.add(basedOn);
      stored = F2;
      return new TextDocument(file, "setlist.md", text, F2, true);
    }
  }

  private final Editing editing = new Editing();
  private final EditingRooms rooms =
      new EditingRooms(
          editing,
          e -> {
            int status = e instanceof AccessDenied ? 403 : e instanceof VersionConflict ? 409 : 404;
            return new EditingRooms.Failure(status, e.getClass().getSimpleName(), e.getMessage());
          },
          3);

  private Tab writer() {
    Tab tab = new Tab();
    editing.writers.add(tab.user);
    return tab;
  }

  private static byte[] frame(byte kind, String body) {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    byte[] frame = new byte[bytes.length + 1];
    frame[0] = kind;
    System.arraycopy(bytes, 0, frame, 1, bytes.length);
    return frame;
  }

  private static String body(byte[] frame) {
    return new String(frame, 1, frame.length - 1, StandardCharsets.UTF_8);
  }

  // ------------------------------------------------------------------ joining

  @Test
  void theFirstEditorIsWelcomedWithTheTextAndSeedsTheDocument() {
    Tab ana = writer();

    rooms.join(FILE, ana);

    Map<String, Object> welcome = ana.last();
    assertThat(welcome.get("type")).isEqualTo("welcome");
    assertThat(welcome.get("file")).isEqualTo(FILE);
    assertThat(welcome.get("name")).isEqualTo("setlist.md");
    assertThat(welcome.get("text")).isEqualTo(TEXT);
    assertThat(welcome.get("fingerprint")).isEqualTo(F1);
    assertThat(welcome.get("editable")).isEqualTo(true);
    assertThat(welcome.get("seed")).isEqualTo(true);
    assertThat(welcome.get("others")).isEqualTo(0);
    assertThat(rooms.openRooms()).isEqualTo(1);
  }

  @Test
  void whoeverJoinsLaterReceivesTheEditsInsteadOfSeeding() {
    Tab ana = writer();
    rooms.join(FILE, ana);
    rooms.receive(ana, frame(EditingRooms.UPDATE, "seed"));
    rooms.receive(ana, frame(EditingRooms.UPDATE, "typed"));
    Tab luis = writer();

    rooms.join(FILE, luis);

    assertThat(luis.messages.get(0).get("seed")).isEqualTo(false);
    assertThat(luis.messages.get(0).get("others")).isEqualTo(1);
    assertThat(luis.frames).extracting(EditingRoomsTest::body).containsExactly("seed", "typed");
  }

  @Test
  void onlyOneEditorSeedsEvenIfTheSecondArrivesBeforeTheFirstEdit() {
    Tab ana = writer();
    Tab luis = writer();

    rooms.join(FILE, ana);
    rooms.join(FILE, luis);

    assertThat(ana.messages.get(0).get("seed")).isEqualTo(true);
    assertThat(luis.messages.get(0).get("seed")).isEqualTo(false);
  }

  @Test
  void aReaderNeverSeeds() {
    Tab reader = new Tab();

    rooms.join(FILE, reader);

    assertThat(reader.last().get("seed")).isEqualTo(false);
    assertThat(reader.last().get("editable")).isEqualTo(false);
  }

  @Test
  void ifTheSeederLeavesBeforeSeedingTheNextEditorSeeds() {
    Tab ana = writer();
    Tab reader = new Tab();
    Tab luis = writer();
    rooms.join(FILE, ana);
    rooms.join(FILE, reader);
    rooms.join(FILE, luis);

    rooms.leave(ana);

    assertThat(luis.last().get("type")).isEqualTo("seed");
    assertThat(reader.messages).hasSize(1);
  }

  @Test
  void aFileThatCannotBeOpenedClosesTheConnection() {
    editing.failure = new ResourceNotFound("No file");
    Tab ana = writer();

    rooms.join(FILE, ana);

    assertThat(ana.closedWith).isEqualTo(4404);
    assertThat(ana.reason).isEqualTo("No file");
    assertThat(ana.messages).isEmpty();
    assertThat(rooms.openRooms()).isZero();
  }

  @Test
  void aLongReasonIsCutTo123Bytes() {
    editing.failure = new AccessDenied("ñ".repeat(100));
    Tab ana = writer();

    rooms.join(FILE, ana);

    assertThat(ana.closedWith).isEqualTo(4403);
    assertThat(ana.reason.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(123);
  }

  // ------------------------------------------------------------------ relaying

  @Test
  void editsAndPresenceGoToTheOthersButNotBackToTheSender() {
    Tab ana = writer();
    Tab luis = writer();
    rooms.join(FILE, ana);
    rooms.join(FILE, luis);

    rooms.receive(ana, frame(EditingRooms.UPDATE, "edit"));
    rooms.receive(ana, frame(EditingRooms.AWARENESS, "cursor"));

    assertThat(luis.frames).extracting(EditingRoomsTest::body).containsExactly("edit", "cursor");
    assertThat(ana.frames).isEmpty();
  }

  @Test
  void presenceIsNotKeptForLaterArrivals() {
    Tab ana = writer();
    rooms.join(FILE, ana);
    rooms.receive(ana, frame(EditingRooms.UPDATE, "edit"));
    rooms.receive(ana, frame(EditingRooms.AWARENESS, "cursor"));
    Tab luis = writer();

    rooms.join(FILE, luis);

    assertThat(luis.frames).extracting(EditingRoomsTest::body).containsExactly("edit");
  }

  @Test
  void editsOfAReaderAreDroppedButItsPresenceIsShown() {
    Tab ana = writer();
    Tab reader = new Tab();
    rooms.join(FILE, ana);
    rooms.join(FILE, reader);

    rooms.receive(reader, frame(EditingRooms.UPDATE, "sneaky"));
    rooms.receive(reader, frame(EditingRooms.AWARENESS, "here"));

    assertThat(ana.frames).extracting(EditingRoomsTest::body).containsExactly("here");
  }

  @Test
  void anUnknownFrameIsAnError() {
    Tab ana = writer();
    rooms.join(FILE, ana);

    rooms.receive(ana, new byte[] {9});

    assertThat(ana.last().get("type")).isEqualTo("error");
  }

  @Test
  void theRoomGoesAwayWithTheLastOne() {
    Tab ana = writer();
    Tab luis = writer();
    rooms.join(FILE, ana);
    rooms.join(FILE, luis);

    rooms.leave(ana);
    rooms.leave(ana);
    assertThat(rooms.openRooms()).isEqualTo(1);
    rooms.leave(luis);

    assertThat(rooms.openRooms()).isZero();
  }

  // ------------------------------------------------------------------ compacting

  @Test
  void afterManyEditsTheRoomAsksForTheWholeDocumentAndKeepsThatInstead() {
    Tab ana = writer();
    rooms.join(FILE, ana);
    for (int i = 1; i <= 4; i++) {
      rooms.receive(ana, frame(EditingRooms.UPDATE, "e" + i));
    }
    assertThat(ana.last().get("type")).isEqualTo("compact");
    rooms.receive(ana, frame(EditingRooms.UPDATE, "e5"));

    rooms.receive(ana, frame(EditingRooms.STATE, "whole"));
    Tab luis = writer();
    rooms.join(FILE, luis);

    assertThat(luis.frames).extracting(EditingRoomsTest::body).containsExactly("whole", "e5");
    assertThat(luis.frames.get(0)[0]).isEqualTo(EditingRooms.UPDATE);
  }

  @Test
  void aWholeDocumentNobodyAskedForIsIgnored() {
    Tab ana = writer();
    rooms.join(FILE, ana);
    rooms.receive(ana, frame(EditingRooms.UPDATE, "e1"));

    rooms.receive(ana, frame(EditingRooms.STATE, "forged"));
    Tab luis = writer();
    rooms.join(FILE, luis);

    assertThat(luis.frames).extracting(EditingRoomsTest::body).containsExactly("e1");
  }

  // ------------------------------------------------------------------ saving

  @Test
  void aSaveIsBasedOnTheRoomsFingerprintAndEveryoneHearsTheNewOne() {
    Tab ana = writer();
    Tab luis = writer();
    rooms.join(FILE, ana);
    rooms.join(FILE, luis);
    rooms.receive(ana, frame(EditingRooms.UPDATE, "seed"));

    rooms.receive(ana, frame(EditingRooms.SAVE, "# Setlist ñ\n"));
    rooms.receive(luis, frame(EditingRooms.SAVE, "# Setlist ñ\n"));

    assertThat(editing.saved).containsExactly("# Setlist ñ\n", "# Setlist ñ\n");
    assertThat(editing.basedOn).containsExactly(F1, F2);
    for (Tab tab : List.of(ana, luis)) {
      assertThat(tab.last().get("type")).isEqualTo("saved");
      assertThat(tab.last().get("fingerprint")).isEqualTo(F2);
    }
    assertThat(ana.messages.get(ana.messages.size() - 2).get("by")).isEqualTo(ana.user);
  }

  @Test
  void nothingIsSavedBeforeTheDocumentIsLoaded() {
    Tab ana = writer();
    rooms.join(FILE, ana);

    rooms.receive(ana, frame(EditingRooms.SAVE, ""));

    assertThat(editing.saved).isEmpty();
    assertThat(ana.last().get("error")).isEqualTo("NotReady");
  }

  @Test
  void aFailedSaveIsToldOnlyToWhoeverSaved() {
    Tab ana = writer();
    Tab luis = writer();
    rooms.join(FILE, ana);
    rooms.join(FILE, luis);
    rooms.receive(ana, frame(EditingRooms.UPDATE, "seed"));
    editing.failure = new VersionConflict("Changed outside the editor");

    rooms.receive(ana, frame(EditingRooms.SAVE, "mine"));

    assertThat(ana.last().get("type")).isEqualTo("error");
    assertThat(ana.last().get("error")).isEqualTo("VersionConflict");
    assertThat(ana.last().get("message")).isEqualTo("Changed outside the editor");
    assertThat(luis.messages).hasSize(1);
  }

  @Test
  void framesFromSomeoneOutsideARoomAreIgnored() {
    Tab stranger = writer();

    rooms.receive(stranger, frame(EditingRooms.SAVE, "x"));

    assertThat(editing.saved).isEmpty();
    assertThat(stranger.messages).isEmpty();
  }
}
