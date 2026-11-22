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
| `AccesoDenegado` | `AccessDenied` | Domain model |
| `ConcesionAcceso` | `AccessGrant` | Services |
| `PoliticaAcceso` | `AccessPolicy` | Domain model |
| `ServicioAcceso` | `AccessService` | Services |
| `TipoAcceso` | `AccessType` | Domain model |
| `ErroresApi` | `ApiErrors` | Ports and adapters |
| `JsonApi` | `ApiJson` | Ports and adapters |
| `ConfiguracionApp` | `AppConfiguration` | Ports and adapters |
| `AccionAuditoria` | `AuditAction` | Domain model |
| `EntradaAuditoria` | `AuditEntry` | Domain model |
| `EntradaAuditoriaId` | `AuditEntryId` | Domain model |
| `MapeadorEntradaAuditoria` | `AuditEntryMapper` | Ports and adapters |
| `ConsultaAuditoria` | `AuditQuery` | Services |
| `ServicioAuditoria` | `AuditService` | Services |
| `ControlAccesoAuditado` | `AuditedAccessControl` | Services |
| `GestionCatalogoAuditada` | `AuditedCatalogManagement` | Services |
| `BusquedaAuditada` | `AuditedSearch` | Services |
| `SincronizacionAuditada` | `AuditedSync` | Services |
| `DecoradorAuditoria` | `AuditingDecorator` | Services |
| `ServicioAutorizado` | `AuthorizedService` | Services |
| `TareaRespaldar` | `BackupTask` | Ports and adapters |
| `RepositorioCatalogoConCache` | `CachedCatalogRepository` | Ports and adapters |
| `TranscodificadorConCache` | `CachedTranscoder` | Ports and adapters |
| `EventoCatalogo` | `CatalogEvent` | Domain model |
| `CatalogoId` | `CatalogId` | Domain model |
| `CambioCatalogo` | `CatalogChange` | Services |
| `OyenteCambiosCatalogo` | `CatalogChangeListener` | Ports and adapters |
| `ServicioFlujoCatalogo` | `CatalogFeedService` | Services |
| `CatalogoIndice` | `CatalogIndex` | Domain model |
| `MapeadorCatalogoIndice` | `CatalogIndexMapper` | Ports and adapters |
| `ElementoCatalogo` | `CatalogItem` | Domain model |
| `InstantaneaElemento` | `CatalogItemSnapshot` | Domain model |
| `ServicioCatalogo` | `CatalogService` | Services |
| `InstantaneaCatalogo` | `CatalogSnapshot` | Domain model |
| `TipoCambio` | `ChangeType` | Domain model |
| `EstadoCircuito` | `CircuitState` | Ports and adapters |
| `Clasificacion` | `Classification` | Domain model |
| `CriterioClasificacion` | `ClassificationCriterion` | Domain model |
| `ReglaClasificacion` | `ClassificationRule` | Services |
| `ServicioClasificacion` | `ClassificationService` | Services |
| `PasoClasificacion` | `ClassificationStep` | Services |
| `AdaptadorCLI` | `CliAdapter` | Ports and adapters |
| `Coleccion` | `Collection` | Domain model |
| `ComandoColeccion` | `CollectionCommand` | Services |
| `ColeccionCreada` | `CollectionCreated` | Domain model |
| `MapeadorColeccion` | `CollectionMapper` | Ports and adapters |
| `ColeccionModificada` | `CollectionModified` | Domain model |
| `InstantaneaColeccion` | `CollectionSnapshot` | Domain model |
| `ComposicionRaiz` | `CompositionRoot` | Ports and adapters |
| `CargadorConfiguracion` | `ConfigurationLoader` | Ports and adapters |
| `Credenciales` | `Credentials` | Services |
| `MapeadorCriterio` | `CriterionMapper` | Ports and adapters |
| `ColeccionCiclica` | `CyclicCollection` | Domain model |
| `ReglaPorFecha` | `DateRule` | Services |
| `PoliticaDeduplicacion` | `DeduplicationPolicy` | Domain model |
| `StreamDirecto` | `DirectStream` | Services |
| `EventoDominio` | `DomainEvent` | Domain model |
| `ExcepcionDominio` | `DomainException` | Domain model |
| `AccionDuplicado` | `DuplicateAction` | Domain model |
| `PasoDuplicados` | `DuplicatesStep` | Services |
| `AdaptadorBusEventos` | `EventBusAdapter` | Ports and adapters |
| `ServicioDespachoEventos` | `EventDispatchService` | Services |
| `EventoId` | `EventId` | Domain model |
| `MapeadorEvento` | `EventMapper` | Ports and adapters |
| `EnlaceVencido` | `ExpiredLink` | Domain model |
| `PasoExtraccion` | `ExtractionStep` | Services |
| `ArchivoClasificado` | `FileClassified` | Domain model |
| `ArchivoEliminado` | `FileDeleted` | Domain model |
| `ArchivoIngestado` | `FileIngested` | Domain model |
| `ArchivoModificado` | `FileModified` | Domain model |
| `ArchivoRechazado` | `FileRejected` | Domain model |
| `SuscripcionFlujo` | `FeedSubscription` | Ports and adapters |
| `HuellaDigital` | `Fingerprint` | Domain model |
| `Seguidor` | `Follower` | Services |
| `IControlAcceso` | `IAccessControl` | Ports and adapters |
| `IRegistroAuditoria` | `IAuditLog` | Ports and adapters |
| `IHistorialAuditoria` | `IAuditTrail` | Ports and adapters |
| `IFlujoCatalogo` | `ICatalogFeed` | Ports and adapters |
| `IGestionCatalogo` | `ICatalogManagement` | Ports and adapters |
| `IObservadorCatalogo` | `ICatalogObserver` | Services |
| `IRepositorioCatalogo` | `ICatalogRepository` | Ports and adapters |
| `IRegistroCambios` | `IChangeLog` | Ports and adapters |
| `IRepositorioConfiguracion` | `IConfigurationRepository` | Ports and adapters |
| `IRegistroCredenciales` | `ICredentialRegistry` | Ports and adapters |
| `IRespaldoDatos` | `IDataBackup` | Ports and adapters |
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
| `CredencialesInvalidas` | `InvalidCredentials` | Domain model |
| `IRepositorioOutbox` | `IOutboxRepository` | Ports and adapters |
| `IReproduccion` | `IPlayback` | Ports and adapters |
| `IGestionReglas` | `IRuleManagement` | Ports and adapters |
| `IRepositorioReglas` | `IRuleRepository` | Ports and adapters |
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
| `MapeadorIdentidad` | `LinkedIdentityMapper` | Ports and adapters |
| `CredencialesLocales` | `LocalCredentials` | Services |
| `FabricaLocal` | `LocalFactory` | Ports and adapters |
| `AdaptadorArchivosLocal` | `LocalFileStorageAdapter` | Ports and adapters |
| `AdaptadorIdentidadLocal` | `LocalIdentityAdapter` | Ports and adapters |
| `ArchivoMultimedia` | `MediaFile` | Domain model |
| `MapeadorArchivo` | `MediaFileMapper` | Ports and adapters |
| `InstantaneaArchivo` | `MediaFileSnapshot` | Domain model |
| `FlujoMultimedia` | `MediaStream` | Services |
| `TipoMedio` | `MediaType` | Domain model |
| `ReglaPorTipo` | `MediaTypeRule` | Services |
| `AdaptadorMetadatos` | `MetadataAdapter` | Ports and adapters |
| `Metadato` | `MetadataEntry` | Domain model |
| `MapeadorMetadato` | `MetadataEntryMapper` | Ports and adapters |
| `OrigenMetadato` | `MetadataSource` | Domain model |
| `AdaptadorArchivosRed` | `NetworkFileStorageAdapter` | Ports and adapters |
| `PaqueteOffline` | `OfflinePackage` | Services |
| `CredencialesOIDC` | `OidcCredentials` | Services |
| `AdaptadorIdentidadOIDC` | `OidcIdentityAdapter` | Ports and adapters |
| `Permiso` | `Permission` | Domain model |
| `NivelPermiso` | `PermissionLevel` | Domain model |
| `MapeadorPermiso` | `PermissionMapper` | Ports and adapters |
| `ServicioReproduccion` | `PlaybackService` | Services |
| `SesionReproduccion` | `PlaybackSession` | Domain model |
| `InstantaneaSesion` | `PlaybackSessionSnapshot` | Domain model |
| `AdaptadorPersistenciaPostgreSQL` | `PostgreSqlPersistenceAdapter` | Ports and adapters |
| `TareaReindexar` | `ReindexTask` | Ports and adapters |
| `ProveedorIdentidadResiliente` | `ResilientIdentityProvider` | Ports and adapters |
| `RecursoNoEncontrado` | `ResourceNotFound` | Domain model |
| `AdaptadorApiRest` | `RestApiAdapter` | Ports and adapters |
| `Rol` | `Role` | Domain model |
| `OperadorRegla` | `RuleOperator` | Domain model |
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
| `InstantaneaEnlace` | `SharedLinkSnapshot` | Domain model |
| `AdaptadorPersistenciaSQLite` | `SqlitePersistenceAdapter` | Ports and adapters |
| `PerfilStream` | `StreamProfile` | Services |
| `EstrategiaStream` | `StreamStrategy` | Services |
| `DeltaSincronizacion` | `SyncDelta` | Services |
| `ObservadorSincronizacion` | `SyncObserver` | Services |
| `ServicioSincronizacion` | `SyncService` | Services |
| `TareaSincronizar` | `SyncTask` | Ports and adapters |
| `Etiqueta` | `Tag` | Domain model |
| `OrigenEtiqueta` | `TagSource` | Domain model |
| `StreamTranscodificado` | `TranscodedStream` | Services |
| `AdaptadorTranscodificacion` | `TranscodingAdapter` | Ports and adapters |
| `Usuario` | `User` | Domain model |
| `ReglasDelUsuario` | `UserDefinedRules` | Services |
| `UsuarioId` | `UserId` | Domain model |
| `MapeadorUsuario` | `UserMapper` | Ports and adapters |
| `InstantaneaUsuario` | `UserSnapshot` | Domain model |
| `ConflictoDeVersion` | `VersionConflict` | Domain model |
| `NavegadorWeb` | `WebBrowser` | Ports and adapters |
| `AdaptadorWebUI` | `WebUiAdapter` | Ports and adapters |

## Context-dependent terms

| Spanish | English | Where |
|---|---|---|
| `clave` | `key` | Metadata key: `MetadataEntry.key`, `metadataEntry(key)`, `editMetadata(..., key, value)` |
| `clave` | `password` | Local credentials: `LocalCredentials.password`, `createUser(..., password, ...)`, `ICredentialRegistry.register(username, password)` |
| `agregar` | `itemsToAdd` | Field of `CollectionCommand` |
| `agregar` | `add` | Method `Collection.add()` |
| `registra` | `registers` | `IEventSubscriber` registering observers |
| `registra` | `records` | Aggregates and the change log recording events |

## Delegation (ADR 0024)

| Spanish | English | Where |
|---|---|---|
| `otorgante` | `granter` | Who gives or takes a permission: `grantPermission(granter, ...)`, `revokePermission(granter, ...)` |
| `puedeDelegar()` | `mayDelegate()` | `AccessPolicy` |
| `nivelHeredado` | `inheritedLevel` | Level reached through the collections that contain a resource |
| `titular` | `user` | Field of `AccessGrant`: who holds the permission |
| `accesoA()` | `accessTo()` | Access list of a resource |
| `listarUsuarios()` / `buscarUsuario()` / `buscarTodos()` | `listUsers()` / `findUser()` / `findAll()` | Administration and user lookup |

## Audit trail (ADR 0025)

| Spanish | English | Where |
|---|---|---|
| `de()` / `sobre()` / `referidaA()` / `diciendo()` | `of()` / `about()` / `concerning()` / `saying()` | Building an `AuditEntry` |
| `en` / `actor` / `sujeto` / `detalle` | `at` / `actor` / `subject` / `detail` | Parts of an `AuditEntry` |
| `inicio` / `fin` / `limite` / `ultimas()` / `coincide()` | `from` / `to` / `limit` / `latest()` / `matches()` | `AuditQuery` |
| `anexar()` / `historial()` | `append()` / `history()` | `IAuditLog`, `IAuditTrail` |
| `auditado()` / `anotar()` / `entrada()` / `delegado` | `audited()` / `record()` / `entry()` / `delegate` | `AuditingDecorator` and its subclasses |
| `auditarPuertosEntrada()` / `crearRegistroAuditoria()` | `auditDrivingPorts()` / `createAuditLog()` | Composition |
| `Gestionar acceso a una coleccion` (UC28) / `Consultar historial de cambios` (UC29) | `Manage access to a collection` / `Review change history` | Use cases |

## Live changes (ADR 0026)

| Spanish | English | Where |
|---|---|---|
| `seguir()` / `alCambiar()` / `cerrar()` | `follow()` / `onChange()` / `close()` | `ICatalogFeed`, `CatalogChangeListener`, `FeedSubscription` |
| `oyente` / `flujoCatalogo` / `suscriptorEventos` | `listener` / `catalogFeed` / `eventSubscriber` | Fields and parameters |
| `elemento` / `cambio` / `evento` | `item` / `change` / `event` | Parts of a `CatalogChange` |
| `puedeVer()` / `detener()` / `usuarioActivo()` | `canSee()` / `stop()` / `activeUser()` | `CatalogFeedService` and its `Follower` |

## Recurring member names

| Spanish | English |
|---|---|
| `aInstantanea()` / `desdeInstantanea()` | `toSnapshot()` / `fromSnapshot()` |
| `aValor()` / `asignar()` (mappers) | `toValue()` / `bind()` |
| `buscar...` / `guardar` / `eliminar` | `find...` / `save` / `delete` |
| `ahora` / `reloj` | `now` / `clock` |
| `carpetaRecursos` (`AdaptadorWebUI`) | `resourceFolder` (`WebUiAdapter`) |

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
| `AUTOMATICA` | `AUTOMATIC` |
| `IGUAL` | `EQUALS` |
| `CONTIENE` | `CONTAINS` |
| `MAYOR_QUE` | `GREATER_THAN` |
| `MENOR_QUE` | `LESS_THAN` |
| `INICIO_SESION` / `INICIO_FALLIDO` / `CIERRE_SESION` | `LOGGED_IN` / `LOGIN_FAILED` / `LOGGED_OUT` |
| `ENLACE_CREADO` / `ENLACE_USADO` | `LINK_CREATED` / `LINK_USED` |
| `USUARIO_CREADO` / `ROL_CAMBIADO` / `USUARIO_DESACTIVADO` | `USER_CREATED` / `ROLE_CHANGED` / `USER_DEACTIVATED` |
| `PERMISO_OTORGADO` / `PERMISO_REVOCADO` | `PERMISSION_GRANTED` / `PERMISSION_REVOKED` |
| `ARCHIVO_AGREGADO` / `ARCHIVO_ELIMINADO` / `ARCHIVO_ETIQUETADO` / `ARCHIVO_DESCARGADO` | `FILE_ADDED` / `FILE_DELETED` / `FILE_TAGGED` / `FILE_DOWNLOADED` |
| `METADATO_EDITADO` | `METADATA_EDITED` |
| `COLECCION_CREADA` / `COLECCION_MODIFICADA` | `COLLECTION_CREATED` / `COLLECTION_MODIFIED` |
| `BIBLIOTECA_REINDEXADA` / `RUTA_BIBLIOTECA_CAMBIADA` / `RESPALDO_CREADO` | `LIBRARY_REINDEXED` / `LIBRARY_PATH_CHANGED` / `BACKUP_CREATED` |

Constants not listed (`VIDEO`, `AUDIO`, `ERROR`, `MANUAL`) are the same in both languages.
