package co.omnistream.infrastructure.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.port.dto.LocalCredentials;
import co.omnistream.infrastructure.database.Database;
import co.omnistream.infrastructure.database.Schema;
import java.nio.file.Path;
import java.sql.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalIdentityAdapterTest {

  @TempDir Path temporary;

  private Database database;
  private LocalIdentityAdapter adapter;

  @BeforeEach
  void setUp() {
    database = Database.sqlite(temporary.resolve("identity.db"));
    Connection connection = database.connection();
    try {
      Schema.create(connection);
    } finally {
      database.release(connection);
    }
    adapter = new LocalIdentityAdapter(database);
  }

  @AfterEach
  void tearDown() {
    database.close();
  }

  @Test
  void registersAnAccountAndVerifiesItsPassword() {
    LinkedIdentity identity = adapter.register("ana", "secreto");

    assertThat(identity).isEqualTo(new LinkedIdentity(LinkedIdentity.LOCAL_ISSUER, "ana"));
    assertThat(adapter.verify(new LocalCredentials("local", "ana", "secreto"))).contains(identity);
    assertThat(adapter.verify(new LocalCredentials("local", "ana", "otro"))).isEmpty();
    assertThat(adapter.verify(new LocalCredentials("local", "nadie", "secreto"))).isEmpty();
  }

  @Test
  void doesNotStoreThePasswordInClearText() {
    adapter.register("ana", "secreto");

    assertThat(adapter.hash("secreto")).isNotEqualTo("secreto").startsWith("pbkdf2-sha256$");
    assertThat(adapter.hash("secreto")).isNotEqualTo(adapter.hash("secreto"));
  }

  @Test
  void changesThePasswordOfALocalAccount() {
    LinkedIdentity identity = adapter.register("ana", "secreto");

    adapter.changePassword(identity, "nueva");

    assertThat(adapter.verify(new LocalCredentials("local", "ana", "secreto"))).isEmpty();
    assertThat(adapter.verify(new LocalCredentials("local", "ana", "nueva"))).contains(identity);
  }

  @Test
  void refusesToRegisterTheSameUsernameTwice() {
    adapter.register("ana", "secreto");

    assertThatThrownBy(() -> adapter.register("ana", "otra"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void supportsOnlyLocalCredentials() {
    assertThat(adapter.supports(new LocalCredentials("local", "ana", "x"))).isTrue();
    assertThat(adapter.supports(new LocalCredentials("oidc", "ana", "x"))).isFalse();
  }
}
