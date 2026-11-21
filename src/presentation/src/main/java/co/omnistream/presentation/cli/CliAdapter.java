package co.omnistream.presentation.cli;

import co.omnistream.domain.exception.DomainException;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.IngestionResult;
import co.omnistream.domain.port.dto.ServerStatus;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Primary adapter for the command line. It works on paths of the machine where OmniStream runs, so
 * it is the only way to call {@code ingestFile}; the REST API has no route for it (ADR 0023).
 *
 * <p>Whoever can run commands on that machine already has its files, so the commands run as the
 * system identity, an administrator, like the scheduled tasks.
 *
 * <p>Exit codes: {@value #OK} on success, {@value #FAILED} when the domain refuses the operation,
 * {@value #USAGE} when the command line is wrong.
 */
public final class CliAdapter {

  /** The command worked. */
  public static final int OK = 0;

  /** The domain refused the operation; the message says why. */
  public static final int FAILED = 1;

  /** Unknown command or wrong arguments; the help is printed. */
  public static final int USAGE = 2;

  private final ICatalogManagement catalogManagement;
  private final UserId systemIdentity;
  private final PrintStream out;
  private final PrintStream err;

  public CliAdapter(ICatalogManagement catalogManagement, UserId systemIdentity) {
    this(catalogManagement, systemIdentity, System.out, System.err);
  }

  /** A CLI that writes to the given streams; for tests. */
  CliAdapter(
      ICatalogManagement catalogManagement,
      UserId systemIdentity,
      PrintStream out,
      PrintStream err) {
    this.catalogManagement = Objects.requireNonNull(catalogManagement, "catalogManagement");
    this.systemIdentity = Objects.requireNonNull(systemIdentity, "systemIdentity");
    this.out = Objects.requireNonNull(out, "out");
    this.err = Objects.requireNonNull(err, "err");
  }

  /**
   * Runs one command.
   *
   * @param args the command and its arguments, without the options the bootstrap already read (such
   *     as {@code --config})
   * @return the exit code
   */
  public int execute(List<String> args) {
    if (args.isEmpty() || List.of("help", "--help", "-h").contains(args.get(0))) {
      printHelp();
      return args.isEmpty() ? USAGE : OK;
    }
    String command = args.get(0);
    List<String> rest = args.subList(1, args.size());
    try {
      return switch (command) {
        case "ingest" -> ingest(rest);
        case "reindex" -> reindex(rest);
        case "duplicates" -> duplicates(rest);
        case "backup" -> backup(rest);
        case "library-path" -> libraryPath(rest);
        case "status" -> status(rest);
        default -> usage("Unknown command: " + command);
      };
    } catch (DomainException | IllegalArgumentException | IllegalStateException e) {
      err.println("Error: " + e.getMessage());
      return FAILED;
    }
  }

  // ------------------------------------------------------------------ commands

  private int ingest(List<String> files) {
    if (files.isEmpty()) {
      return usage("ingest needs at least one file");
    }
    int failures = 0;
    for (String file : files) {
      try {
        IngestionResult result = catalogManagement.ingestFile(systemIdentity, path(file));
        out.println(describe(file, result));
      } catch (DomainException | IllegalArgumentException e) {
        err.println(file + ": " + e.getMessage());
        failures++;
      }
    }
    return failures == 0 ? OK : FAILED;
  }

  private static String describe(String file, IngestionResult result) {
    if (result.wasIndexed()) {
      return file + ": indexed as " + result.fileId().value();
    }
    return file
        + ": duplicate of "
        + result.fileId().value()
        + result.action().map(action -> " (" + action + ")").orElse("");
  }

  private int reindex(List<String> args) {
    if (args.size() != 1) {
      return usage("reindex needs the folder to scan");
    }
    int indexed = catalogManagement.reindexLibrary(systemIdentity, path(args.get(0)));
    out.println("Indexed " + indexed + " files");
    return OK;
  }

  private int duplicates(List<String> args) {
    if (!args.isEmpty()) {
      return usage("duplicates takes no arguments");
    }
    List<MediaFileSnapshot> files = catalogManagement.detectDuplicates(systemIdentity);
    if (files.isEmpty()) {
      out.println("No duplicates");
    }
    files.forEach(file -> out.println(file.id().value() + "  " + file.path()));
    return OK;
  }

  private int backup(List<String> args) {
    if (args.size() != 1) {
      return usage("backup needs the destination folder");
    }
    Path copy = catalogManagement.backupCatalog(systemIdentity, path(args.get(0)));
    out.println("Catalog copied to " + copy);
    return OK;
  }

  private int libraryPath(List<String> args) {
    if (args.size() != 1) {
      return usage("library-path needs the new folder");
    }
    catalogManagement.configureLibraryPath(systemIdentity, path(args.get(0)));
    out.println("Library folder set to " + args.get(0));
    return OK;
  }

  private int status(List<String> args) {
    if (!args.isEmpty()) {
      return usage("status takes no arguments");
    }
    ServerStatus status = catalogManagement.monitorServerStatus(systemIdentity);
    out.println("Indexed files:   " + status.indexedFiles());
    out.println("Free space:      " + status.freeSpace() + " bytes");
    out.println("Active sessions: " + status.activeSessions());
    return OK;
  }

  // ------------------------------------------------------------------ helpers

  private static Path path(String value) {
    try {
      return Path.of(value).toAbsolutePath().normalize();
    } catch (InvalidPathException e) {
      throw new IllegalArgumentException("Not a valid path: " + value, e);
    }
  }

  private int usage(String problem) {
    err.println(problem);
    printHelp();
    return USAGE;
  }

  private void printHelp() {
    out.println(
        """
        Usage: omnistream <command> [arguments]

        Commands:
          ingest <file>...        add files of this machine to the catalog
          reindex <folder>        scan a folder and index what is new or changed
          duplicates              list the files that are duplicates of others
          backup <folder>         copy the catalog into a folder
          library-path <folder>   change the folder of the library
          status                  show indexed files, free space and active sessions
          help                    show this help

        Without a command, OmniStream starts the server.\
        """);
  }
}
