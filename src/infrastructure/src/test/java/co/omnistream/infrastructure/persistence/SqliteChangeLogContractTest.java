package co.omnistream.infrastructure.persistence;

import co.omnistream.domain.port.out.IChangeLog;
import co.omnistream.domain.port.out.contract.ChangeLogContract;
import co.omnistream.infrastructure.database.Database;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class SqliteChangeLogContractTest extends ChangeLogContract {

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
  protected IChangeLog createChangeLog() {
    return adapter;
  }
}
