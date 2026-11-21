package co.omnistream.domain.service.audit;

import co.omnistream.domain.model.access.LinkedIdentity;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.audit.AuditEntry;
import co.omnistream.domain.port.dto.AuditQuery;
import co.omnistream.domain.port.out.IAuditLog;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.port.out.IUserRepository;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** In-memory ports for the audit decorator tests (ADR 0018). */
final class AuditSupport {

  static final Instant NOW = Instant.parse("2026-11-21T16:00:00Z");
  static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private AuditSupport() {}

  /** Keeps the entries in a list, and remembers whether each one was appended in a transaction. */
  static final class InMemoryAuditLog implements IAuditLog {
    final List<AuditEntry> entries = new ArrayList<>();
    private final TrackingUnitOfWork unitOfWork;

    InMemoryAuditLog(TrackingUnitOfWork unitOfWork) {
      this.unitOfWork = unitOfWork;
    }

    @Override
    public void append(AuditEntry e) {
      if (!unitOfWork.inside()) {
        throw new AssertionError("An audit entry was appended outside a unit of work");
      }
      entries.add(e);
    }

    @Override
    public List<AuditEntry> find(AuditQuery q) {
      return entries.stream()
          .filter(q::matches)
          .sorted(Comparator.comparing(AuditEntry::at).reversed())
          .limit(q.limit())
          .toList();
    }

    AuditEntry only() {
      if (entries.size() != 1) {
        throw new AssertionError("Expected one audit entry, found " + entries);
      }
      return entries.get(0);
    }
  }

  /** Runs the work at once, and knows whether it is inside a unit of work. */
  static final class TrackingUnitOfWork implements IUnitOfWork {
    private int depth;

    @Override
    public <T> T execute(Supplier<T> work) {
      depth++;
      try {
        return work.get();
      } finally {
        depth--;
      }
    }

    boolean inside() {
      return depth > 0;
    }
  }

  static final class InMemoryUsers implements IUserRepository {
    private final Map<UserId, User> users = new HashMap<>();

    @Override
    public void save(User u) {
      users.put(u.toSnapshot().id(), u);
    }

    @Override
    public Optional<User> findById(UserId id) {
      return Optional.ofNullable(users.get(id));
    }

    @Override
    public Optional<User> findByUsername(String username) {
      return users.values().stream()
          .filter(user -> user.toSnapshot().username().equals(username))
          .findFirst();
    }

    @Override
    public Optional<User> findByIdentity(LinkedIdentity i) {
      return users.values().stream()
          .filter(user -> user.toSnapshot().identities().contains(i))
          .findFirst();
    }

    @Override
    public List<User> findAll() {
      return List.copyOf(users.values());
    }
  }

  /**
   * A port whose methods record their name in {@code calls} and answer what {@code answers} holds
   * for that name. A method without an answer throws {@code failure} if it is set, or answers
   * nothing (void methods) otherwise.
   */
  static final class FakePort<T> {
    final List<String> calls = new ArrayList<>();
    final Map<String, Object> answers = new HashMap<>();
    RuntimeException failure;
    final T port;

    FakePort(Class<T> type) {
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
                        default -> type.getSimpleName() + " (fake)";
                      };
                    }
                    calls.add(method.getName());
                    if (answers.containsKey(method.getName())) {
                      Object answer = answers.get(method.getName());
                      if (answer instanceof RuntimeException e) {
                        throw e;
                      }
                      return answer;
                    }
                    if (failure != null) {
                      throw failure;
                    }
                    if (method.getReturnType() == void.class) {
                      return null;
                    }
                    throw new AssertionError("No answer for " + method.getName());
                  }));
    }

    FakePort<T> answer(String method, Object value) {
      answers.put(method, value);
      return this;
    }
  }
}
