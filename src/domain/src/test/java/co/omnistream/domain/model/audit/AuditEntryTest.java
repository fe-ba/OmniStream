package co.omnistream.domain.model.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.ItemId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEntryTest {

  private static final Instant NOW = Instant.parse("2026-11-21T16:00:00Z");
  private final UserId ana = new UserId(UUID.randomUUID());
  private final UserId luis = new UserId(UUID.randomUUID());
  private final ItemId file = new ItemId(UUID.randomUUID());

  @Test
  void aNewEntrySaysOnlyWhoDidWhatAndWhen() {
    AuditEntry entry = AuditEntry.of(ana, AuditAction.LOGGED_IN, NOW);

    assertThat(entry.actor()).isEqualTo(ana);
    assertThat(entry.action()).isEqualTo(AuditAction.LOGGED_IN);
    assertThat(entry.at()).isEqualTo(NOW);
    assertThat(entry.resource()).isEmpty();
    assertThat(entry.subject()).isEmpty();
    assertThat(entry.detail()).isEqualTo("");
  }

  @Test
  void addingWhatAppliesKeepsTheSameEntry() {
    AuditEntry entry = AuditEntry.of(ana, AuditAction.PERMISSION_GRANTED, NOW);

    AuditEntry complete = entry.about(file).concerning(luis).saying("WRITE");

    assertThat(complete.id()).isEqualTo(entry.id());
    assertThat(complete.at()).isEqualTo(NOW);
    assertThat(complete.resource()).contains(file);
    assertThat(complete.subject()).contains(luis);
    assertThat(complete.detail()).isEqualTo("WRITE");
  }

  @Test
  void aLongDetailIsCutInsteadOfLosingTheEntry() {
    String value = "x".repeat(AuditEntry.MAX_DETAIL + 50);

    String detail = AuditEntry.of(ana, AuditAction.METADATA_EDITED, NOW).saying(value).detail();

    assertThat(detail.length()).isEqualTo(AuditEntry.MAX_DETAIL);
    assertThat(detail.endsWith("...")).isTrue();
  }

  @Test
  void aDetailThatFitsIsKeptAsItIs() {
    String value = "y".repeat(AuditEntry.MAX_DETAIL);

    assertThat(AuditEntry.of(ana, AuditAction.METADATA_EDITED, NOW).saying(value).detail())
        .isEqualTo(value);
  }

  @Test
  void theConstructorRejectsADetailThatIsTooLong() {
    String value = "z".repeat(AuditEntry.MAX_DETAIL + 1);

    assertThatThrownBy(() ->
            new AuditEntry(
                AuditEntryId.random(),
                NOW,
                ana,
                AuditAction.LOGGED_IN,
                Optional.empty(),
                Optional.empty(),
                value))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void everyPartIsRequired() {
    assertThatThrownBy(() -> AuditEntry.of(null, AuditAction.LOGGED_IN, NOW))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> AuditEntry.of(ana, null, NOW))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> AuditEntry.of(ana, AuditAction.LOGGED_IN, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> AuditEntry.of(ana, AuditAction.LOGGED_IN, NOW).saying(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void identifiersAreRandomAndRequired() {
    assertThat(AuditEntryId.random()).isNotEqualTo(AuditEntryId.random());
    assertThatThrownBy(() -> new AuditEntryId(null)).isInstanceOf(NullPointerException.class);
  }
}
