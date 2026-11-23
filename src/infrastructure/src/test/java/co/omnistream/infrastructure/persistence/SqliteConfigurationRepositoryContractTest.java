package co.omnistream.infrastructure.persistence;

import co.omnistream.domain.port.out.IConfigurationRepository;
import co.omnistream.domain.port.out.contract.ConfigurationRepositoryContract;
import co.omnistream.infrastructure.database.Database;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class SqliteConfigurationRepositoryContractTest extends ConfigurationRepositoryContract {

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
  protected IConfigurationRepository createRepository() {
    return adapter;
  }
}
