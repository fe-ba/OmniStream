package co.omnistream.domain.model.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TagTest {

  @Test
  void keepsTheSpellingButCleansTheSpaces() {
    Tag tag = new Tag("  Viaje   a  Cartagena ", TagSource.MANUAL);

    assertThat(tag.name()).isEqualTo("Viaje a Cartagena");
  }

  @Test
  void normalizedIgnoresCaseAndAccents() {
    Tag accented = new Tag("Música", TagSource.MANUAL);
    Tag plain = new Tag("MUSICA", TagSource.AUTOMATIC);

    assertThat(accented.normalized()).isEqualTo("musica");
    assertThat(plain.normalized()).isEqualTo(accented.normalized());
  }

  @Test
  void normalizedKeepsTheLetterEnye() {
    Tag tag = new Tag("Año Nuevo", TagSource.MANUAL);

    assertThat(tag.normalized()).isEqualTo("año nuevo");
  }

  @Test
  void normalizedTreatsComposedAndDecomposedLettersAlike() {
    Tag composed = new Tag("Canción", TagSource.MANUAL);
    Tag decomposed = new Tag("Cancio\u0301n", TagSource.MANUAL);

    assertThat(decomposed.normalized()).isEqualTo(composed.normalized());
  }

  @Test
  void knowsWhetherTheRulesSetIt() {
    assertThat(new Tag("video", TagSource.AUTOMATIC).isAutomatic()).isTrue();
    assertThat(new Tag("favoritos", TagSource.MANUAL).isAutomatic()).isFalse();
  }

  @Test
  void rejectsABlankName() {
    assertThatThrownBy(() -> new Tag("   ", TagSource.MANUAL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
  }

  @Test
  void needsASource() {
    assertThatThrownBy(() -> new Tag("video", null)).isInstanceOf(NullPointerException.class);
  }
}
