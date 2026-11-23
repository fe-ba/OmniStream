package co.omnistream.domain.port.out.contract;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.event.CollectionCreated;
import co.omnistream.domain.event.FileDeleted;
import co.omnistream.domain.event.FileModified;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.SyncDelta;
import co.omnistream.domain.port.out.IChangeLog;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behaviour every {@link IChangeLog} must share (ADR 0018). */
public abstract class ChangeLogContract {

  protected abstract IChangeLog createChangeLog();

  @Test
  void groupsTheChangesSinceAnInstantByTheirType() {
    IChangeLog changeLog = createChangeLog();
    ItemId added = new ItemId(UUID.randomUUID());
    ItemId modified = new ItemId(UUID.randomUUID());
    ItemId removed = new ItemId(UUID.randomUUID());
    changeLog.register(new CollectionCreated(added, PortFixtures.NOW.minusSeconds(60)));
    changeLog.register(new FileModified(modified, PortFixtures.NOW));
    changeLog.register(new FileDeleted(removed, PortFixtures.NOW.plusSeconds(60)));

    SyncDelta delta = changeLog.changesSince(PortFixtures.NOW.minusSeconds(60));

    assertThat(delta.added()).containsExactly(added);
    assertThat(delta.modified()).containsExactly(modified);
    assertThat(delta.removed()).containsExactly(removed);
  }

  @Test
  void includesTheInstantOfTheQuery() {
    IChangeLog changeLog = createChangeLog();
    changeLog.register(new FileModified(new ItemId(UUID.randomUUID()), PortFixtures.NOW));

    assertThat(changeLog.hasChangesSince(PortFixtures.NOW)).isTrue();
    assertThat(changeLog.changesSince(PortFixtures.NOW).modified()).hasSize(1);
    assertThat(changeLog.hasChangesSince(PortFixtures.NOW.plusMillis(1))).isFalse();
    assertThat(changeLog.hasChangesSince(PortFixtures.NOW.minusMillis(1))).isTrue();
  }

  @Test
  void registersAnEventOnlyOnce() {
    IChangeLog changeLog = createChangeLog();
    ItemId item = new ItemId(UUID.randomUUID());
    CollectionCreated event = new CollectionCreated(item, PortFixtures.NOW);

    changeLog.register(event);
    changeLog.register(event);

    assertThat(changeLog.changesSince(PortFixtures.NOW.minusSeconds(1)).added())
        .containsExactly(item);
  }

  @Test
  void reportsNoChangesBeforeTheFirstRegisteredEvent() {
    IChangeLog changeLog = createChangeLog();
    changeLog.register(new FileModified(new ItemId(UUID.randomUUID()), PortFixtures.NOW));

    assertThat(changeLog.hasChangesSince(Instant.parse("2026-01-02T00:00:00Z"))).isFalse();
  }
}
