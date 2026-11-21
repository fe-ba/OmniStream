package co.omnistream.presentation.rest;

import java.lang.reflect.Proxy;

/** Ports a test does not use: any call to them fails the test. */
final class Unused {

  private Unused() {}

  static <T> T port(Class<T> type) {
    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, args) -> {
              if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                  case "equals" -> proxy == args[0];
                  case "hashCode" -> System.identityHashCode(proxy);
                  default -> type.getSimpleName() + " (unused)";
                };
              }
              throw new AssertionError(type.getSimpleName() + "." + method.getName() + " called");
            }));
  }
}
