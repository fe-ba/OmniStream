package co.omnistream.domain.service;

import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.FileRejected;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.exception.VersionConflict;
import co.omnistream.domain.model.access.AccessType;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.policy.TextFilePolicy;
import co.omnistream.domain.port.dto.TextDocument;
import co.omnistream.domain.port.in.ITextEditing;
import co.omnistream.domain.port.out.ICatalogRepository;
import co.omnistream.domain.port.out.IFileStorage;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.port.out.IUserRepository;
import co.omnistream.domain.snapshot.CatalogSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;

/**
 * Domain service that implements the ITextEditing driving port (ADR 0027): it reads a text file for
 * the editor and saves what the editor sends back.
 *
 * <p>A save writes the file through {@link IFileStorage}, then records its new fingerprint and size
 * in the catalog, which raises {@code FileModified}: the live feed (ADR 0026) and the change log
 * see it like any other change. If the catalog cannot be saved, the previous content is written
 * back, so the file and the catalog never disagree.
 *
 * <p>Several people editing the same file all end up with the same text, and each of them saves it.
 * A save whose text is already the stored one changes nothing and succeeds, whatever it is based
 * on; only a different text based on an old fingerprint is a {@link VersionConflict}.
 */
public final class TextEditingService extends AuthorizedService implements ITextEditing {
  private final ICatalogRepository catalogRepository;
  private final IFileStorage storage;
  private final IUnitOfWork unitOfWork;
  private final TextFilePolicy textFilePolicy;

  public TextEditingService(
      IUserRepository userRepository,
      AccessPolicy accessPolicy,
      Clock clock,
      ICatalogRepository catalogRepository,
      IFileStorage storage,
      IUnitOfWork unitOfWork,
      TextFilePolicy textFilePolicy) {
    super(userRepository, accessPolicy, clock);
    this.catalogRepository = Objects.requireNonNull(catalogRepository, "catalogRepository");
    this.storage = Objects.requireNonNull(storage, "storage");
    this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    this.textFilePolicy = Objects.requireNonNull(textFilePolicy, "textFilePolicy");
  }

  /**
   * @throws ResourceNotFound if there is no such active user, or no such file
   * @throws AccessDenied if the user may not see the file
   * @throws FileRejected if the file is not text, is too large or is not UTF-8
   */
  @Override
  public TextDocument open(UserId u, ItemId file) {
    User user = activeUser(u);
    CatalogSnapshot catalog = catalogRepository.getCatalog().toSnapshot();
    require(user, file, AccessType.VIEW, catalog);
    MediaFileSnapshot f = textFile(catalog, file);
    String text = decode(f, read(f));
    boolean editable = accessPolicy.authorizes(user, file, AccessType.MODIFY, catalog);
    return new TextDocument(f.id(), f.name(), text, f.fingerprint(), editable);
  }

  /**
   * @throws ResourceNotFound if there is no such active user, or no such file
   * @throws AccessDenied if the user may not change the file
   * @throws FileRejected if the file is not text, or {@code text} is too large
   * @throws VersionConflict if the file changed since {@code basedOn} and {@code text} is different
   */
  @Override
  public TextDocument save(UserId u, ItemId file, String text, Fingerprint basedOn) {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(basedOn, "basedOn");
    byte[] content = text.getBytes(StandardCharsets.UTF_8);
    if (content.length > TextFilePolicy.MAX_BYTES) {
      throw new FileRejected(
          "A text file can have at most " + TextFilePolicy.MAX_BYTES + " bytes in UTF-8");
    }
    return unitOfWork.execute(() -> write(activeUser(u), file, text, content, basedOn));
  }

  private TextDocument write(
      User user, ItemId file, String text, byte[] content, Fingerprint basedOn) {
    CatalogIndex index = catalogRepository.getCatalog();
    CatalogSnapshot catalog = index.toSnapshot();
    require(user, file, AccessType.MODIFY, catalog);
    MediaFileSnapshot f = textFile(catalog, file);
    byte[] previous = read(f);
    if (previous.length > TextFilePolicy.MAX_BYTES) {
      throw new FileRejected(f.name() + " grew past 1 MiB outside the editor; it cannot be saved");
    }
    if (Arrays.equals(previous, content)) {
      return new TextDocument(f.id(), f.name(), text, f.fingerprint(), true);
    }
    if (!f.fingerprint().matches(basedOn)) {
      throw new VersionConflict(
          "The file " + f.name() + " changed since it was opened; open it again");
    }
    storage.write(f.path(), new ByteArrayInputStream(content));
    try {
      Fingerprint saved = storage.computeFingerprint(f.path());
      index.replaceContent(f.id(), saved, content.length, clock.instant());
      catalogRepository.save(index);
      return new TextDocument(f.id(), f.name(), text, saved, true);
    } catch (RuntimeException failure) {
      try {
        storage.write(f.path(), new ByteArrayInputStream(previous));
      } catch (RuntimeException restoring) {
        failure.addSuppressed(restoring);
      }
      throw failure;
    }
  }

  private User activeUser(UserId u) {
    Objects.requireNonNull(u, "u");
    return userRepository
        .findById(u)
        .filter(user -> user.toSnapshot().active())
        .orElseThrow(() -> new ResourceNotFound("No active user " + u.value()));
  }

  private void require(User user, ItemId file, AccessType access, CatalogSnapshot catalog) {
    Objects.requireNonNull(file, "file");
    if (!accessPolicy.authorizes(user, file, access, catalog)) {
      throw new AccessDenied(
          access == AccessType.VIEW
              ? "You cannot open this file"
              : "You can read this file but not change it");
    }
  }

  private MediaFileSnapshot textFile(CatalogSnapshot catalog, ItemId file) {
    MediaFileSnapshot f =
        catalog.files().stream()
            .filter(candidate -> candidate.id().equals(file))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFound("No file " + file.value()));
    if (!textFilePolicy.isText(f)) {
      throw new FileRejected("Only text files of at most 1 MiB can be edited: " + f.name());
    }
    return f;
  }

  /** The stored bytes, at most one more than the limit, so a file that grew is noticed. */
  private byte[] read(MediaFileSnapshot f) {
    try (InputStream in = storage.read(f.path())) {
      return in.readNBytes(TextFilePolicy.MAX_BYTES + 1);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read " + f.path(), e);
    }
  }

  private String decode(MediaFileSnapshot f, byte[] bytes) {
    return textFilePolicy
        .decode(bytes)
        .orElseThrow(() ->
            new FileRejected(
                f.name() + " is not UTF-8 text of at most 1 MiB, so it cannot be edited"));
  }
}
