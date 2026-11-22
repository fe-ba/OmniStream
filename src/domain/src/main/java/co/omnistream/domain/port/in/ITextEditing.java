package co.omnistream.domain.port.in;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.Fingerprint;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.port.dto.TextDocument;

/**
 * Driving port: implemented by a domain service, called by primary adapters. Opens and saves the
 * text files that several people edit together (ADR 0027). The editing itself happens in the
 * browsers; the server only reads and saves the text.
 */
public interface ITextEditing {

  /**
   * The text of a file. Needs {@code READ}; {@link TextDocument#editable()} says whether the user
   * also has {@code WRITE}.
   */
  TextDocument open(UserId u, ItemId file);

  /**
   * Saves the text of a file. Needs {@code WRITE}.
   *
   * @param basedOn the fingerprint the editor last received, from {@link #open} or from a save
   * @return the file as saved, with its new fingerprint
   */
  TextDocument save(UserId u, ItemId file, String text, Fingerprint basedOn);
}
