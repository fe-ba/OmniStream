package co.omnistream.presentation.cli;

import static org.assertj.core.api.Assertions.assertThat;

import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.FileRejected;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.DuplicateAction;
import co.omnistream.domain.model.catalog.IngestionResult;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.ServerStatus;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.presentation.rest.Recording;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CliAdapterTest {

  private static final UserId SYSTEM = new UserId(UUID.randomUUID());
  private static final ItemId FILE =
      new ItemId(UUID.fromString("00000000-0000-4000-8000-00000000f001"));

  private final Recording<ICatalogManagement> catalog = Recording.of(ICatalogManagement.class);
  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  private final ByteArrayOutputStream err = new ByteArrayOutputStream();

  private int run(String... args) {
    CliAdapter cli =
        new CliAdapter(
            catalog.port(),
            SYSTEM,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
    return cli.execute(List.of(args));
  }

  private String out() {
    return out.toString(StandardCharsets.UTF_8);
  }

  private String err() {
    return err.toString(StandardCharsets.UTF_8);
  }

  @Test
  void helpListsTheCommands() {
    assertThat(run("help")).isEqualTo(CliAdapter.OK);
    assertThat(out()).contains("ingest <file>...", "reindex <folder>", "status");
  }

  @Test
  void anUnknownCommandIsAUsageError() {
    assertThat(run("dance")).isEqualTo(CliAdapter.USAGE);
    assertThat(err()).contains("Unknown command: dance");
    assertThat(catalog.calls).isEmpty();
  }

  @Test
  void ingestAddsEachFileAsTheSystemIdentityWithAnAbsolutePath() {
    catalog.answer(
        "ingestFile",
        args ->
            ((Path) args[1]).endsWith("song.mp3")
                ? new IngestionResult(FILE, false, Optional.empty())
                : new IngestionResult(FILE, true, Optional.of(DuplicateAction.REJECT)));

    assertThat(run("ingest", "music/song.mp3", "music/copy.mp3")).isEqualTo(CliAdapter.OK);

    assertThat(out())
        .contains("music/song.mp3: indexed as " + FILE.value())
        .contains("music/copy.mp3: duplicate of " + FILE.value() + " (REJECT)");
    assertThat(catalog.calls).hasSize(2);
    assertThat(catalog.calls.get(0).args().get(0)).isEqualTo(SYSTEM);
    assertThat((Path) catalog.calls.get(0).args().get(1))
        .isEqualTo(Path.of("music/song.mp3").toAbsolutePath().normalize());
  }

  @Test
  void aRejectedFileDoesNotStopTheOthers() {
    catalog.answer(
        "ingestFile",
        args -> {
          if (((Path) args[1]).endsWith("broken.mp3")) {
            throw new FileRejected("not a media file");
          }
          return new IngestionResult(FILE, false, Optional.empty());
        });

    assertThat(run("ingest", "broken.mp3", "song.mp3")).isEqualTo(CliAdapter.FAILED);

    assertThat(err()).contains("broken.mp3: not a media file");
    assertThat(out()).contains("song.mp3: indexed");
    assertThat(catalog.calls).hasSize(2);
  }

  @Test
  void administrationCommandsCallTheCatalog() {
    catalog
        .answer("reindexLibrary", 7)
        .answer("backupCatalog", Path.of("/backups/catalog.db"))
        .answer("monitorServerStatus", new ServerStatus(7, 1024, 1))
        .answer("detectDuplicates", List.of());

    assertThat(run("reindex", "/library")).isEqualTo(CliAdapter.OK);
    assertThat(run("backup", "/backups")).isEqualTo(CliAdapter.OK);
    assertThat(run("library-path", "/media")).isEqualTo(CliAdapter.OK);
    assertThat(run("status")).isEqualTo(CliAdapter.OK);
    assertThat(run("duplicates")).isEqualTo(CliAdapter.OK);

    assertThat(out())
        .contains("Indexed 7 files")
        .contains("Catalog copied to /backups/catalog.db")
        .contains("Library folder set to /media")
        .contains("Indexed files:   7")
        .contains("Active sessions: 1")
        .contains("No duplicates");
    assertThat(catalog.calls)
        .extracting(Recording.Call::method)
        .containsExactly(
            "reindexLibrary",
            "backupCatalog",
            "configureLibraryPath",
            "monitorServerStatus",
            "detectDuplicates");
    assertThat(catalog.calls.get(0).args()).containsExactly(SYSTEM, Path.of("/library"));
  }

  @Test
  void wrongArgumentsAreAUsageError() {
    assertThat(run("ingest")).isEqualTo(CliAdapter.USAGE);
    assertThat(run("reindex")).isEqualTo(CliAdapter.USAGE);
    assertThat(run("status", "now")).isEqualTo(CliAdapter.USAGE);
    assertThat(catalog.calls).isEmpty();
  }

  @Test
  void aRefusalOfTheDomainIsAFailureWithItsMessage() {
    catalog.answer(
        "monitorServerStatus",
        args -> {
          throw new AccessDenied("not an administrator");
        });

    assertThat(run("status")).isEqualTo(CliAdapter.FAILED);
    assertThat(err()).contains("Error: not an administrator");
  }
}
