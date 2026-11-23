package co.omnistream.infrastructure.persistence;

import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.contract.CatalogRepositoryContract;
import co.omnistream.infrastructure.database.Database;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class SqliteCatalogRepositoryContractTest extends CatalogRepositoryContract {

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
  protected ICatalogRepository createRepository() {
    return adapter;
  }
}
