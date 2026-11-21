package co.omnistream.domain.port.in;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.port.dto.AuditQuery;
import java.util.List;

/** Driving port: implemented by a domain service, called by primary adapters. */
public interface IAuditTrail {
  List<AuditEntry> history(UserId u, AuditQuery q);
}
