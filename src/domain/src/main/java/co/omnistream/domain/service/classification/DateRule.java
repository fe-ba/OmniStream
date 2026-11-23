package co.omnistream.domain.service.classification;

import co.omnistream.domain.model.catalog.Classification;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.model.catalog.TagSource;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Concrete classification rule: by capture date (Strategy, ADR 0016, ADR 0032). */
public final class DateRule implements ClassificationRule {

  private static final List<String> DATE_KEYS = List.of("captureDate", "creation_time", "date");
  private static final DateTimeFormatter EXIF_DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT);
  private static final DateTimeFormatter EXIF_DATE =
      DateTimeFormatter.ofPattern("yyyy:MM:dd", Locale.ROOT);

  @Override
  public boolean appliesTo(MediaFileSnapshot a) {
    Objects.requireNonNull(a, "a");
    return extractCaptureDate(a).isPresent();
  }

  @Override
  public Classification classify(MediaFileSnapshot a) {
    Objects.requireNonNull(a, "a");
    return extractCaptureDate(a)
        .map(date -> {
          String year = String.format(Locale.ROOT, "%04d", date.getYear());
          String yearMonth = YearMonth.from(date).toString();
          return new Classification(
              Set.of(new Tag(year, TagSource.AUTOMATIC), new Tag(yearMonth, TagSource.AUTOMATIC)),
              Set.of());
        })
        .orElse(Classification.empty());
  }

  private static Optional<LocalDate> extractCaptureDate(MediaFileSnapshot snapshot) {
    for (String key : DATE_KEYS) {
      Optional<LocalDate> parsed =
          snapshot.metadata().stream()
              .filter(entry -> entry.key().equals(key))
              .map(MetadataEntry::value)
              .map(DateRule::parseDate)
              .flatMap(Optional::stream)
              .findFirst();
      if (parsed.isPresent()) {
        return parsed;
      }
    }
    return Optional.empty();
  }

  private static Optional<LocalDate> parseDate(String raw) {
    String value = raw.strip();
    if (value.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(LocalDate.ofInstant(Instant.parse(value), ZoneOffset.UTC));
    } catch (DateTimeParseException ignored) {
    }
    try {
      return Optional.of(OffsetDateTime.parse(value).toLocalDate());
    } catch (DateTimeParseException ignored) {
    }
    try {
      return Optional.of(LocalDateTime.parse(value).toLocalDate());
    } catch (DateTimeParseException ignored) {
    }
    try {
      return Optional.of(LocalDate.parse(value));
    } catch (DateTimeParseException ignored) {
    }
    try {
      return Optional.of(LocalDateTime.parse(value, EXIF_DATE_TIME).toLocalDate());
    } catch (DateTimeParseException ignored) {
    }
    try {
      return Optional.of(LocalDate.parse(value, EXIF_DATE));
    } catch (DateTimeParseException ignored) {
    }
    if (value.length() >= 10 && value.charAt(4) == '-' && value.charAt(7) == '-') {
      try {
        return Optional.of(LocalDate.parse(value.substring(0, 10)));
      } catch (DateTimeParseException ignored) {
      }
    }
    return Optional.empty();
  }
}
