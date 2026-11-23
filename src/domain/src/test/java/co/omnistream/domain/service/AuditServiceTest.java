package co.omnistream.domain.service;

import static co.omnistream.domain.model.access.AccessFixtures.item;
import static co.omnistream.domain.model.access.AccessFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.dto.AuditQuery;
import co.omnistream.domain.port.out.memory.InMemoryAuditLog;
import co.omnistream.domain.port.out.memory.InMemoryUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuditServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-21T18:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final InMemoryUserRepository users = new InMemoryUserRepository();
  private final InMemoryAuditLog auditLog = new InMemoryAuditLog();

  private AuditService service;
  private UserId adminId;
  private UserId anaId;
  private UserId luisId;

  @BeforeEach
  void setUp() {
    service = new AuditService(users, new AccessPolicy(), CLOCK, auditLog);

    User admin = user("admin", Role.ADMIN);
    User ana = user("ana", Role.USER);
    User luis = user("luis", Role.USER);
    users.save(admin);
    users.save(ana);
    users.save(luis);

    adminId = admin.toSnapshot().id();
    anaId = ana.toSnapshot().id();
    luisId = luis.toSnapshot().id();
  }

  @Test
  void adminCanQueryEntireTrailNewestFirstWithAnyFilters() {
    ItemId fileId = item();
    AuditEntry login = AuditEntry.of(anaId, AuditAction.LOGGED_IN, NOW);
    AuditEntry added =
        AuditEntry.of(adminId, AuditAction.FILE_ADDED, NOW.plusSeconds(10)).about(fileId);
    AuditEntry roleChanged =
        AuditEntry.of(adminId, AuditAction.ROLE_CHANGED, NOW.plusSeconds(20)).concerning(luisId);
    auditLog.append(login);
    auditLog.append(added);
    auditLog.append(roleChanged);

    List<AuditEntry> all = service.history(adminId, AuditQuery.latest(10));
    List<AuditEntry> limited = service.history(adminId, AuditQuery.latest(2));
    List<AuditEntry> byActorAndAction =
        service.history(
            adminId,
            new AuditQuery(
                Optional.of(anaId),
                Optional.empty(),
                Optional.empty(),
                Set.of(AuditAction.LOGGED_IN),
                Optional.empty(),
                Optional.empty(),
                10));

    assertThat(all).containsExactly(roleChanged, added, login);
    assertThat(limited).containsExactly(roleChanged, added);
    assertThat(byActorAndAction).containsExactly(login);
  }

  @Test
  void regularUserAndGuestCanQueryOnlyResourcesTheyCanView() {
    ItemId sharedFile = item();
    ItemId secretFile = item();

    User ana = users.findById(anaId).orElseThrow();
    ana.grant(new Permission(sharedFile, PermissionLevel.READ));
    users.save(ana);

    User guest = User.createGuest(sharedFile, PermissionLevel.READ);
    users.save(guest);

    AuditEntry sharedAdded = AuditEntry.of(adminId, AuditAction.FILE_ADDED, NOW).about(sharedFile);
    AuditEntry sharedEdited =
        AuditEntry.of(anaId, AuditAction.FILE_TAGGED, NOW.plusSeconds(5)).about(sharedFile);
    AuditEntry secretAdded =
        AuditEntry.of(adminId, AuditAction.FILE_ADDED, NOW.plusSeconds(10)).about(secretFile);
    AuditEntry login = AuditEntry.of(anaId, AuditAction.LOGGED_IN, NOW.plusSeconds(15));
    auditLog.append(sharedAdded);
    auditLog.append(sharedEdited);
    auditLog.append(secretAdded);
    auditLog.append(login);

    assertThat(service.history(anaId, AuditQuery.latest(10).about(sharedFile)))
        .containsExactly(sharedEdited, sharedAdded);
    assertThat(service.history(guest.toSnapshot().id(), AuditQuery.latest(10).about(sharedFile)))
        .containsExactly(sharedEdited, sharedAdded);

    assertThatThrownBy(() -> service.history(anaId, AuditQuery.latest(10)))
        .isInstanceOf(AccessDenied.class);
    assertThatThrownBy(() -> service.history(anaId, AuditQuery.latest(10).about(secretFile)))
        .isInstanceOf(AccessDenied.class);
    assertThatThrownBy(() -> service.history(luisId, AuditQuery.latest(10).about(sharedFile)))
        .isInstanceOf(AccessDenied.class);
  }

  @Test
  void historyRejectsDeactivatedAdminUnknownUserAndNullQuery() {
    ItemId fileId = item();
    User admin = users.findById(adminId).orElseThrow();
    admin.deactivate();
    users.save(admin);

    assertThatThrownBy(() -> service.history(adminId, AuditQuery.latest(10)))
        .isInstanceOf(AccessDenied.class);
    assertThatThrownBy(() -> service.history(new UserId(UUID.randomUUID()), AuditQuery.latest(10)))
        .isInstanceOf(ResourceNotFound.class);
    assertThatThrownBy(() -> service.history(anaId, null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.history(anaId, AuditQuery.latest(10).about(fileId)))
        .isInstanceOf(AccessDenied.class);
  }
}
