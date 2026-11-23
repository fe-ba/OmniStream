package co.omnistream.domain.port.out.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InMemoryUnitOfWorkTest {

  private final InMemoryUnitOfWork unitOfWork = new InMemoryUnitOfWork();

  @Test
  void runsTheWorkAndAnswersItsResult() {
    String result = unitOfWork.execute(() -> "done");

    assertThat(result).isEqualTo("done");
    assertThat(unitOfWork.inside()).isFalse();
  }

  @Test
  void joinsANestedWork() {
    assertThat(
            unitOfWork.execute(() -> {
              assertThat(unitOfWork.inside()).isTrue();
              return unitOfWork.execute(() -> unitOfWork.depth());
            }))
        .isEqualTo(2);

    assertThat(unitOfWork.depth()).isZero();
  }

  @Test
  void closesTheWorkWhenItFails() {
    assertThatThrownBy(() ->
            unitOfWork.execute(() -> {
              throw new IllegalStateException("boom");
            }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(unitOfWork.inside()).isFalse();
  }
}
