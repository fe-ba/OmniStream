package co.omnistream.presentation.rest;

import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.IndexingStatus;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Snapshots and identifiers the API tests send and expect. */
final class Samples {

  static final String FILE = "00000000-0000-4000-8000-0000000000f1";
  static final String COLLECTION = "00000000-0000-4000-8000-0000000000c2";
  static final ItemId FILE_ID = new ItemId(UUID.fromString(FILE));
  static final ItemId COLLECTION_ID = new ItemId(UUID.fromString(COLLECTION));

  private Samples() {}

  static MediaFileSnapshot file() {
    return new MediaFileSnapshot(
        FILE_ID,
        "song.mp3",
        Path.of("/library/song.mp3"),
        new Fingerprint("SHA-256", "ab12"),
        MediaType.AUDIO,
        2048,
        Duration.ofSeconds(180),
        IndexingStatus.INDEXED,
        Instant.parse("2026-11-20T15:04:05Z"),
        List.of(),
        Set.of());
  }

  static CollectionSnapshot collection() {
    return new CollectionSnapshot(COLLECTION_ID, "Favorites", "", List.of(FILE_ID));
  }
}
