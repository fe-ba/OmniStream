package co.omnistream.domain.service.ingestion;

import static co.omnistream.domain.model.catalog.CatalogFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.event.FileClassified;
import co.omnistream.domain.event.FileDeleted;
import co.omnistream.domain.event.FileIngested;
import co.omnistream.domain.exception.FileRejected;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.catalog.CatalogId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.DuplicateAction;
import co.omnistream.domain.model.catalog.IngestionResult;
import co.omnistream.domain.model.catalog.MediaFile;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.model.catalog.MetadataSource;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.model.catalog.TagSource;
import co.omnistream.domain.policy.DeduplicationPolicy;
import co.omnistream.domain.port.out.IMetadataExtractor;
import co.omnistream.domain.port.out.memory.InMemoryFileStorage;
import co.omnistream.domain.service.classification.DateRule;
import co.omnistream.domain.service.classification.MediaTypeRule;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngestionChainTest {

  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final InMemoryFileStorage storage = new InMemoryFileStorage();
  private final StubMetadataExtractor extractor = new StubMetadataExtractor();

  private void put(Path path, byte[] bytes) {
    storage.write(path, new ByteArrayInputStream(bytes));
  }

  private IngestionStep fullChain() {
    ExtractionStep extraction = new ExtractionStep(storage, extractor, CLOCK);
    DuplicatesStep duplicates = new DuplicatesStep(new DeduplicationPolicy());
    ClassificationStep classification =
        new ClassificationStep(List.of(new MediaTypeRule(), new DateRule()));
    IndexingStep indexing = new IndexingStep();
    extraction.chain(duplicates).chain(classification).chain(indexing);
    return extraction;
  }

  private static CatalogIndex emptyCatalog() {
    return CatalogIndex.fromSnapshot(
        new CatalogSnapshot(
            new CatalogId(UUID.randomUUID()), "OmniStream", 0L, List.of(), List.of()));
  }

  @Test
  void chainRejectsSelfLinkingAndStopsWhenAStepReturnsFalse() {
    List<String> executed = new ArrayList<>();
    IngestionStep first =
        new IngestionStep() {
          @Override
          protected boolean process(IngestionContext ctx) {
            executed.add("first");
            return false;
          }
        };
    IngestionStep second =
        new IngestionStep() {
          @Override
          protected boolean process(IngestionContext ctx) {
            executed.add("second");
            return true;
          }
        };
    first.chain(second);
    IngestionContext ctx = new IngestionContext();

    first.execute(ctx);

    assertThat(executed).containsExactly("first");
    assertThatThrownBy(() -> first.chain(first)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ingestsExtractsAndAppliesFixedClassificationRules() {
    Path path = Path.of("/library/concert.mp4");
    byte[] bytes = "video-content".getBytes(StandardCharsets.UTF_8);
    put(path, bytes);
    extractor.put(
        path,
        List.of(
            new MetadataEntry("captureDate", "2025-07-14T18:00:00Z", MetadataSource.EXTRACTED),
            new MetadataEntry("duration", "95.5", MetadataSource.EXTRACTED)));
    CatalogIndex catalog = emptyCatalog();
    IngestionContext ctx = context(catalog, path);

    IngestionResult result = fullChain().execute(ctx);
    MediaFileSnapshot stored = catalog.toSnapshot().files().getFirst();

    assertThat(result.wasIndexed()).isTrue();
    assertThat(result.duplicate()).isFalse();
    assertThat(stored.type()).isEqualTo(MediaType.VIDEO);
    assertThat(stored.sizeBytes()).isEqualTo(bytes.length);
    assertThat(stored.duration()).isEqualTo(Duration.ofMillis(95_500));
    assertThat(stored.ingestedAt()).isEqualTo(NOW);
    assertThat(stored.tags())
        .containsExactlyInAnyOrder(
            new Tag("video", TagSource.AUTOMATIC),
            new Tag("2025", TagSource.AUTOMATIC),
            new Tag("2025-07", TagSource.AUTOMATIC));
    assertThat(eventTypes(catalog)).containsExactly(FileIngested.class, FileClassified.class);
  }

  @Test
  void stopsChainWhenDuplicateAtSamePathIsRejected() {
    Path path = Path.of("/library/song.mp3");
    put(path, "same-audio".getBytes(StandardCharsets.UTF_8));
    extractor.put(path, List.of(new MetadataEntry("duration", "PT3M", MetadataSource.EXTRACTED)));
    CatalogIndex catalog = emptyCatalog();
    IngestionResult first = fullChain().execute(context(catalog, path));
    catalog.pullEvents();

    IngestionResult second = fullChain().execute(context(catalog, path));

    assertThat(second.wasIndexed()).isFalse();
    assertThat(second.fileId()).isEqualTo(first.fileId());
    assertThat(second.action()).contains(DuplicateAction.REJECT);
    assertThat(catalog.totalFiles()).isEqualTo(1);
    assertThat(catalog.pullEvents()).isEmpty();
  }

  @Test
  void keepsBothWhenDuplicateIsAtAnotherPathAndReplacesErroredFile() {
    Path original = Path.of("/library/photo.jpg");
    Path backup = Path.of("/backup/photo.jpg");
    byte[] bytes = "image-data".getBytes(StandardCharsets.UTF_8);
    put(original, bytes);
    put(backup, bytes);
    CatalogIndex catalog = emptyCatalog();
    IngestionResult first = fullChain().execute(context(catalog, original));
    MediaFile firstEntity = (MediaFile) catalog.findItem(first.fileId()).orElseThrow();
    firstEntity.tag(new Tag("favorites", TagSource.MANUAL));

    IngestionResult keptBoth = fullChain().execute(context(catalog, backup));
    assertThat(keptBoth.wasIndexed()).isTrue();
    assertThat(keptBoth.action()).contains(DuplicateAction.KEEP_BOTH);
    assertThat(catalog.totalFiles()).isEqualTo(2);

    firstEntity.markFailed("corrupted index");
    catalog.pullEvents();
    Path replacement = Path.of("/incoming/photo.jpg");
    put(replacement, bytes);

    IngestionResult replaced = fullChain().execute(context(catalog, replacement));
    assertThat(replaced.wasIndexed()).isTrue();
    assertThat(replaced.action()).contains(DuplicateAction.REPLACE);
    assertThat(eventTypes(catalog))
        .containsExactly(FileDeleted.class, FileIngested.class, FileClassified.class);
  }

  @Test
  void rejectsUnsupportedExtensionAndMissingFileAndRespectsMetadataSizeOverride() {
    Path missing = Path.of("/library/missing.mp4");
    Path unknownExt = Path.of("/library/archive.bin");
    Path doc = Path.of("/library/notes.md");
    put(unknownExt, "binary".getBytes(StandardCharsets.UTF_8));
    put(doc, "hello".getBytes(StandardCharsets.UTF_8));
    extractor.unsupportedTypes = Set.of(MediaType.DOCUMENT);

    CatalogIndex catalog = emptyCatalog();
    assertThatThrownBy(() -> fullChain().execute(context(catalog, missing)))
        .isInstanceOf(ResourceNotFound.class);
    assertThatThrownBy(() -> fullChain().execute(context(catalog, unknownExt)))
        .isInstanceOf(FileRejected.class);

    IngestionResult docResult = fullChain().execute(context(catalog, doc));
    MediaFileSnapshot docSnapshot =
        catalog.findItem(docResult.fileId()).orElseThrow().toSnapshot()
                instanceof MediaFileSnapshot m
            ? m
            : null;
    assertThat(docSnapshot).isNotNull();
    assertThat(docSnapshot.type()).isEqualTo(MediaType.DOCUMENT);
    assertThat(docSnapshot.metadata()).isEmpty();
    assertThat(docSnapshot.sizeBytes()).isEqualTo(5);

    Path sized = Path.of("/library/clip.mkv");
    put(sized, "short".getBytes(StandardCharsets.UTF_8));
    extractor.unsupportedTypes = Set.of();
    extractor.put(sized, List.of(new MetadataEntry("sizeBytes", "4096", MetadataSource.EXTRACTED)));
    IngestionResult sizedResult = fullChain().execute(context(catalog, sized));
    MediaFileSnapshot sizedSnapshot =
        (MediaFileSnapshot) catalog.findItem(sizedResult.fileId()).orElseThrow().toSnapshot();
    assertThat(sizedSnapshot.sizeBytes()).isEqualTo(4096L);
  }

  private static List<Class<?>> eventTypes(CatalogIndex catalog) {
    return catalog.pullEvents().stream().<Class<?>>map(Object::getClass).toList();
  }

  private static IngestionContext context(CatalogIndex catalog, Path path) {
    IngestionContext ctx = new IngestionContext();
    ctx.catalog = catalog;
    ctx.path = path;
    return ctx;
  }

  private static final class StubMetadataExtractor implements IMetadataExtractor {
    private final Map<Path, List<MetadataEntry>> metadataByPath = new HashMap<>();
    Set<MediaType> unsupportedTypes = Set.of();

    void put(Path path, List<MetadataEntry> entries) {
      metadataByPath.put(path, entries);
    }

    @Override
    public boolean supports(MediaType type) {
      return !unsupportedTypes.contains(type);
    }

    @Override
    public List<MetadataEntry> extract(Path path) {
      return metadataByPath.getOrDefault(path, List.of());
    }
  }
}
