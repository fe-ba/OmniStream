package co.omnistream.domain.service;

import static co.omnistream.domain.model.access.AccessFixtures.item;
import static co.omnistream.domain.service.ServiceTestDoubles.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.event.CollectionCreated;
import co.omnistream.domain.event.CollectionModified;
import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.event.EventId;
import co.omnistream.domain.event.FileClassified;
import co.omnistream.domain.event.FileDeleted;
import co.omnistream.domain.event.FileIngested;
import co.omnistream.domain.event.FileModified;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.SyncDelta;
import co.omnistream.domain.service.ServiceTestDoubles.InMemoryChangeLog;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SyncObserverTest {

  private final InMemoryChangeLog changeLog = new InMemoryChangeLog();
  private final SyncObserver observer = new SyncObserver(changeLog);

  @Test
  void onCatalogChangedRegistersCatalogEventsIdempotentlyByEventId() {
    Instant t = NOW.plusSeconds(1);
    ItemId addedFile = item();
    ItemId addedCollection = item();
    ItemId modifiedFile = item();
    ItemId classifiedFile = item();
    ItemId modifiedCollection = item();
    ItemId deletedFile = item();

    FileIngested ingested = new FileIngested(addedFile, t);
    observer.onCatalogChanged(ingested);
    observer.onCatalogChanged(ingested);
    observer.onCatalogChanged(new CollectionCreated(addedCollection, t.plusSeconds(1)));
    observer.onCatalogChanged(new FileModified(modifiedFile, t.plusSeconds(2)));
    observer.onCatalogChanged(new FileClassified(classifiedFile, t.plusSeconds(3)));
    observer.onCatalogChanged(new CollectionModified(modifiedCollection, t.plusSeconds(4)));
    observer.onCatalogChanged(new FileDeleted(deletedFile, t.plusSeconds(5)));

    SyncDelta delta = changeLog.changesSince(NOW);

    assertThat(changeLog.size()).isEqualTo(6);
    assertThat(delta.added()).containsExactly(addedFile, addedCollection);
    assertThat(delta.modified()).containsExactly(modifiedFile, classifiedFile, modifiedCollection);
    assertThat(delta.removed()).containsExactly(deletedFile);
  }

  @Test
  void onCatalogChangedIgnoresNonCatalogDomainEventsAndRejectsNull() {
    DomainEvent other = new DomainEvent(new EventId(UUID.randomUUID()), NOW.plusSeconds(1)) {};

    observer.onCatalogChanged(other);

    assertThat(changeLog.size()).isZero();
    assertThatThrownBy(() -> observer.onCatalogChanged(null))
        .isInstanceOf(NullPointerException.class);
  }
}
