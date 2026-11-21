package co.omnistream.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.IndexingStatus;
import co.omnistream.domain.model.catalog.IngestionResult;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.catalog.MediaType;
import co.omnistream.domain.model.catalog.MetadataEntry;
import co.omnistream.domain.model.catalog.MetadataSource;
import co.omnistream.domain.model.catalog.Tag;
import co.omnistream.domain.model.catalog.TagSource;
import co.omnistream.domain.snapshot.CatalogItemSnapshot;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ApiJsonTest {

  private static final UUID FILE_UUID = UUID.fromString("3f2c1d4e-0000-4000-8000-000000000001");
  private static final UUID COLLECTION_UUID =
      UUID.fromString("3f2c1d4e-0000-4000-8000-000000000002");

  private final JsonMapper mapper = ApiJson.mapper();

  private JsonNode toTree(Object value) {
    return mapper.readTree(mapper.writeValueAsString(value));
  }

  private MediaFileSnapshot file() {
    return new MediaFileSnapshot(
        new ItemId(FILE_UUID),
        "concert.mp4",
        Path.of("/library/concert.mp4"),
        new Fingerprint("SHA-256", "ab12"),
        MediaType.VIDEO,
        1024,
        Duration.ofMillis(2_712_500),
        IndexingStatus.INDEXED,
        Instant.parse("2026-11-20T15:04:05Z"),
        List.of(new MetadataEntry("artist", "Ana", MetadataSource.EXTRACTED)),
        Set.of(new Tag("jazz", TagSource.MANUAL)));
  }

  private CollectionSnapshot collection() {
    return new CollectionSnapshot(
        new ItemId(COLLECTION_UUID), "Live", "Concerts", List.of(new ItemId(FILE_UUID)));
  }

  @Test
  void identifiersAreTheUuidAsAString() {
    JsonNode json = toTree(file());

    assertThat(json.get("id").asString()).isEqualTo(FILE_UUID.toString());
  }

  @Test
  void instantsAreIso8601() {
    assertThat(toTree(file()).get("ingestedAt").asString()).isEqualTo("2026-11-20T15:04:05Z");
  }

  @Test
  void durationsAreSecondsWithDecimals() {
    JsonNode duration = toTree(file()).get("duration");

    assertThat(duration.isNumber()).isTrue();
    assertThat(duration.asDouble()).isEqualTo(2712.5);
  }

  @Test
  void pathsArePlainStrings() {
    assertThat(toTree(file()).get("path").asString()).isEqualTo("/library/concert.mp4");
  }

  @Test
  void otherRecordsAreObjectsWithTheirComponents() {
    JsonNode json = toTree(file());

    assertThat(json.get("tags").get(0).get("name").asString()).isEqualTo("jazz");
    assertThat(json.get("tags").get(0).get("source").asString()).isEqualTo("MANUAL");
    assertThat(json.get("tags").get(0).propertyNames()).containsExactlyInAnyOrder("name", "source");
    assertThat(json.get("metadata").get(0).propertyNames())
        .containsExactlyInAnyOrder("key", "value", "source");
    assertThat(json.get("fingerprint").get("algorithm").asString()).isEqualTo("SHA-256");
  }

  @Test
  void filesAndCollectionsSayWhatKindTheyAre() {
    assertThat(toTree(file()).get("kind").asString()).isEqualTo("file");
    assertThat(toTree(collection()).get("kind").asString()).isEqualTo("collection");
    assertThat(toTree(collection()).get("items").get(0).asString()).isEqualTo(FILE_UUID.toString());
  }

  @Test
  void theKindIsAlsoWrittenInsideAMixedList() {
    List<CatalogItemSnapshot> items = List.of(file(), collection());

    JsonNode json = toTree(items);

    assertThat(json.get(0).get("kind").asString()).isEqualTo("file");
    assertThat(json.get(1).get("kind").asString()).isEqualTo("collection");
  }

  @Test
  void anEmptyOptionalIsNull() {
    IngestionResult result = new IngestionResult(new ItemId(FILE_UUID), false, Optional.empty());

    JsonNode json = toTree(result);

    assertThat(json.get("action").isNull()).isTrue();
    assertThat(json.get("duplicate").asBoolean()).isFalse();
  }

  @Test
  void requestBodiesAreReadWithTheSameConventions() {
    Permission permission =
        mapper.readValue(
            "{\"resource\": \"" + FILE_UUID + "\", \"level\": \"WRITE\"}", Permission.class);
    Duration position = mapper.readValue("754.25", Duration.class);
    Path root = mapper.readValue("\"/library/new\"", Path.class);

    assertThat(permission).isEqualTo(new Permission(new ItemId(FILE_UUID), PermissionLevel.WRITE));
    assertThat(position).isEqualTo(Duration.ofMillis(754_250));
    assertThat(root).isEqualTo(Path.of("/library/new"));
  }

  @Test
  void aMalformedIdentifierIsRejected() {
    assertThatThrownBy(() -> mapper.readValue("\"not-a-uuid\"", UserId.class))
        .isInstanceOf(JacksonException.class);
    assertThatThrownBy(() -> mapper.readValue("42", UserId.class))
        .isInstanceOf(JacksonException.class);
  }

  @Test
  void aDurationMustBeANumber() {
    assertThatThrownBy(() -> mapper.readValue("\"PT1M\"", Duration.class))
        .isInstanceOf(JacksonException.class);
  }
}
