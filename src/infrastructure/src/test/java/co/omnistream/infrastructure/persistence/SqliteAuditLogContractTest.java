package co.omnistream.infrastructure.persistence;

import co.omnistream.domain.port.out.IAuditLog;
import co.omnistream.domain.port.out.contract.AuditLogContract;
import co.omnistream.infrastructure.database.Database;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class SqliteAuditLogContractTest extends AuditLogContract {

  @TempDir Path temporary;

  private SqlitePersistenceAdapter adapter;

  @BeforeEach
  void openDatabase() {
    adapter = new SqlitePersistenceAdapter(Database.sqlite(temporary.resolve("catalog.db")));
  }

  @AfterEach
  void closeDatabase() {
    adapter.database().close();
  }

  @Override
  protected IAuditLog createAuditLog() {
    return adapter;
  }
}
