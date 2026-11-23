package co.omnistream.domain.port.out.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.exception.VersionConflict;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.MediaFile;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.model.catalog.MetadataSource;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.model.catalog.TagSource;
import co.omnistream.domain.port.dto.SearchCriteria;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Behaviour every {@link ICatalogRepository} must share (ADR 0018). */
public abstract class CatalogRepositoryContract {

  protected abstract ICatalogRepository createRepository();

  @Test
  void startsEmpty() {
    CatalogIndex catalog = createRepository().getCatalog();

    assertThat(catalog.toSnapshot().version()).isZero();
    assertThat(catalog.toSnapshot().files()).isEmpty();
    assertThat(catalog.toSnapshot().collections()).isEmpty();
  }

  @Test
  void savesAndFindsFilesByEveryCriterion() {
    ICatalogRepository repository = createRepository();
    CatalogIndex catalog = PortFixtures.emptyCatalog();
    MediaFile video = PortFixtures.video("Trip to Rome.mp4", 'a');
    video.addMetadata(new MetadataEntry("camera", "Pixel 8", MetadataSource.EXTRACTED));
    video.tag(new Tag("Travel", TagSource.MANUAL));
    MediaFile audio = PortFixtures.audio("Podcast.mp3", 'b');
    catalog.ingest(video, PortFixtures.NOW);
    catalog.ingest(audio, PortFixtures.NOW);

    repository.save(catalog);

    MediaFileSnapshot saved = video.toSnapshot();
    assertThat(repository.findFile(saved.id())).contains(saved);
    assertThat(repository.findByFingerprint(saved.fingerprint())).contains(saved);
    assertThat(repository.findByText("rome"))
        .extracting(MediaFileSnapshot::id)
        .containsExactly(saved.id());
    assertThat(repository.findByText("pixel"))
        .extracting(MediaFileSnapshot::id)
        .containsExactly(saved.id());
    assertThat(repository.findByText("travel"))
        .extracting(MediaFileSnapshot::id)
        .containsExactly(saved.id());
    assertThat(repository.findByText(" ")).isEmpty();
    assertThat(
            repository.findByCriteria(
                new SearchCriteria(Optional.empty(), Optional.of(MediaType.VIDEO), Set.of())))
        .extracting(MediaFileSnapshot::id)
        .containsExactly(saved.id());
    assertThat(
            repository.findByCriteria(
                new SearchCriteria(
                    Optional.empty(),
                    Optional.empty(),
                    Set.of(new Tag("travel", TagSource.AUTOMATIC)))))
        .extracting(MediaFileSnapshot::id)
        .containsExactly(saved.id());
    assertThat(
            repository.findByCriteria(
                new SearchCriteria(Optional.empty(), Optional.of(MediaType.DOCUMENT), Set.of())))
        .isEmpty();
  }

  @Test
  void incrementsTheVersionOnEverySave() {
    ICatalogRepository repository = createRepository();

    repository.save(PortFixtures.emptyCatalog());
    CatalogIndex loaded = repository.getCatalog();
    repository.save(loaded);

    assertThat(loaded.toSnapshot().version()).isEqualTo(1);
    assertThat(repository.getCatalog().toSnapshot().version()).isEqualTo(2);
  }

  @Test
  void rejectsACatalogChangedSinceItWasLoaded() {
    ICatalogRepository repository = createRepository();
    repository.save(PortFixtures.emptyCatalog());
    CatalogIndex first = repository.getCatalog();
    CatalogIndex second = repository.getCatalog();
    second.createCollection("Shows", PortFixtures.NOW);
    repository.save(second);

    assertThatThrownBy(() -> repository.save(first)).isInstanceOf(VersionConflict.class);
  }
}
