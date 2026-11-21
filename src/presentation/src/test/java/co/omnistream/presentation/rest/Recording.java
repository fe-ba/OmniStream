package co.omnistream.presentation.rest;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A port that records every call it receives and answers with what the test told it to. A method
 * without an answer returns {@code null}, which is fine for {@code void} methods. Used by the tests
 * of every primary adapter.
 */
public final class Recording<T> {

  /** A call: the method's name and its arguments. */
  public record Call(String method, List<Object> args) {}

  public final List<Call> calls = new ArrayList<>();
  private final Map<String, Function<Object[], Object>> answers = new HashMap<>();
  private final T port;

  private Recording(Class<T> type) {
    this.port =
        type.cast(
            Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, args) -> {
                  if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                      case "equals" -> proxy == args[0];
                      case "hashCode" -> System.identityHashCode(proxy);
                      default -> type.getSimpleName() + " (recording)";
                    };
                  }
                  Object[] given = args == null ? new Object[0] : args;
                  calls.add(new Call(method.getName(), Arrays.asList(given)));
                  Function<Object[], Object> answer = answers.get(method.getName());
                  return answer == null ? null : answer.apply(given);
                }));
  }

  public static <T> Recording<T> of(Class<T> type) {
    return new Recording<>(type);
  }

  /** From now on, {@code method} returns {@code value}. */
  public Recording<T> answer(String method, Object value) {
    return answer(method, args -> value);
  }

  /** From now on, {@code method} returns what {@code answer} computes from its arguments. */
  public Recording<T> answer(String method, Function<Object[], Object> answer) {
    answers.put(method, answer);
    return this;
  }

  public T port() {
    return port;
  }

  /** The only call received, which must be to {@code method}. */
  public Call onlyCall(String method) {
    if (calls.size() != 1 || !calls.get(0).method().equals(method)) {
      throw new AssertionError("Expected one call to " + method + " but got " + calls);
    }
    return calls.get(0);
  }
}
