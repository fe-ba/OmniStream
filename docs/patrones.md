# Patrones de diseño en OmniStream

Qué patrones del GoF (*Design Patterns*, Gamma, Helm, Johnson y Vlissides)
están en el código, dónde está cada uno y qué papel cumple cada clase. Al final
están los patrones que no usamos a propósito y los patrones de arquitectura que
no son del GoF.

Las rutas se abrevian: `domain/…` es
`src/domain/src/main/java/co/omnistream/domain/…`, y lo mismo para
`infrastructure/…`, `presentation/…` y `bootstrap/…`. Las pruebas están en la
misma ruta bajo `src/test/java`.

## Resumen

| # | Patrón | Tipo | Dónde, en una línea | ADR |
|---|---|---|---|---|
| 1 | [Abstract Factory](#1-abstract-factory) | Creacional | `InfrastructureFactory` y sus dos fábricas, una por perfil | [0010](decisions/0010-desktop-app-is-the-local-server.md), [0013](decisions/0013-one-maven-module-per-layer.md) |
| 2 | [Adapter](#2-adapter) | Estructural | Los adaptadores de la arquitectura hexagonal; `Browser` en el relay del editor | [0013](decisions/0013-one-maven-module-per-layer.md), [0027](decisions/0027-collaborative-text-editing.md) |
| 3 | [Composite](#3-composite) | Estructural | El árbol del catálogo: `CatalogItem`, `MediaFile`, `Collection` | [0019](decisions/0019-package-structure.md) |
| 4 | [Decorator](#4-decorator) | Estructural | La auditoría alrededor de los servicios; las cachés alrededor de los adaptadores | [0025](decisions/0025-audit-trail-with-decorators.md), [0009](decisions/0009-scope-of-version-1-0.md) |
| 5 | [Chain of Responsibility](#5-chain-of-responsibility) | Comportamiento | La ingesta: extracción, duplicados, clasificación, indexado | [0032](decisions/0032-ingestion-pipeline-and-fixed-classification-rules.md) |
| 6 | [Command](#6-command) | Comportamiento | Las tareas programadas: sincronizar, reindexar, respaldar | [0009](decisions/0009-scope-of-version-1-0.md) |
| 7 | [Mediator](#7-mediator) | Comportamiento | Las salas del editor colaborativo | [0027](decisions/0027-collaborative-text-editing.md) |
| 8 | [Memento](#8-memento) | Comportamiento | Las instantáneas (*snapshots*) de los agregados | [0011](decisions/0011-snapshots-to-expose-aggregate-state.md) |
| 9 | [Observer](#9-observer) | Comportamiento | El bus de eventos y sus observadores: cambios en vivo y registro de cambios | [0026](decisions/0026-live-catalog-changes.md) |
| 10 | [Strategy](#10-strategy) | Comportamiento | Cómo se entrega un archivo (directo o transcodificado) y cómo se clasifica | [0016](decisions/0016-automatic-classification.md), [0031](decisions/0031-session-lifecycle-and-playback-resilience.md) |
| 11 | [Template Method](#11-template-method) | Comportamiento | El paso de ingesta; el respaldo de la base de datos | [0032](decisions/0032-ingestion-pipeline-and-fixed-classification-rules.md) |

Los diagramas de clases en
[docs/architecture/software](architecture/software/) marcan cada clase con su
estereotipo (`<<abstract factory>>`, `<<decorator>>`, `<<strategy>>`, `<<memento>>`…).

---

## Creacionales

### 1. Abstract Factory

**Intención:** crear familias de objetos relacionados sin nombrar sus clases
concretas.

OmniStream corre con dos perfiles (ADR 0010): `local`, con SQLite y el disco,
y `server`, con PostgreSQL. Cada perfil es una familia de adaptadores que deben
ir juntos: la unidad de trabajo tiene que ser la misma conexión que usan los
repositorios. La raíz de composición pide los adaptadores a una fábrica
abstracta y nunca sabe cuál le tocó.

| Rol GoF | Clase |
|---|---|
| AbstractFactory | `bootstrap/factory/InfrastructureFactory` (17 métodos `create*()`) |
| ConcreteFactory | `bootstrap/factory/LocalInfrastructureFactory`, `ServerInfrastructureFactory` |
| AbstractProduct | los puertos de salida: `ICatalogRepository`, `IUnitOfWork`, `IFileStorage`, `ITranscoder`… (`domain/port/out/`) |
| ConcreteProduct | `SqlitePersistenceAdapter`, `PostgresPersistenceAdapter`, `LocalFileStorageAdapter`, `TranscodingAdapter`… (`infrastructure/`) |
| Client | `bootstrap/CompositionRoot` |

`CompositionRoot.selectFactory` elige la fábrica según el perfil; los
adaptadores que no cambian entre perfiles los crea `CommonAdapters`. Las pruebas
usan una tercera fábrica concreta, en memoria (`InMemoryFactory`).

Pruebas: `CompositionRootTest`, `MainTest`.

---

## Estructurales

### 2. Adapter

**Intención:** convertir la interfaz de una clase en otra que el cliente espera.

Toda la arquitectura hexagonal es este patrón: el dominio define los puertos y
los adaptadores traducen el mundo exterior a ellos. El caso de libro (adaptador
de objeto) está en el relay del editor:

| Rol GoF | Clase |
|---|---|
| Target | `presentation/rest/EditingRooms.Peer` (lo que las salas saben usar) |
| Adapter | `Browser`, clase interna de `presentation/rest/TextEditingRelay` |
| Adaptee | `io.javalin.websocket.WsConnectContext` (la conexión WebSocket de Javalin) |
| Client | `EditingRooms` |

Gracias a eso, las salas no dependen de Javalin y se prueban con pestañas
falsas (`EditingRoomsTest`).

Los demás adaptadores:

- **Primarios** (llaman al dominio): `RestApiAdapter` (HTTP),
  `TextEditingRelay` (WebSocket), `WebUiAdapter`, `CliAdapter` (consola),
  `SchedulerAdapter` (reloj). En `presentation/`.
- **Secundarios** (el dominio los llama): `JdbcPersistenceAdapter` (JDBC),
  `LocalFileStorageAdapter` (disco), `MetadataAdapter` (ffprobe y ExifTool),
  `TranscodingAdapter` (FFmpeg), `LocalIdentityAdapter`, `EventBusAdapter`,
  `InMemoryTokenAdapter`. En `infrastructure/`.

### 3. Composite

**Intención:** tratar igual a un objeto y a un grupo de objetos organizados en
árbol.

Una colección puede contener archivos y otras colecciones. Contar archivos,
sumar tamaños o saber si algo está dentro se pide igual a un archivo que a una
colección entera.

| Rol GoF | Clase |
|---|---|
| Component | `domain/model/catalog/CatalogItem` (`totalSize()`, `countFiles()`, `contains()`, `collectFiles()`) |
| Leaf | `domain/model/catalog/MediaFile` |
| Composite | `domain/model/catalog/Collection` (guarda una lista de `CatalogItem` y recorre el árbol) |
| Client | `CatalogIndex` y, a través de él, los servicios |

`Collection.add` impide los ciclos (una colección dentro de sí misma, directa o
indirectamente). El permiso sobre una colección se hereda hacia todo lo que
contiene (ADR 0024); `AccessPolicy` recorre el mismo árbol sobre las
instantáneas de las colecciones.

Pruebas: `CollectionTest`, `CatalogIndexTest`, `AccessPolicyTest`.

### 4. Decorator

**Intención:** agregar responsabilidades a un objeto envolviéndolo, sin
cambiar su clase.

Lo usamos en dos lugares, con el mismo principio: el envoltorio implementa la
misma interfaz que el objeto que envuelve.

**Auditoría** (ADR 0025): cada cambio queda registrado en el historial sin que
los servicios sepan que existe.

| Rol GoF | Clase |
|---|---|
| Component | los puertos de entrada: `IAccessControl`, `ICatalogManagement`, `ISearch`, `ISync`, `ITextEditing` |
| ConcreteComponent | `AccessService`, `CatalogService`, `SearchService`, `SyncService`, `TextEditingService` |
| Decorator | `domain/service/audit/AuditingDecorator` (abstracta: registra en la misma transacción) |
| ConcreteDecorator | `AuditedAccessControl`, `AuditedCatalogManagement`, `AuditedSearch`, `AuditedSync`, `AuditedTextEditing` |

**Cachés** (ADR 0009), activas con `cache.enabled` (por defecto, sí):

| Rol GoF | Clase |
|---|---|
| Component | `ICatalogRepository`, `ITranscoder` |
| ConcreteComponent | `JdbcPersistenceAdapter`, `TranscodingAdapter` |
| ConcreteDecorator | `infrastructure/persistence/CachedCatalogRepository` (el catálogo en memoria), `infrastructure/media/CachedTranscoder` (lo transcodificado en disco) |

`CompositionRoot` arma las dos capas: envuelve los adaptadores con las cachés
(`cached(…)`) y los servicios con la auditoría.

Pruebas: `AuditedAccessControlTest`, `AuditedCatalogManagementTest`,
`AuditedSearchAndSyncTest`, `AuditedTextEditingTest`,
`CachedCatalogRepositoryTest`, `CachedTranscoderTest`.

---

## De comportamiento

### 5. Chain of Responsibility

**Intención:** pasar una petición por una cadena de manejadores; cada uno hace
su parte y decide si sigue.

Un archivo nuevo pasa por cuatro pasos. Un paso puede cortar la cadena: si el
archivo es un duplicado, no se clasifica ni se indexa.

| Rol GoF | Clase |
|---|---|
| Handler | `domain/service/ingestion/IngestionStep` (`chain()`, `execute()`) |
| ConcreteHandler | `ExtractionStep` → `DuplicatesStep` → `ClassificationStep` → `IndexingStep` |
| Request | `IngestionContext` (el archivo y lo que cada paso va dejando) |
| Client | `CatalogService` |

La cadena se arma una sola vez, en `CompositionRoot.buildIngestionChain`.

Pruebas: `IngestionChainTest`, `IngestionStepTest`.

### 6. Command

**Intención:** encapsular una acción como un objeto, para ejecutarla después
o repetidamente sin que quien la dispara sepa qué hace.

El planificador corre tareas cada cierto tiempo sin saber qué hacen.

| Rol GoF | Clase |
|---|---|
| Command | `presentation/scheduler/ScheduledTask` (`execute(UserId)`) |
| ConcreteCommand | `SyncTask` (cada 2 s), `ReindexTask` y `BackupTask` (a diario) |
| Invoker | `presentation/scheduler/SchedulerAdapter` |
| Receiver | los puertos `IEventDispatch` y `ICatalogManagement` |
| Client | `CompositionRoot.createScheduler` |

Pruebas: `ScheduledTasksTest`, `SchedulerAdapterTest`.

### 7. Mediator

**Intención:** que un grupo de objetos se comunique a través de un mediador
en lugar de referirse unos a otros.

En el editor colaborativo, los navegadores nunca se hablan directamente. La
sala decide:
- quién carga el texto inicial;
- a quién reenvía cada edición;
- quién compacta el historial;
- cómo se guarda.

| Rol GoF | Clase |
|---|---|
| Mediator | `Room`, clase interna de `presentation/rest/EditingRooms` |
| Colleague | `EditingRooms.Peer` (cada pestaña del navegador, vía `Browser`) |

Nota: en la criba de la presentación del curso, Mediator sale con acoplamiento
alto y sin cumplir SRP ni OCP. Aquí el acoplamiento queda dentro de la sala, a
cambio de que los navegadores no se conozcan entre sí.

Pruebas: `EditingRoomsTest`.

### 8. Memento

**Intención:** capturar el estado interno de un objeto sin romper su
encapsulamiento, para restaurarlo después.

Los agregados no exponen sus campos. Para guardarse o mostrarse entregan una
instantánea inmutable, y se reconstruyen a partir de ella (ADR 0011).

| Rol GoF | Clase |
|---|---|
| Originator | `CatalogIndex`, `User`, `SharedLink`, `PlaybackSession` (`toSnapshot()` y `fromSnapshot()`) |
| Memento | los *records* de `domain/snapshot/`: `CatalogSnapshot`, `MediaFileSnapshot`, `CollectionSnapshot`, `UserSnapshot`, `SharedLinkSnapshot`, `PlaybackSessionSnapshot` |
| Caretaker | los repositorios: `JdbcPersistenceAdapter` con sus *mappers*, y `CachedCatalogRepository`, que guarda la instantánea y entrega a cada lector un catálogo propio |

Pruebas: `CatalogIndexTest`, `UserTest`, `SharedLinkTest`,
`PlaybackSessionTest`.

### 9. Observer

**Intención:** que varios objetos se enteren de los cambios de otro sin que
este los conozca.

Cada cambio del catálogo se publica como evento, y quien se suscribe se
entera.

| Rol GoF | Clase |
|---|---|
| Subject | `IEventBus` (`IEventPublisher` + `IEventSubscriber`), en `domain/port/out/` |
| ConcreteSubject | `infrastructure/event/EventBusAdapter` |
| Observer | `domain/event/ICatalogObserver` (`onCatalogChanged`) |
| ConcreteObserver | `domain/service/SyncObserver` (registro de cambios para sincronizar), `Follower` dentro de `CatalogFeedService` (un usuario que sigue los cambios en vivo) |

Los eventos llegan al bus desde el outbox: `SyncTask` los publica cada dos
segundos. A su vez, `CatalogFeedService` notifica a un `CatalogChangeListener`,
que el adaptador REST conecta a un canal SSE del navegador (ADR 0026). Cada
seguidor solo recibe los cambios de lo que puede ver.

Pruebas: `CatalogFeedServiceTest`, `SyncObserverTest`, `EventBusAdapterTest`,
`RestApiFeedTest`.

### 10. Strategy

**Intención:** definir una familia de algoritmos intercambiables y elegir uno
en tiempo de ejecución.

**Entrega de un archivo:** reproducir un archivo tal cual o convertirlo para el
dispositivo son dos algoritmos con la misma interfaz.

| Rol GoF | Clase |
|---|---|
| Strategy | `domain/service/stream/StreamStrategy` (`supports()`, `generateStream()`) |
| ConcreteStrategy | `DirectStream`, `TranscodedStream` |
| Context | `PlaybackService`, que usa la primera estrategia que soporta el archivo y el perfil |

**Clasificación:** cada regla etiqueta los archivos según un criterio.

| Rol GoF | Clase |
|---|---|
| Strategy | `domain/service/classification/ClassificationRule` (`appliesTo()`, `classify()`) |
| ConcreteStrategy | `MediaTypeRule`, `DateRule` (y `UserDefinedRules`, diseñada para la versión 2.0) |
| Context | `ClassificationStep` |

Pruebas: `StreamStrategiesTest`, `PlaybackServiceTest`, `MediaTypeRuleTest`,
`DateRuleTest`.

### 11. Template Method

**Intención:** fijar el esqueleto de un algoritmo en una clase base y dejar
que las subclases rellenen algunos pasos.

| Dónde | Método plantilla | Paso que rellenan las subclases |
|---|---|---|
| `domain/service/ingestion/IngestionStep` | `execute()`: procesa y, si debe seguir, pasa al siguiente | `process()`, en cada paso de la ingesta |
| `infrastructure/persistence/JdbcPersistenceAdapter` | `backup()`: decide el destino y crea la carpeta | `writeDump()`: `VACUUM INTO` en `SqlitePersistenceAdapter`, `pg_dump` en `PostgresPersistenceAdapter` |

En la ingesta, este patrón y Chain of Responsibility van juntos: el método
plantilla es el que hace avanzar la cadena.

Pruebas: `IngestionChainTest`, `SqlitePersistenceAdapterTest`.

---

## Lo que no usamos, a propósito

- **Singleton.** No hay ninguno. La raíz de composición crea una sola instancia
  de cada servicio y adaptador y la entrega por constructor. Así cada pieza se
  puede probar con dobles y no hay estado global. En la criba del curso es el
  patrón peor evaluado: acoplamiento alto, cohesión baja y ningún principio
  SOLID.
- **Factory Method** como patrón aparte. Los `create*()` de
  `InfrastructureFactory` son métodos fábrica, pero así es como el GoF
  implementa Abstract Factory; no lo contamos dos veces.
- **Proxy.** Solo aparece en los dobles de prueba (`java.lang.reflect.Proxy`).
  Un repositorio con caché delante podría verse como un proxy, pero lo
  diseñamos y documentamos como decorador (ADR 0009).

## Otros patrones (no del GoF)

| Patrón | Dónde | ADR |
|---|---|---|
| Arquitectura hexagonal (puertos y adaptadores) | `domain/port/in`, `domain/port/out`, y los módulos `infrastructure` y `presentation`; `ArchitectureTest` hace cumplir las dependencias | [0013](decisions/0013-one-maven-module-per-layer.md), [0019](decisions/0019-package-structure.md), [0020](decisions/0020-architecture-tests-and-coverage.md) |
| Raíz de composición (inyección por constructor) | `bootstrap/CompositionRoot` | [0013](decisions/0013-one-maven-module-per-layer.md) |
| Agregado (DDD) | `CatalogIndex`, `User`, `SharedLink`, `PlaybackSession` | [0011](decisions/0011-snapshots-to-expose-aggregate-state.md) |
| Repositorio | `ICatalogRepository`, `IUserRepository`… y su implementación JDBC | [0015](decisions/0015-plain-jdbc-for-persistence.md) |
| Unidad de trabajo | `IUnitOfWork`; `infrastructure/database/Database` guarda la transacción por hilo y las llamadas anidadas se unen a ella | [0030](decisions/0030-service-transactions-and-access-enforcement.md) |
| Outbox transaccional | `JdbcPersistenceAdapter.save` guarda el catálogo y sus eventos en la misma transacción; `EventDispatchService` los publica después | [0026](decisions/0026-live-catalog-changes.md) |
| Data Mapper | `infrastructure/persistence/mapper/` (`UserMapper`, `CatalogIndexMapper`…) | [0015](decisions/0015-plain-jdbc-for-persistence.md) |
| Bloqueo optimista | la versión del catálogo; un cambio sobre una versión vieja es `VersionConflict` | — |
| Circuit Breaker | `infrastructure/identity/ResilientIdentityProvider` (también decorador). Preparado para el proveedor OIDC de la versión 2.0, por eso todavía no se conecta | [0009](decisions/0009-scope-of-version-1-0.md) |
| CRDT (edición colaborativa) | Yjs en el navegador (`web/app.js`); el servidor solo retransmite | [0027](decisions/0027-collaborative-text-editing.md) |
