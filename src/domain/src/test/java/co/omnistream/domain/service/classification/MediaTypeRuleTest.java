package co.omnistream.domain.service.classification;

import static co.omnistream.domain.model.catalog.CatalogFixtures.NOW;
import static co.omnistream.domain.model.catalog.CatalogFixtures.fingerprint;
import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.catalog.Classification;
import co.omnistream.domain.model.catalog.MediaFile;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.model.catalog.TagSource;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MediaTypeRuleTest {

  private final MediaTypeRule rule = new MediaTypeRule();

  @Test
  void appliesToAllMediaTypesAndTagsLowercaseName() {
    for (MediaType type : MediaType.values()) {
      MediaFileSnapshot file =
          MediaFile.create(
                  "sample",
                  Path.of("/library/sample"),
                  fingerprint('a'),
                  type,
                  100,
                  Duration.ZERO,
                  NOW)
              .toSnapshot();

      assertThat(rule.appliesTo(file)).isTrue();
      Classification result = rule.classify(file);

      assertThat(result.tags())
          .containsExactly(new Tag(type.name().toLowerCase(), TagSource.AUTOMATIC));
      assertThat(result.collections()).isEmpty();
    }
  }
}
