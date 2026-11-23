package co.omnistream.presentation.rest;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.TextDocument;
import co.omnistream.domain.port.in.ITextEditing;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The rooms of the collaborative editor (ADR 0027): everyone editing the same file is in one room,
 * and the room passes each edit on to the others. It never decodes an edit: the browsers merge them
 * with Yjs.
 *
 * <p>The protocol, as binary frames whose first byte says what they carry:
 *
 * <ul>
 *   <li>{@value #UPDATE}: a Yjs update. Kept, so whoever joins later catches up, and passed on to
 *       the others. Dropped if it comes from someone who may only read.
 *   <li>{@value #AWARENESS}: who is there and where their cursor is. Passed on, never kept.
 *   <li>{@value #STATE}: the whole document as one update, sent when the room asked for it ({@code
 *       compact}); it takes the place of the updates kept so far.
 *   <li>{@value #SAVE}: the text to save, in UTF-8. The room saves it based on the last fingerprint
 *       it knows, and tells everyone the new one.
 * </ul>
 *
 * <p>And as JSON text messages from the room to a browser: {@code welcome} (with the text, and
 * {@code seed} true for the one who must load it into the document), {@code seed} (you load it now,
 * the previous one left), {@code saved}, {@code compact} and {@code error}.
 *
 * <p>Each room is changed by one thread at a time; a save holds its room until it is written.
 */
final class EditingRooms {

  static final byte UPDATE = 0;
  static final byte AWARENESS = 1;
  static final byte STATE = 2;
  static final byte SAVE = 3;

  /** Updates kept before the room asks someone for the whole document instead. */
  static final int COMPACT_AFTER = 500;

  /** Someone in a room: a browser tab connected to the relay. */
  interface Peer {
    UserId user();

    void send(Map<String, Object> message);

    void send(byte[] frame);

    void close(int code, String reason);
  }

  /** What went wrong, as the REST API would answer it. */
  record Failure(int status, String error, String message) {}

  private final ITextEditing textEditing;
  private final Function<RuntimeException, Failure> failures;
  private final int compactAfter;
  private final Map<ItemId, Room> rooms = new HashMap<>();
  private final Map<Peer, Room> roomOf = new HashMap<>();

  EditingRooms(ITextEditing textEditing, Function<RuntimeException, Failure> failures) {
    this(textEditing, failures, COMPACT_AFTER);
  }

  EditingRooms(
      ITextEditing textEditing, Function<RuntimeException, Failure> failures, int compactAfter) {
    this.textEditing = Objects.requireNonNull(textEditing, "textEditing");
    this.failures = Objects.requireNonNull(failures, "failures");
    this.compactAfter = compactAfter;
  }

  /**
   * Opens the file for {@code p} and puts it in the file's room. If the file cannot be opened, the
   * connection is closed with 4000 plus the HTTP status the API would answer (4403, 4404, 4422).
   */
  void join(ItemId file, Peer p) {
    TextDocument doc;
    try {
      doc = textEditing.open(p.user(), file);
    } catch (RuntimeException e) {
      Failure failure = failures.apply(e);
      p.close(4000 + failure.status(), reason(failure.message()));
      return;
    }
    Room room;
    synchronized (rooms) {
      room = rooms.computeIfAbsent(file, Room::new);
      roomOf.put(p, room);
      synchronized (room) {
        room.join(new Member(p, doc.editable()), doc);
      }
    }
  }

  /** Handles a frame from {@code p}; frames from someone who is not in a room are ignored. */
  void receive(Peer p, byte[] frame) {
    Room room;
    synchronized (rooms) {
      room = roomOf.get(p);
    }
    if (room == null || frame.length == 0) {
      return;
    }
    synchronized (room) {
      room.receive(p, frame);
    }
  }

  /**
   * Takes {@code p} out of its room; the room goes away with the last one. Leaving twice is fine.
   */
  void leave(Peer p) {
    synchronized (rooms) {
      Room room = roomOf.remove(p);
      if (room == null) {
        return;
      }
      synchronized (room) {
        room.leave(p);
        if (room.members.isEmpty()) {
          rooms.remove(room.file);
        }
      }
    }
  }

  /** How many rooms are open. */
  int openRooms() {
    synchronized (rooms) {
      return rooms.size();
    }
  }

  /** A close reason has at most 123 bytes in UTF-8. */
  private static String reason(String message) {
    String reason = message;
    while (reason.getBytes(StandardCharsets.UTF_8).length > 123) {
      reason = reason.substring(0, reason.length() - 1);
    }
    return reason;
  }

  private static Map<String, Object> message(String type, Object... pairs) {
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("type", type);
    for (int i = 0; i < pairs.length; i += 2) {
      message.put((String) pairs[i], pairs[i + 1]);
    }
    return message;
  }

  private record Member(Peer peer, boolean editable) {}

  private final class Room {
    final ItemId file;
    final List<Member> members = new ArrayList<>();
    final List<byte[]> updates = new ArrayList<>();
    Fingerprint fingerprint;
    Member seeder;
    Member compactor;
    int compactMark;

    Room(ItemId file) {
      this.file = file;
    }

    void join(Member m, TextDocument doc) {
      if (fingerprint == null) {
        fingerprint = doc.fingerprint();
      }
      boolean seed = m.editable() && updates.isEmpty() && seeder == null;
      if (seed) {
        seeder = m;
      }
      m.peer()
          .send(
              message(
                  "welcome",
                  "file",
                  doc.file(),
                  "name",
                  doc.name(),
                  "editable",
                  doc.editable(),
                  "seed",
                  seed,
                  "text",
                  doc.text(),
                  "fingerprint",
                  fingerprint,
                  "others",
                  members.size()));
      updates.forEach(m.peer()::send);
      members.add(m);
    }

    void receive(Peer p, byte[] frame) {
      Optional<Member> sender = member(p);
      if (sender.isEmpty()) {
        return;
      }
      Member m = sender.get();
      switch (frame[0]) {
        case UPDATE -> update(m, frame);
        case AWARENESS -> others(m, frame);
        case STATE -> state(m, frame);
        case SAVE -> save(m, Arrays.copyOfRange(frame, 1, frame.length));
        default ->
            m.peer().send(message("error", "error", "BadRequest", "message", "Unknown frame"));
      }
    }

    private void update(Member m, byte[] frame) {
      if (!m.editable()) {
        return;
      }
      updates.add(frame);
      if (seeder == m) {
        seeder = null;
      }
      others(m, frame);
      if (updates.size() > compactAfter && compactor == null) {
        compactor = m;
        compactMark = updates.size();
        m.peer().send(message("compact"));
      }
    }

    /**
     * The whole document replaces the updates kept before it was asked for. Updates that arrived
     * since are kept too: the document may or may not include them, and Yjs applies an update it
     * already has without changing anything.
     */
    private void state(Member m, byte[] frame) {
      if (compactor != m) {
        return;
      }
      byte[] whole = frame.clone();
      whole[0] = UPDATE;
      List<byte[]> since = new ArrayList<>(updates.subList(compactMark, updates.size()));
      updates.clear();
      updates.add(whole);
      updates.addAll(since);
      compactor = null;
    }

    private void save(Member m, byte[] text) {
      if (updates.isEmpty()) {
        m.peer()
            .send(
                message("error", "error", "NotReady", "message", "The document is not loaded yet"));
        return;
      }
      try {
        TextDocument saved =
            textEditing.save(
                m.peer().user(), file, new String(text, StandardCharsets.UTF_8), fingerprint);
        fingerprint = saved.fingerprint();
        Map<String, Object> news =
            message("saved", "fingerprint", fingerprint, "by", m.peer().user());
        members.forEach(member -> member.peer().send(news));
      } catch (RuntimeException e) {
        Failure failure = failures.apply(e);
        m.peer().send(message("error", "error", failure.error(), "message", failure.message()));
      }
    }

    void leave(Peer p) {
      Optional<Member> leaving = member(p);
      if (leaving.isEmpty()) {
        return;
      }
      Member m = leaving.get();
      members.remove(m);
      if (compactor == m) {
        compactor = null;
      }
      if (seeder == m) {
        seeder = null;
        if (updates.isEmpty()) {
          members.stream()
              .filter(Member::editable)
              .findFirst()
              .ifPresent(next -> {
                seeder = next;
                next.peer().send(message("seed"));
              });
        }
      }
    }

    private void others(Member from, byte[] frame) {
      for (Member m : members) {
        if (m != from) {
          m.peer().send(frame);
        }
      }
    }

    private Optional<Member> member(Peer p) {
      return members.stream().filter(m -> m.peer() == p).findFirst();
    }
  }
}
