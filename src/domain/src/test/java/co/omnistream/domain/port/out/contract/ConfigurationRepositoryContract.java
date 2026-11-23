package co.omnistream.domain.port.out.contract;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.port.out.IConfigurationRepository;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Behaviour every {@link IConfigurationRepository} must share (ADR 0018). */
public abstract class ConfigurationRepositoryContract {

  protected abstract IConfigurationRepository createRepository();

  @Test
  void startsWithTheDefaultLibraryPath() {
    Path expected = Path.of(System.getProperty("user.home"), ".omnistream", "library");

    assertThat(createRepository().getLibraryPath()).isEqualTo(expected);
  }

  @Test
  void savesAndReadsTheLibraryPath() {
    IConfigurationRepository repository = createRepository();
    Path library = Path.of("/media/library");

    repository.saveLibraryPath(library);

    assertThat(repository.getLibraryPath()).isEqualTo(library);
  }

  @Test
  void overwritesTheLibraryPath() {
    IConfigurationRepository repository = createRepository();
    repository.saveLibraryPath(Path.of("/first"));
    repository.saveLibraryPath(Path.of("/second"));

    assertThat(repository.getLibraryPath()).isEqualTo(Path.of("/second"));
  }
}
