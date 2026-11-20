package co.omnistream.domain.model.catalog;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * Label of a file, set by a user or by the classification rules (ADR 0016); {@link TagSource} says
 * which.
 *
 * <p>The name keeps the spelling it was given, with surrounding spaces removed and inner spaces
 * collapsed. {@link #normalized()} is the form used to compare tags and to search.
 *
 * @param name the label as shown to the user
 * @param source who set the tag
 */
public record Tag(String name, TagSource source) {

  private static final Pattern SPACES = Pattern.compile("\\s+");
  private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

  public Tag {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(source, "source");
    name = SPACES.matcher(name.strip()).replaceAll(" ");
    if (name.isEmpty()) {
      throw new IllegalArgumentException("A tag needs a name");
    }
  }

  /**
   * The name in lower case and without accents, so that "Música", "musica" and "MUSICA" compare as
   * the same tag. The letter "ñ" is kept, because in Spanish "año" and "ano" are different words.
   */
  public String normalized() {
    String lowerCase = Normalizer.normalize(name, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    StringJoiner result = new StringJoiner("ñ");
    for (String part : lowerCase.split("ñ", -1)) {
      String decomposed = Normalizer.normalize(part, Normalizer.Form.NFD);
      result.add(COMBINING_MARKS.matcher(decomposed).replaceAll(""));
    }
    return result.toString();
  }

  /** Whether the classification rules set this tag. */
  public boolean isAutomatic() {
    return source == TagSource.AUTOMATIC;
  }
}
