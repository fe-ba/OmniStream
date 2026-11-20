# Coding conventions

Rules for the Java code in `src/`. They apply the decisions in
[`docs/decisions/`](../decisions/) to everyday code. When a rule here and the
diagrams disagree, the diagrams win and this file is fixed.

The build enforces part of this: `./mvnw verify` fails if the code is not
formatted, if a module breaks the layer rules or if a test fails.

## 1. Names and packages

- Code is written in **English** (ADR 0006). Types and members take their
  names from the English diagrams in
  [`docs/architecture/software/`](../architecture/software/), and the
  [glossary](glossary.md) maps them to the Spanish originals. Do not invent
  synonyms: if the diagram says `CatalogIndex`, the class is `CatalogIndex`.
- Ports keep the `I` prefix the diagrams use (`ICatalogRepository`,
  `IPlayback`). No other interface uses it.
- Packages follow ADR 0019, for example `co.omnistream.domain.model.catalog`
  or `co.omnistream.domain.port.out`.
- Test classes are named `<Class>Test`. Port contracts are `<Port>Contract`
  and in-memory implementations `InMemory<Port>`, without the `I`
  (`CatalogRepositoryContract`, `InMemoryCatalogRepository`; ADR 0018).

## 2. What is a record and what is a class

| Kind | Java construct | Examples |
|---|---|---|
| Value object, identifier | `record` | `Tag`, `Fingerprint`, `Permission`, `ItemId` |
| Snapshot (ADR 0011) | `record` | `MediaFileSnapshot`, `UserSnapshot` |
| Port DTO and command | `record`, or a `final class` when the diagram shows inheritance | `SearchCriteria`, `StreamProfile`; `LocalCredentials extends Credentials` |
| Entity, aggregate root | `final class` (or `abstract` / `sealed` when the diagram shows inheritance) | `CatalogIndex`, `MediaFile`, `CatalogItem` |
| Domain event | `final class` extending the abstract event from the diagram | `FileIngested extends CatalogEvent` |
| Enum | `enum` | `MediaType`, `Role` |

The accessors that a record generates are fine. "No getters" (section 3) is
about entities.

## 3. Entities and aggregates

- **No getters or setters.** Each public method expresses a rule of the
  domain and is in the diagram (`advance()`, `grant()`, `ingest()`). An
  entity's state leaves the domain only through `toSnapshot()` (ADR 0011).
- **Creation goes through static factories** (`create(...)`, `start(...)`) and
  `fromSnapshot(...)`. Constructors are `private`. The factory validates;
  `fromSnapshot(...)` rebuilds without validating and without recording events.
- **Equality by identity.** `equals` and `hashCode` use only the identifier.
  Value objects (records) compare by value, which records already do.
- **Every change to the catalog goes through `CatalogIndex`**, which records
  the matching event. Nobody outside the aggregate holds a reference to a
  `MediaFile` or a `Collection` to modify it.
- **Aggregates refer to each other by identifier** (`ItemId`, `UserId`), never
  by object.
- **Collections never leave the object mutable.** Fields may hold mutable
  collections; anything returned or put in a snapshot is a copy
  (`List.copyOf`, `Set.copyOf`, `Map.copyOf`).
- Fields are `final` whenever the diagram does not describe a change to them.

```java
public final class PlaybackSession {

  private final SessionId id;
  private final ItemId fileId;
  private final UserId userId;
  private final Duration totalDuration;
  private final Instant startedAt;
  private Duration position;
  private SessionStatus status;

  private PlaybackSession(
      SessionId id,
      ItemId fileId,
      UserId userId,
      Duration position,
      Duration totalDuration,
      SessionStatus status,
      Instant startedAt) {
    this.id = id;
    this.fileId = fileId;
    this.userId = userId;
    this.position = position;
    this.totalDuration = totalDuration;
    this.status = status;
    this.startedAt = startedAt;
  }

  public static PlaybackSession start(
      ItemId fileId, UserId userId, Duration totalDuration, Instant now) {
    Objects.requireNonNull(fileId, "fileId");
    Objects.requireNonNull(userId, "userId");
    if (totalDuration.isNegative()) {
      throw new IllegalArgumentException("totalDuration must not be negative");
    }
    return new PlaybackSession(
        new SessionId(UUID.randomUUID()),
        fileId,
        userId,
        Duration.ZERO,
        totalDuration,
        SessionStatus.ACTIVE,
        now);
  }

  public PlaybackSessionSnapshot toSnapshot() {
    return new PlaybackSessionSnapshot(
        id, fileId, userId, position, totalDuration, status, startedAt);
  }

  // advance(), pause(), resume(), finish(), ... as in the diagram

  @Override
  public boolean equals(Object other) {
    return other instanceof PlaybackSession session && id.equals(session.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
```

## 4. Value objects

- They are immutable `record`s and validate in the compact constructor.
- Identifiers wrap a `UUID` (`record ItemId(UUID value)`). New identifiers are
  created in the factory of their aggregate with `UUID.randomUUID()`.
- `Fingerprint` uses **SHA-256**, with `algorithm = "SHA-256"` and the digest
  in lowercase hexadecimal. The file storage adapter computes it
  (`IFileStorage.computeFingerprint`); the domain only compares fingerprints.

```java
public record Tag(String name, TagSource source) {

  public Tag {
    Objects.requireNonNull(source, "source");
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("A tag needs a name");
    }
    name = name.strip();
  }

  public String normalized() {
    return name.toLowerCase(Locale.ROOT);
  }

  public boolean isAutomatic() {
    return source == TagSource.AUTOMATIC;
  }
}
```

## 5. Errors and null

- **A broken business rule** throws the matching subclass of
  `DomainException` (`AccessDenied`, `CyclicCollection`, `VersionConflict`, ...).
  They are unchecked. The REST API translates each one into an HTTP status.
- **A programming error** (a `null` argument, a negative duration) throws
  `NullPointerException` via `Objects.requireNonNull`, or
  `IllegalArgumentException`. These are bugs, not business outcomes.
- **No `null` crosses a method boundary** in the domain. A value that may be
  missing is returned as `Optional`; `Optional` is never a parameter. Record
  components may be `Optional` only where the diagram says so.
- **Infrastructure never leaks technical exceptions** through a port: an
  `SQLException` or `IOException` is wrapped in an unchecked exception inside
  the adapter. Checked exceptions do not appear in port signatures.

## 6. Time

The domain never reads the clock. `Instant.now()`, `LocalDateTime.now()` and
`System.currentTimeMillis()` do not appear in `omnistream-domain`.

Services receive a `java.time.Clock` in their constructor and pass
`clock.instant()` to the domain methods that take `now`. Tests use
`Clock.fixed(...)`, so every result is reproducible.

## 7. Dependencies, frameworks and comments

- `omnistream-domain` depends only on the JDK. No annotations from frameworks,
  no Lombok, no logging. The build rejects any other dependency (ADR 0013).
- Services receive every port in their constructor. There is no dependency
  injection framework: `CompositionRoot` creates and connects everything.
- A class that implements a GoF pattern says so in its Javadoc, with its role
  (for example: "Concrete handler of the ingestion chain (Chain of
  Responsibility)"). It makes the patterns easy to find when grading and
  reviewing.
- Public types of the ports and the services have a short Javadoc. Inside
  methods, comment only what the code cannot say.

## 8. Tests

- JUnit Jupiter and AssertJ (ADR 0012). Assertions use `assertThat(...)`.
- No mocking library: services are tested with the in-memory implementations
  from `co.omnistream.domain.port.out.memory` (ADR 0018).
- A test checks one behaviour, and its name says which, in camelCase:
  `rejectsADuplicateFile()`, `cannotAddACollectionToItself()`.
- The body is laid out as *given*, *when*, *then*, separated by blank lines.
- Tests live in the same package as the class they test, under
  `src/test/java`.
- A driven port with behaviour has a contract test. The in-memory version
  extends it in the domain module; the real adapter extends it in the
  infrastructure module.
- `ArchitectureTest` (domain module) checks the architecture rules of
  sections 1 to 7 with ArchUnit (ADR 0020). If it fails, the message names the
  rule and why it exists: fix the code, or discuss changing the rule.

```java
public abstract class SessionRepositoryContract {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  protected abstract ISessionRepository createRepository();

  @Test
  void findsASavedSessionById() {
    ISessionRepository repository = createRepository();
    PlaybackSession session =
        PlaybackSession.start(
            new ItemId(UUID.randomUUID()),
            new UserId(UUID.randomUUID()),
            Duration.ofMinutes(90),
            NOW);

    repository.save(session);

    assertThat(repository.findById(session.toSnapshot().id())).contains(session);
  }
}

class InMemorySessionRepositoryTest extends SessionRepositoryContract {

  @Override
  protected ISessionRepository createRepository() {
    return new InMemorySessionRepository();
  }
}
```

## 9. Format and everyday commands

The code follows Google Java Style, applied by google-java-format through
Spotless. Nobody formats by hand. From `src/`:

| Command | What it does |
|---|---|
| `./mvnw spotless:apply` | Formats every Java file |
| `./mvnw verify` | Format check, layer rules, compilation and all tests |
| `./mvnw -pl domain verify` | The same, only for the domain module |
| `./mvnw -pl domain test -Dtest=TagTest` | One test class |

After `./mvnw verify`, each module's coverage report is in
`target/site/jacoco/index.html` (ADR 0020). On GitHub, the summary page of each
*Build* run shows the coverage per module.

In IntelliJ IDEA, the *google-java-format* plugin formats on save with the same
rules. With other editors, run `./mvnw spotless:apply` before committing.
