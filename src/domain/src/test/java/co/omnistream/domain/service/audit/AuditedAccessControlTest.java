package co.omnistream.domain.service.audit;

import static co.omnistream.domain.service.audit.AuditSupport.CLOCK;
import static co.omnistream.domain.service.audit.AuditSupport.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.InvalidCredentials;
import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.Permission;
import co.omnistream.domain.model.access.PermissionLevel;
import co.omnistream.domain.model.access.Role;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditAction;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.sharing.SharedLinkId;
import co.omnistream.domain.port.dto.LocalCredentials;
import co.omnistream.domain.port.dto.SessionToken;
import co.omnistream.domain.port.in.IAccessControl;
import co.omnistream.domain.service.audit.AuditSupport.FakePort;
import co.omnistream.domain.service.audit.AuditSupport.InMemoryAuditLog;
import co.omnistream.domain.service.audit.AuditSupport.InMemoryUsers;
import co.omnistream.domain.service.audit.AuditSupport.TrackingUnitOfWork;
import co.omnistream.domain.snapshot.SharedLinkSnapshot;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditedAccessControlTest {

  private final TrackingUnitOfWork unitOfWork = new TrackingUnitOfWork();
  private final InMemoryAuditLog auditLog = new InMemoryAuditLog(unitOfWork);
  private final InMemoryUsers users = new InMemoryUsers();
  private final FakePort<IAccessControl> service = new FakePort<>(IAccessControl.class);
  private final IAccessControl audited =
      new AuditedAccessControl(service.port, users, auditLog, unitOfWork, CLOCK);

  private final UserId admin = new UserId(UUID.randomUUID());
  private final UserId luis = new UserId(UUID.randomUUID());
  private final ItemId collection = new ItemId(UUID.randomUUID());

  private User ana() {
    User ana =
        User.create("ana", Role.USER, new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, "ana"));
    users.save(ana);
    return ana;
  }

  private static SessionToken token(UserId user) {
    return new SessionToken("token", user, Role.USER, NOW.plusSeconds(3600));
  }

  @Test
  void aLoginIsRecordedOnTheAccountThatLoggedIn() {
    service.answer("authenticateUser", token(luis));

    SessionToken token = audited.authenticateUser(new LocalCredentials("local", "luis", "secret"));

    assertThat(token.userId()).isEqualTo(luis);
    AuditEntry entry = auditLog.only();
    assertThat(entry.actor()).isEqualTo(luis);
    assertThat(entry.action()).isEqualTo(AuditAction.LOGGED_IN);
    assertThat(entry.at()).isEqualTo(NOW);
    assertThat(entry.detail()).isEqualTo("local");
  }

  @Test
  void aWrongPasswordIsRecordedOnTheAccountItWasTriedOn() {
    UserId ana = ana().toSnapshot().id();
    service.answer("authenticateUser", new InvalidCredentials("Wrong username or password"));

    assertThatThrownBy(
            () -> audited.authenticateUser(new LocalCredentials("local", "ana", "guess")))
        .isInstanceOf(InvalidCredentials.class);

    AuditEntry entry = auditLog.only();
    assertThat(entry.actor()).isEqualTo(ana);
    assertThat(entry.action()).isEqualTo(AuditAction.LOGIN_FAILED);
  }

  @Test
  void anUnknownUsernameIsNotRecorded() {
    service.answer("authenticateUser", new InvalidCredentials("Wrong username or password"));

    assertThatThrownBy(
            () -> audited.authenticateUser(new LocalCredentials("local", "nadie", "guess")))
        .isInstanceOf(InvalidCredentials.class);

    assertThat(auditLog.entries).isEmpty();
  }

  @Test
  void aLogoutIsRecordedOnlyWhenTheTokenBelongedToSomeone() {
    service.answer("validateToken", Optional.of(token(luis)));
    audited.logout("token");

    service.answer("validateToken", Optional.empty());
    audited.logout("stale");

    assertThat(service.calls).containsExactly("validateToken", "logout", "validateToken", "logout");
    AuditEntry entry = auditLog.only();
    assertThat(entry.actor()).isEqualTo(luis);
    assertThat(entry.action()).isEqualTo(AuditAction.LOGGED_OUT);
  }

  @Test
  void openingALinkIsRecordedOnItsGuest() {
    UserId guest = new UserId(UUID.randomUUID());
    service.answer("accessWithLink", token(guest));

    audited.accessWithLink("link-token");

    assertThat(auditLog.only().actor()).isEqualTo(guest);
    assertThat(auditLog.only().action()).isEqualTo(AuditAction.LINK_USED);
  }

  @Test
  void sharingRecordsTheCollectionTheGuestAndTheLevel() {
    UserId guest = new UserId(UUID.randomUUID());
    service.answer(
        "shareCollection",
        new SharedLinkSnapshot(
            new SharedLinkId(UUID.randomUUID()),
            "link-token",
            collection,
            guest,
            PermissionLevel.READ,
            luis,
            NOW.plus(Duration.ofDays(7)),
            false));

    audited.shareCollection(luis, collection, PermissionLevel.READ, Duration.ofDays(7));

    AuditEntry entry = auditLog.only();
    assertThat(entry.action()).isEqualTo(AuditAction.LINK_CREATED);
    assertThat(entry.resource()).contains(collection);
    assertThat(entry.subject()).contains(guest);
    assertThat(entry.detail()).isEqualTo("READ until 2026-11-28T16:00:00Z");
  }

  @Test
  void creatingAUserRecordsItsNameAndRoleButNeverThePassword() {
    service.answer("createUser", luis);

    audited.createUser(admin, "luis", "s3cret-pw", Role.USER);

    AuditEntry entry = auditLog.only();
    assertThat(entry.actor()).isEqualTo(admin);
    assertThat(entry.action()).isEqualTo(AuditAction.USER_CREATED);
    assertThat(entry.subject()).contains(luis);
    assertThat(entry.detail()).isEqualTo("luis as USER");
    assertThat(entry.toString().contains("s3cret-pw")).isFalse();
  }

  @Test
  void rolesPermissionsAndDeactivationsNameTheirSubject() {
    Permission write = new Permission(collection, PermissionLevel.WRITE);

    audited.manageRoles(admin, luis, Role.ADMIN);
    audited.grantPermission(admin, luis, write);
    audited.revokePermission(admin, luis, write);
    audited.deactivateUser(admin, luis);

    List<AuditEntry> entries = auditLog.entries;
    assertThat(entries.stream().map(AuditEntry::action).toList())
        .containsExactly(
            AuditAction.ROLE_CHANGED,
            AuditAction.PERMISSION_GRANTED,
            AuditAction.PERMISSION_REVOKED,
            AuditAction.USER_DEACTIVATED);
    for (AuditEntry entry : entries) {
      assertThat(entry.actor()).isEqualTo(admin);
      assertThat(entry.subject()).contains(luis);
    }
    assertThat(entries.get(0).detail()).isEqualTo("ADMIN");
    assertThat(entries.get(1).resource()).contains(collection);
    assertThat(entries.get(1).detail()).isEqualTo("WRITE");
    assertThat(entries.get(2).resource()).contains(collection);
  }

  @Test
  void aCallThatFailsRecordsNothingAndKeepsItsError() {
    service.answer("grantPermission", new AccessDenied("Ana may not share this"));

    assertThatThrownBy(() ->
            audited.grantPermission(luis, admin, new Permission(collection, PermissionLevel.READ)))
        .isInstanceOf(AccessDenied.class);

    assertThat(auditLog.entries).isEmpty();
  }

  @Test
  void readingRecordsNothing() {
    service
        .answer("validateToken", Optional.empty())
        .answer("authorizeResource", true)
        .answer("listUsers", List.of())
        .answer("findUser", luis)
        .answer("accessTo", List.of());

    audited.validateToken("token");
    audited.authorizeResource(luis, collection, co.omnistream.domain.model.access.AccessType.VIEW);
    audited.listUsers(admin);
    audited.findUser(admin, "luis");
    audited.accessTo(admin, collection);

    assertThat(service.calls)
        .containsExactly("validateToken", "authorizeResource", "listUsers", "findUser", "accessTo");
    assertThat(auditLog.entries).isEmpty();
  }
}
