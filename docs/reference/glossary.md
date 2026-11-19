# Glossary

Spanish to English names used in the OmniStream design. The Spanish names are
the ones in the original diagrams (the course deliverable); the English names
are the ones used in the code and in the English diagrams.

The Spanish originals are in `docs/architecture/software/es/` and the English
diagrams in `docs/architecture/software/`. Each Spanish identifier always maps
to the same English identifier, except the few context-dependent terms listed
below.

## Types

| Spanish | English | Defined in |
|---|---|---|
| `PoliticaAcceso` | `AccessPolicy` | Domain model |
| `ServicioAcceso` | `AccessService` | Services |
| `TipoAcceso` | `AccessType` | Domain model |
| `ConfiguracionApp` | `AppConfiguration` | Ports and adapters |
| `ServicioAutorizado` | `AuthorizedService` | Services |
| `TareaRespaldar` | `BackupTask` | Ports and adapters |
| `RepositorioCatalogoConCache` | `CachedCatalogRepository` | Ports and adapters |
| `TranscodificadorConCache` | `CachedTranscoder` | Ports and adapters |
| `EventoCatalogo` | `CatalogEvent` | Domain model |
| `CatalogoId` | `CatalogId` | Domain model |
| `CatalogoIndice` | `CatalogIndex` | Domain model |
| `MapeadorCatalogoIndice` | `CatalogIndexMapper` | Ports and adapters |
| `ElementoCatalogo` | `CatalogItem` | Domain model |
| `ServicioCatalogo` | `CatalogService` | Services |
| `TipoCambio` | `ChangeType` | Domain model |
| `EstadoCircuito` | `CircuitState` | Ports and adapters |
| `AdaptadorCLI` | `CliAdapter` | Ports and adapters |
| `Coleccion` | `Collection` | Domain model |
| `ComandoColeccion` | `CollectionCommand` | Services |
| `ColeccionCreada` | `CollectionCreated` | Domain model |
| `ColeccionModificada` | `CollectionModified` | Domain model |
| `ComposicionRaiz` | `CompositionRoot` | Ports and adapters |
| `Credenciales` | `Credentials` | Services |
| `PoliticaDeduplicacion` | `DeduplicationPolicy` | Domain model |
| `AdaptadorDesktop` | `DesktopAdapter` | Ports and adapters |
| `StreamDirecto` | `DirectStream` | Services |
| `EventoDominio` | `DomainEvent` | Domain model |
| `AccionDuplicado` | `DuplicateAction` | Domain model |
| `PasoDuplicados` | `DuplicatesStep` | Services |
| `AdaptadorBusEventos` | `EventBusAdapter` | Ports and adapters |
| `ServicioDespachoEventos` | `EventDispatchService` | Services |
| `EventoId` | `EventId` | Domain model |
| `MapeadorEvento` | `EventMapper` | Ports and adapters |
| `PasoExtraccion` | `ExtractionStep` | Services |
| `ArchivoEliminado` | `FileDeleted` | Domain model |
| `ArchivoIngestado` | `FileIngested` | Domain model |
| `ArchivoModificado` | `FileModified` | Domain model |
| `HuellaDigital` | `Fingerprint` | Domain model |
| `SolicitudHttp` | `HttpRequest` | Ports and adapters |
| `RespuestaHttp` | `HttpResponse` | Ports and adapters |
| `IControlAcceso` | `IAccessControl` | Ports and adapters |
| `IGestionCatalogo` | `ICatalogManagement` | Ports and adapters |
| `IObservadorCatalogo` | `ICatalogObserver` | Services |
| `IRepositorioCatalogo` | `ICatalogRepository` | Ports and adapters |
| `IRegistroCambios` | `IChangeLog` | Ports and adapters |
| `IRegistroCredenciales` | `ICredentialRegistry` | Ports and adapters |
| `IBusEventos` | `IEventBus` | Ports and adapters |
| `IDespachoEventos` | `IEventDispatch` | Ports and adapters |
| `IPublicadorEventos` | `IEventPublisher` | Ports and adapters |
| `ISuscriptorEventos` | `IEventSubscriber` | Ports and adapters |
| `IAlmacenamientoArchivos` | `IFileStorage` | Ports and adapters |
| `IProveedorIdentidad` | `IIdentityProvider` | Ports and adapters |
| `IExtractorMetadatos` | `IMetadataExtractor` | Ports and adapters |
| `EstadoIndexacion` | `IndexingStatus` | Domain model |
| `PasoIndexacion` | `IndexingStep` | Services |
| `FabricaInfraestructura` | `InfrastructureFactory` | Ports and adapters |
| `ContextoIngesta` | `IngestionContext` | Services |
| `ResultadoIngesta` | `IngestionResult` | Domain model |
| `PasoIngesta` | `IngestionStep` | Services |
| `AdaptadorTokensEnMemoria` | `InMemoryTokenAdapter` | Ports and adapters |
| `IRepositorioOutbox` | `IOutboxRepository` | Ports and adapters |
| `IReproduccion` | `IPlayback` | Ports and adapters |
| `IBusqueda` | `ISearch` | Ports and adapters |
| `IRepositorioSesiones` | `ISessionRepository` | Ports and adapters |
| `IRepositorioEnlaces` | `ISharedLinkRepository` | Ports and adapters |
| `ISincronizacion` | `ISync` | Ports and adapters |
| `ElementoId` | `ItemId` | Domain model |
| `IRepositorioTokens` | `ITokenRepository` | Ports and adapters |
| `ITranscodificador` | `ITranscoder` | Ports and adapters |
| `IUnidadDeTrabajo` | `IUnitOfWork` | Ports and adapters |
| `IRepositorioUsuarios` | `IUserRepository` | Ports and adapters |
| `AdaptadorPersistenciaJdbc` | `JdbcPersistenceAdapter` | Ports and adapters |
| `IdentidadVinculada` | `LinkedIdentity` | Domain model |
| `CredencialesLocales` | `LocalCredentials` | Services |
| `FabricaLocal` | `LocalFactory` | Ports and adapters |
| `AdaptadorArchivosLocal` | `LocalFileStorageAdapter` | Ports and adapters |
| `AdaptadorIdentidadLocal` | `LocalIdentityAdapter` | Ports and adapters |
| `ArchivoMultimedia` | `MediaFile` | Domain model |
| `MapeadorArchivo` | `MediaFileMapper` | Ports and adapters |
| `FlujoMultimedia` | `MediaStream` | Services |
| `TipoMedio` | `MediaType` | Domain model |
| `AdaptadorMetadatos` | `MetadataAdapter` | Ports and adapters |
| `Metadato` | `MetadataEntry` | Domain model |
| `OrigenMetadato` | `MetadataSource` | Domain model |
| `AdaptadorMobile` | `MobileAdapter` | Ports and adapters |
| `AdaptadorArchivosRed` | `NetworkFileStorageAdapter` | Ports and adapters |
| `PaqueteOffline` | `OfflinePackage` | Services |
| `CredencialesOIDC` | `OidcCredentials` | Services |
| `AdaptadorIdentidadOIDC` | `OidcIdentityAdapter` | Ports and adapters |
| `Permiso` | `Permission` | Domain model |
| `NivelPermiso` | `PermissionLevel` | Domain model |
| `ServicioReproduccion` | `PlaybackService` | Services |
| `SesionReproduccion` | `PlaybackSession` | Domain model |
| `AdaptadorPersistenciaPostgreSQL` | `PostgreSqlPersistenceAdapter` | Ports and adapters |
| `TareaReindexar` | `ReindexTask` | Ports and adapters |
| `ProveedorIdentidadResiliente` | `ResilientIdentityProvider` | Ports and adapters |
| `AdaptadorApiRest` | `RestApiAdapter` | Ports and adapters |
| `Rol` | `Role` | Domain model |
| `TareaProgramada` | `ScheduledTask` | Ports and adapters |
| `AdaptadorProgramador` | `SchedulerAdapter` | Ports and adapters |
| `CriterioBusqueda` | `SearchCriteria` | Services |
| `ServicioBusqueda` | `SearchService` | Services |
| `FabricaServidor` | `ServerFactory` | Ports and adapters |
| `EstadoServidor` | `ServerStatus` | Services |
| `SesionId` | `SessionId` | Domain model |
| `MapeadorSesion` | `SessionMapper` | Ports and adapters |
| `EstadoSesion` | `SessionStatus` | Domain model |
| `TokenSesion` | `SessionToken` | Services |
| `EnlaceCompartido` | `SharedLink` | Domain model |
| `EnlaceId` | `SharedLinkId` | Domain model |
| `MapeadorEnlace` | `SharedLinkMapper` | Ports and adapters |
| `AdaptadorPersistenciaSQLite` | `SqlitePersistenceAdapter` | Ports and adapters |
| `PerfilStream` | `StreamProfile` | Services |
| `EstrategiaStream` | `StreamStrategy` | Services |
| `DeltaSincronizacion` | `SyncDelta` | Services |
| `ObservadorSincronizacion` | `SyncObserver` | Services |
| `ServicioSincronizacion` | `SyncService` | Services |
| `TareaSincronizar` | `SyncTask` | Ports and adapters |
| `Etiqueta` | `Tag` | Domain model |
| `StreamTranscodificado` | `TranscodedStream` | Services |
| `AdaptadorTranscodificacion` | `TranscodingAdapter` | Ports and adapters |
| `Usuario` | `User` | Domain model |
| `UsuarioId` | `UserId` | Domain model |
| `MapeadorUsuario` | `UserMapper` | Ports and adapters |
| `NavegadorWeb` | `WebBrowser` | Ports and adapters |
| `AdaptadorWebUI` | `WebUiAdapter` | Ports and adapters |

## Context-dependent terms

| Spanish | English | Where |
|---|---|---|
| `clave` | `key` | Metadata key: `MetadataEntry.key`, `metadataEntry(key)`, `editMetadata(..., key, value)` |
| `clave` | `password` | Local credentials: `LocalCredentials.password`, `createUser(..., password, ...)`, `ICredentialRegistry.register(username, password)` |
| `agregar` | `itemsToAdd` | Field of `CollectionCommand` |
| `agregar` | `add` | Method `Collection.add()` |

## Enum constants

| Spanish | English |
|---|---|
| `IMAGEN` | `IMAGE` |
| `DOCUMENTO` | `DOCUMENT` |
| `PENDIENTE` | `PENDING` |
| `INDEXANDO` | `INDEXING` |
| `INDEXADO` | `INDEXED` |
| `EXTRAIDO` | `EXTRACTED` |
| `ADMINISTRADOR` | `ADMIN` |
| `USUARIO` | `USER` |
| `INVITADO` | `GUEST` |
| `LECTURA` | `READ` |
| `ESCRITURA` | `WRITE` |
| `ADMINISTRACION` | `MANAGE` |
| `VER` | `VIEW` |
| `DESCARGAR` | `DOWNLOAD` |
| `MODIFICAR` | `MODIFY` |
| `ELIMINAR` | `DELETE` |
| `ACTIVA` | `ACTIVE` |
| `PAUSADA` | `PAUSED` |
| `FINALIZADA` | `FINISHED` |
| `RECHAZAR` | `REJECT` |
| `REEMPLAZAR` | `REPLACE` |
| `CONSERVAR_AMBOS` | `KEEP_BOTH` |
| `AGREGADO` | `ADDED` |
| `MODIFICADO` | `MODIFIED` |
| `ELIMINADO` | `REMOVED` |
| `CERRADO` | `CLOSED` |
| `ABIERTO` | `OPEN` |
| `SEMI_ABIERTO` | `HALF_OPEN` |

Constants not listed (`VIDEO`, `AUDIO`, `ERROR`, `MANUAL`) are the same in both languages.
