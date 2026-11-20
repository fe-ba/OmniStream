package co.omnistream.domain.model.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentifiersTest {

  @Test
  void itemIdsWithTheSameUuidAreEqual() {
    UUID uuid = UUID.randomUUID();

    assertThat(new ItemId(uuid)).isEqualTo(new ItemId(uuid));
  }

  @Test
  void itemIdNeedsAUuid() {
    assertThatThrownBy(() -> new ItemId(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void catalogIdNeedsAUuid() {
    assertThatThrownBy(() -> new CatalogId(null)).isInstanceOf(NullPointerException.class);
  }
}
