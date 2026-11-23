package co.omnistream.domain.port.out.contract;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.AuditQuery;
import co.omnistream.domain.port.out.IAuditLog;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behaviour every {@link IAuditLog} must share (ADR 0018). */
public abstract class AuditLogContract {

  protected abstract IAuditLog createAuditLog();

  @Test
  void returnsTheNewestEntriesFirst() {
    IAuditLog audit = createAuditLog();
    UserId actor = actor();
    AuditEntry first = AuditEntry.of(actor, AuditAction.LOGGED_IN, PortFixtures.NOW);
    AuditEntry second =
        AuditEntry.of(actor, AuditAction.FILE_ADDED, PortFixtures.NOW.plusSeconds(1));
    audit.append(first);
    audit.append(second);

    assertThat(audit.find(AuditQuery.latest(10))).containsExactly(second, first);
  }

  @Test
  void filtersByActorResourceSubjectAndAction() {
    IAuditLog audit = createAuditLog();
    UserId actor = actor();
    UserId other = actor();
    UserId subject = actor();
    ItemId resource = new ItemId(UUID.randomUUID());
    AuditEntry login = AuditEntry.of(actor, AuditAction.LOGGED_IN, PortFixtures.NOW);
    AuditEntry added =
        AuditEntry.of(actor, AuditAction.FILE_ADDED, PortFixtures.NOW.plusSeconds(1))
            .about(resource);
    AuditEntry granted =
        AuditEntry.of(other, AuditAction.PERMISSION_GRANTED, PortFixtures.NOW.plusSeconds(2))
            .about(resource)
            .concerning(subject);
    audit.append(login);
    audit.append(added);
    audit.append(granted);

    assertThat(audit.find(query(Optional.of(actor), Optional.empty(), Optional.empty(), Set.of())))
        .containsExactly(added, login);
    assertThat(
            audit.find(query(Optional.empty(), Optional.of(resource), Optional.empty(), Set.of())))
        .containsExactly(granted, added);
    assertThat(
            audit.find(query(Optional.empty(), Optional.empty(), Optional.of(subject), Set.of())))
        .containsExactly(granted);
    assertThat(
            audit.find(
                query(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Set.of(AuditAction.FILE_ADDED))))
        .containsExactly(added);
  }

  @Test
  void filtersByTheTimeRange() {
    IAuditLog audit = createAuditLog();
    UserId actor = actor();
    AuditEntry first = AuditEntry.of(actor, AuditAction.LOGGED_IN, PortFixtures.NOW);
    AuditEntry second =
        AuditEntry.of(actor, AuditAction.FILE_ADDED, PortFixtures.NOW.plusSeconds(1));
    audit.append(first);
    audit.append(second);

    assertThat(
            audit.find(
                new AuditQuery(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Set.of(),
                    Optional.of(PortFixtures.NOW.plusSeconds(1)),
                    Optional.empty(),
                    10)))
        .containsExactly(second);
    assertThat(
            audit.find(
                new AuditQuery(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Set.of(),
                    Optional.empty(),
                    Optional.of(PortFixtures.NOW.plusSeconds(1)),
                    10)))
        .containsExactly(first);
  }

  @Test
  void returnsAtMostTheRequestedNumberOfEntries() {
    IAuditLog audit = createAuditLog();
    UserId actor = actor();
    audit.append(AuditEntry.of(actor, AuditAction.LOGGED_IN, PortFixtures.NOW));
    audit.append(AuditEntry.of(actor, AuditAction.FILE_ADDED, PortFixtures.NOW.plusSeconds(1)));

    assertThat(audit.find(AuditQuery.latest(1))).hasSize(1);
    assertThat(audit.find(AuditQuery.latest(1)).getFirst().action())
        .isEqualTo(AuditAction.FILE_ADDED);
  }

  private static AuditQuery query(
      Optional<UserId> actor,
      Optional<ItemId> resource,
      Optional<UserId> subject,
      Set<AuditAction> actions) {
    return new AuditQuery(
        actor, resource, subject, actions, Optional.empty(), Optional.empty(), 10);
  }

  private static UserId actor() {
    return new UserId(UUID.randomUUID());
  }
}
