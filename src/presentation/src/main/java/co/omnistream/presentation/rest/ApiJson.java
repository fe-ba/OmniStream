package co.omnistream.presentation.rest;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.model.catalog.CatalogId;
import co.omnistream.domain.model.catalog.ItemId;
import co.omnistream.domain.model.playback.SessionId;
import co.omnistream.domain.model.sharing.SharedLinkId;
import co.omnistream.domain.snapshot.CollectionSnapshot;
import co.omnistream.domain.snapshot.MediaFileSnapshot;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import io.javalin.json.JavalinJackson3;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Function;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonAppend;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.VirtualBeanPropertyWriter;
import tools.jackson.databind.util.Annotations;

/**
 * The JSON conventions of the REST API ({@code docs/reference/rest-api.md}), as a Jackson 3 mapper.
 * The snapshots and DTOs of the domain are serialized as they are (ADR 0011); only these types get
 * special treatment:
 *
 * <ul>
 *   <li>identifiers ({@code ItemId}, {@code UserId}...) are the UUID as a string;
 *   <li>{@code Instant} is an ISO-8601 string;
 *   <li>{@code Duration} is a number of seconds with decimals, like {@code
 *       HTMLMediaElement.currentTime};
 *   <li>{@code Path} is the path as a string;
 *   <li>file and collection snapshots start with {@code "kind": "file"} or {@code "kind":
 *       "collection"}, so a client can tell them apart in a mixed list.
 * </ul>
 *
 * <p>Only the components of a record become properties; methods such as {@code Tag.isAutomatic()}
 * do not.
 */
public final class ApiJson {

  private ApiJson() {}

  /** The mapper Javalin uses to read and write bodies. */
  public static JavalinJackson3 forJavalin() {
    return new JavalinJackson3(mapper(), false);
  }

  /** A new mapper with the API's conventions. */
  public static JsonMapper mapper() {
    SimpleModule module = new SimpleModule("omnistream-api");
    identifier(module, ItemId.class, ItemId::value, ItemId::new);
    identifier(module, UserId.class, UserId::value, UserId::new);
    identifier(module, SessionId.class, SessionId::value, SessionId::new);
    identifier(module, SharedLinkId.class, SharedLinkId::value, SharedLinkId::new);
    identifier(module, CatalogId.class, CatalogId::value, CatalogId::new);
    module.addSerializer(Duration.class, new DurationSerializer());
    module.addDeserializer(Duration.class, new DurationDeserializer());
    module.addSerializer(Path.class, new PathSerializer());
    module.addDeserializer(Path.class, new PathDeserializer());
    return JsonMapper.builder()
        .addModule(module)
        .addMixIn(MediaFileSnapshot.class, FileKind.class)
        .addMixIn(CollectionSnapshot.class, CollectionKind.class)
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        // Only record components become properties: helpers such as Tag.isAutomatic() stay out
        .changeDefaultVisibility(visibility -> visibility.withIsGetterVisibility(Visibility.NONE))
        .build();
  }

  private static <T> void identifier(
      SimpleModule module, Class<T> type, Function<T, UUID> toUuid, Function<UUID, T> fromUuid) {
    module.addSerializer(type, new IdentifierSerializer<>(toUuid));
    module.addDeserializer(type, new IdentifierDeserializer<>(type, fromUuid));
  }

  // ------------------------------------------------------------------ identifiers

  private static final class IdentifierSerializer<T> extends ValueSerializer<T> {
    private final Function<T, UUID> toUuid;

    IdentifierSerializer(Function<T, UUID> toUuid) {
      this.toUuid = toUuid;
    }

    @Override
    public void serialize(T value, JsonGenerator gen, SerializationContext ctxt) {
      gen.writeString(toUuid.apply(value).toString());
    }
  }

  private static final class IdentifierDeserializer<T> extends ValueDeserializer<T> {
    private final Class<T> type;
    private final Function<UUID, T> fromUuid;

    IdentifierDeserializer(Class<T> type, Function<UUID, T> fromUuid) {
      this.type = type;
      this.fromUuid = fromUuid;
    }

    @Override
    public Class<?> handledType() {
      return type;
    }

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) {
      if (p.currentToken() != JsonToken.VALUE_STRING) {
        return ctxt.reportInputMismatch(this, "An identifier must be a UUID in a string");
      }
      try {
        return fromUuid.apply(UUID.fromString(p.getString()));
      } catch (IllegalArgumentException e) {
        return ctxt.reportInputMismatch(this, "Not a valid UUID: %s", p.getString());
      }
    }
  }

  // ------------------------------------------------------------------ durations

  private static final class DurationSerializer extends ValueSerializer<Duration> {
    @Override
    public void serialize(Duration value, JsonGenerator gen, SerializationContext ctxt) {
      gen.writeNumber(value.toNanos() / 1_000_000_000.0);
    }
  }

  private static final class DurationDeserializer extends ValueDeserializer<Duration> {
    @Override
    public Class<?> handledType() {
      return Duration.class;
    }

    @Override
    public Duration deserialize(JsonParser p, DeserializationContext ctxt) {
      if (!p.currentToken().isNumeric()) {
        return ctxt.reportInputMismatch(this, "A duration must be a number of seconds");
      }
      BigDecimal seconds = p.getDecimalValue();
      try {
        return Duration.ofNanos(
            seconds.movePointRight(9).setScale(0, RoundingMode.HALF_UP).longValueExact());
      } catch (ArithmeticException e) {
        return ctxt.reportInputMismatch(this, "Duration out of range: %s", seconds);
      }
    }
  }

  // ------------------------------------------------------------------ paths

  private static final class PathSerializer extends ValueSerializer<Path> {
    @Override
    public void serialize(Path value, JsonGenerator gen, SerializationContext ctxt) {
      gen.writeString(value.toString());
    }
  }

  private static final class PathDeserializer extends ValueDeserializer<Path> {
    @Override
    public Class<?> handledType() {
      return Path.class;
    }

    @Override
    public Path deserialize(JsonParser p, DeserializationContext ctxt) {
      if (p.currentToken() != JsonToken.VALUE_STRING) {
        return ctxt.reportInputMismatch(this, "A path must be a string");
      }
      return Path.of(p.getString());
    }
  }

  // ------------------------------------------------------------------ "kind" of catalog items

  @JsonAppend(prepend = true, props = @JsonAppend.Prop(value = KindProperty.class, name = "kind"))
  private abstract static class FileKind {}

  @JsonAppend(prepend = true, props = @JsonAppend.Prop(value = KindProperty.class, name = "kind"))
  private abstract static class CollectionKind {}

  /** Writes {@code "file"} or {@code "collection"}, depending on the snapshot. */
  static final class KindProperty extends VirtualBeanPropertyWriter {

    KindProperty() {}

    private KindProperty(
        BeanPropertyDefinition propDef, Annotations contextAnnotations, JavaType declaredType) {
      super(propDef, contextAnnotations, declaredType);
    }

    @Override
    protected Object value(Object bean, JsonGenerator g, SerializationContext prov) {
      return bean instanceof CollectionSnapshot ? "collection" : "file";
    }

    @Override
    public VirtualBeanPropertyWriter withConfig(
        MapperConfig<?> config,
        AnnotatedClass declaringClass,
        BeanPropertyDefinition propDef,
        JavaType type) {
      return new KindProperty(propDef, declaringClass.getAnnotations(), type);
    }
  }
}
