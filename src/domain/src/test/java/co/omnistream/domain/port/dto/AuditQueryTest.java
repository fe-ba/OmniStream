package co.omnistream.domain.port.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.model.catalog.ItemId;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditQueryTest {

  private static final Instant NOON = Instant.parse("2026-11-21T12:00:00Z");
  private final UserId ana = new UserId(UUID.randomUUID());
  private final UserId luis = new UserId(UUID.randomUUID());
  private final ItemId file = new ItemId(UUID.randomUUID());

  private final AuditEntry grant =
      AuditEntry.of(ana, AuditAction.PERMISSION_GRANTED, NOON).about(file).concerning(luis);

  private AuditQuery query(
      Optional<UserId> actor,
      Optional<ItemId> resource,
      Optional<UserId> subject,
      Set<AuditAction> actions,
      Optional<Instant> from,
      Optional<Instant> to) {
    return new AuditQuery(actor, resource, subject, actions, from, to, 10);
  }

  @Test
  void withoutFiltersEveryEntryMatches() {
    assertThat(AuditQuery.latest(10).matches(grant)).isTrue();
    assertThat(AuditQuery.latest(10).matches(AuditEntry.of(luis, AuditAction.LOGGED_IN, NOON)))
        .isTrue();
  }

  @Test
  void eachFilterMustMatch() {
    Optional<UserId> none = Optional.empty();
    Optional<ItemId> noItem = Optional.empty();
    Optional<Instant> noTime = Optional.empty();

    assertThat(query(Optional.of(ana), noItem, none, Set.of(), noTime, noTime).matches(grant))
        .isTrue();
    assertThat(query(Optional.of(luis), noItem, none, Set.of(), noTime, noTime).matches(grant))
        .isFalse();
    assertThat(query(none, Optional.of(file), none, Set.of(), noTime, noTime).matches(grant))
        .isTrue();
    assertThat(
            query(none, Optional.of(new ItemId(UUID.randomUUID())), none, Set.of(), noTime, noTime)
                .matches(grant))
        .isFalse();
    assertThat(query(none, noItem, Optional.of(luis), Set.of(), noTime, noTime).matches(grant))
        .isTrue();
    assertThat(query(none, noItem, Optional.of(ana), Set.of(), noTime, noTime).matches(grant))
        .isFalse();
    assertThat(
            query(
                    none,
                    noItem,
                    none,
                    Set.of(AuditAction.PERMISSION_GRANTED, AuditAction.PERMISSION_REVOKED),
                    noTime,
                    noTime)
                .matches(grant))
        .isTrue();
    assertThat(
            query(none, noItem, none, Set.of(AuditAction.LOGGED_IN), noTime, noTime).matches(grant))
        .isFalse();
  }

  @Test
  void anEntryWithoutAResourceNeverMatchesAResourceFilter() {
    AuditEntry login = AuditEntry.of(ana, AuditAction.LOGGED_IN, NOON);

    assertThat(AuditQuery.latest(10).about(file).matches(login)).isFalse();
  }

  @Test
  void fromIsIncludedAndToIsExcluded() {
    Optional<UserId> none = Optional.empty();
    Optional<ItemId> noItem = Optional.empty();

    assertThat(
            query(none, noItem, none, Set.of(), Optional.of(NOON), Optional.empty()).matches(grant))
        .isTrue();
    assertThat(
            query(none, noItem, none, Set.of(), Optional.empty(), Optional.of(NOON)).matches(grant))
        .isFalse();
    assertThat(
            query(
                    none,
                    noItem,
                    none,
                    Set.of(),
                    Optional.of(NOON.minusSeconds(60)),
                    Optional.of(NOON.plusSeconds(60)))
                .matches(grant))
        .isTrue();
  }

  @Test
  void theLimitGoesFromOneToTheMaximum() {
    assertThat(AuditQuery.latest(1).limit()).isEqualTo(1);
    assertThat(AuditQuery.latest(AuditQuery.MAX_LIMIT).limit()).isEqualTo(AuditQuery.MAX_LIMIT);
    assertThatThrownBy(() -> AuditQuery.latest(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AuditQuery.latest(AuditQuery.MAX_LIMIT + 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromMustComeBeforeTo() {
    Optional<UserId> none = Optional.empty();
    Optional<ItemId> noItem = Optional.empty();

    assertThatThrownBy(
            () -> query(none, noItem, none, Set.of(), Optional.of(NOON), Optional.of(NOON)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aboutKeepsTheOtherFilters() {
    AuditQuery query =
        new AuditQuery(
                Optional.of(ana),
                Optional.empty(),
                Optional.empty(),
                Set.of(AuditAction.FILE_TAGGED),
                Optional.empty(),
                Optional.empty(),
                25)
            .about(file);

    assertThat(query.resource()).contains(file);
    assertThat(query.actor()).contains(ana);
    assertThat(query.actions()).containsExactly(AuditAction.FILE_TAGGED);
    assertThat(query.limit()).isEqualTo(25);
  }
}
