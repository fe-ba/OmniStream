package co.omnistream.domain.service;

import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.port.dto.AuditQuery;
import co.omnistream.domain.port.in.IAuditTrail;
import co.omnistream.domain.port.out.IAuditLog;
import co.omnistream.domain.port.out.IUserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Domain service that implements the IAuditTrail driving port: it reads the audit trail. Writing it
 * is the job of the decorators in {@code service.audit} (ADR 0025).
 */
public final class AuditService extends AuthorizedService implements IAuditTrail {
  private final IAuditLog auditLog;

  public AuditService(
      IUserRepository userRepository, AccessPolicy accessPolicy, Clock clock, IAuditLog auditLog) {
    super(userRepository, accessPolicy, clock);
    this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
  }

  /**
   * An administrator may read the whole trail with any filters. Anyone else may only read the
   * history of something they can see: the query must name a resource ({@code AccessDenied}
   * otherwise), and {@code checkAccess(u, resource, VIEW)}. Then {@code auditLog.find(q)}.
   *
   * <p>So a user sees who changed the files and collections they can open, but not who logged in or
   * which roles changed: those entries name no resource.
   */
  @Override
  public List<AuditEntry> history(UserId u, AuditQuery q) {
    Objects.requireNonNull(q, "query");
    User user = loadUser(u);
    if (!user.toSnapshot().active()) {
      throw new AccessDenied("User account is deactivated");
    }
    if (!user.isAdmin()) {
      ItemId resource =
          q.resource()
              .orElseThrow(() ->
                  new AccessDenied(
                      "Only administrators can query audit entries without a resource"));
      checkAccess(u, resource, AccessType.VIEW);
    }
    return auditLog.find(q);
  }
}
