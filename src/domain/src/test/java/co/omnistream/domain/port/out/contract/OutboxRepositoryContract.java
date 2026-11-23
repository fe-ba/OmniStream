package co.omnistream.domain.port.out.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.event.DomainEvent;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.IOutboxRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Behaviour every {@link IOutboxRepository} must share (ADR 0018).
 *
 * <p>The events enter the outbox through the catalog repository, as they do in production: saving
 * the catalog writes the events it pulled.
 */
public abstract class OutboxRepositoryContract {

  protected abstract ICatalogRepository createCatalog();

  protected abstract IOutboxRepository createOutbox();

  @Test
  void startsEmpty() {
    assertThat(createOutbox().fetchPending(10)).isEmpty();
  }

  @Test
  void returnsThePendingEventsInTheOrderTheyHappened() {
    seed(PortFixtures.NOW, PortFixtures.NOW.plusSeconds(1), PortFixtures.NOW.plusSeconds(2));

    List<DomainEvent> pending = createOutbox().fetchPending(10);

    assertThat(pending)
        .extracting(DomainEvent::occurredAt)
        .containsExactly(
            PortFixtures.NOW, PortFixtures.NOW.plusSeconds(1), PortFixtures.NOW.plusSeconds(2));
  }

  @Test
  void returnsAtMostTheRequestedNumberOfEvents() {
    seed(PortFixtures.NOW, PortFixtures.NOW.plusSeconds(1), PortFixtures.NOW.plusSeconds(2));

    List<DomainEvent> pending = createOutbox().fetchPending(2);

    assertThat(pending)
        .extracting(DomainEvent::occurredAt)
        .containsExactly(PortFixtures.NOW, PortFixtures.NOW.plusSeconds(1));
  }

  @Test
  void stopsReturningAPublishedEvent() {
    seed(PortFixtures.NOW, PortFixtures.NOW.plusSeconds(1));
    IOutboxRepository outbox = createOutbox();
    List<DomainEvent> pending = outbox.fetchPending(10);

    outbox.markPublished(pending.getFirst().id());

    assertThat(outbox.fetchPending(10)).containsExactly(pending.getLast());
  }

  @Test
  void publishingTwiceDoesNothingTheSecondTime() {
    seed(PortFixtures.NOW);
    IOutboxRepository outbox = createOutbox();
    DomainEvent event = outbox.fetchPending(10).getFirst();

    outbox.markPublished(event.id());
    outbox.markPublished(event.id());

    assertThat(outbox.fetchPending(10)).isEmpty();
  }

  @Test
  void refusesANonPositiveLimit() {
    assertThatThrownBy(() -> createOutbox().fetchPending(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void seed(Instant... instants) {
    CatalogIndex catalog = PortFixtures.emptyCatalog();
    for (int i = 0; i < instants.length; i++) {
      catalog.createCollection("Collection " + i, instants[i]);
    }
    createCatalog().save(catalog);
  }
}
