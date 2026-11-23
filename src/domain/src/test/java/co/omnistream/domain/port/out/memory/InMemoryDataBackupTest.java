package co.omnistream.domain.port.out.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InMemoryDataBackupTest {

  @Test
  void answersTheDestinationAndRemembersTheBackup() {
    InMemoryDataBackup backup = new InMemoryDataBackup();
    Path destination = Path.of("/backups/omnistream.dump");

    assertThat(backup.backup(destination)).isEqualTo(destination);
    assertThat(backup.backups()).containsExactly(destination);
  }
}
