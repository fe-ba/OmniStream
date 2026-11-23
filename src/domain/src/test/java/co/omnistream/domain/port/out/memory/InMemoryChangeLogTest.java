package co.omnistream.domain.port.out.memory;

import co.omnistream.domain.port.out.IChangeLog;
import co.omnistream.domain.port.out.contract.ChangeLogContract;

class InMemoryChangeLogTest extends ChangeLogContract {

  @Override
  protected IChangeLog createChangeLog() {
    return new InMemoryChangeLog();
  }
}
