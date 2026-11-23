package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.port.out.IAuditLog;
import co.omnistream.domain.port.out.contract.AuditLogContract;

class InMemoryAuditLogTest extends AuditLogContract {

  @Override
  protected IAuditLog createAuditLog() {
    return new InMemoryAuditLog();
  }
}
