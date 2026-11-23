package co.omnistream.infrastructure.persistence;

import co.omnistream.domain.port.out.ISessionRepository;
import co.omnistream.domain.port.out.contract.SessionRepositoryContract;
import co.omnistream.infrastructure.database.Database;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class SqliteSessionRepositoryContractTest extends SessionRepositoryContract {

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
  protected ISessionRepository createRepository() {
    return adapter;
  }
}
