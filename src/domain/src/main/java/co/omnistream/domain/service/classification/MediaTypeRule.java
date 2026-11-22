package co.omnistream.domain.service.classification;

import co.omnistream.domain.model.catalog.Classification;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.model.catalog.TagSource;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Concrete classification rule: by media type (Strategy, ADR 0016, ADR 0030). */
public final class MediaTypeRule implements ClassificationRule {

  @Override
  public boolean appliesTo(MediaFileSnapshot a) {
    Objects.requireNonNull(a, "a");
    return true;
  }

  @Override
  public Classification classify(MediaFileSnapshot a) {
    Objects.requireNonNull(a, "a");
    String tagName = a.type().name().toLowerCase(Locale.ROOT);
    return new Classification(Set.of(new Tag(tagName, TagSource.AUTOMATIC)), Set.of());
  }
}
