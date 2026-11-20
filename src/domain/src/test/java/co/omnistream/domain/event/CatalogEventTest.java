package co.omnistream.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.model.catalog.ItemId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogEventTest {

  private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
  private final ItemId item = new ItemId(UUID.randomUUID());

  @Test
  void eachEventTellsItsKindOfChange() {
    assertThat(new FileIngested(item, NOW).changeType()).isEqualTo(ChangeType.ADDED);
    assertThat(new CollectionCreated(item, NOW).changeType()).isEqualTo(ChangeType.ADDED);
    assertThat(new FileModified(item, NOW).changeType()).isEqualTo(ChangeType.MODIFIED);
    assertThat(new FileClassified(item, NOW).changeType()).isEqualTo(ChangeType.MODIFIED);
    assertThat(new CollectionModified(item, NOW).changeType()).isEqualTo(ChangeType.MODIFIED);
    assertThat(new FileDeleted(item, NOW).changeType()).isEqualTo(ChangeType.REMOVED);
  }

  @Test
  void theTypeIsTheClassName() {
    assertThat(new FileIngested(item, NOW).type()).isEqualTo("FileIngested");
  }

  @Test
  void aNewEventGetsItsOwnIdentifier() {
    FileIngested first = new FileIngested(item, NOW);
    FileIngested second = new FileIngested(item, NOW);

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void anEventReadFromTheOutboxKeepsItsIdentifierAndTime() {
    EventId id = new EventId(UUID.randomUUID());

    FileDeleted event = new FileDeleted(id, NOW, item);

    assertThat(event.id()).isEqualTo(id);
    assertThat(event.occurredAt()).isEqualTo(NOW);
    assertThat(event.item()).isEqualTo(item);
    assertThat(event).isEqualTo(new FileDeleted(id, NOW, item));
  }

  @Test
  void needsAnItem() {
    assertThatThrownBy(() -> new FileIngested(null, NOW)).isInstanceOf(NullPointerException.class);
  }
}
