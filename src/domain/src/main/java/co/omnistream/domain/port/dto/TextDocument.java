package co.omnistream.domain.port.dto;

import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.ItemId;
import java.util.Objects;

/**
 * A text file as the editor sees it (ADR 0027): its text, and the fingerprint of that text, which
 * the editor gives back on {@code ITextEditing.save} to show what its edits are based on.
 *
 * @param file the file
 * @param name its name, for the title of the editor
 * @param text its content
 * @param fingerprint fingerprint of {@code text} as stored
 * @param editable whether the user may save; otherwise the editor is read-only
 */
public record TextDocument(
    ItemId file, String name, String text, Fingerprint fingerprint, boolean editable) {
  public TextDocument {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(fingerprint, "fingerprint");
  }
}
