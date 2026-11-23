package co.omnistream.presentation.demo;

import co.omnistream.domain.policy.AccessPolicy;
import co.omnistream.domain.policy.TextFilePolicy;
import co.omnistream.domain.port.in.IAccessControl;
import co.omnistream.domain.port.in.ICatalogFeed;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.domain.port.in.ISearch;
import co.omnistream.domain.port.in.ISync;
import co.omnistream.domain.port.in.ITextEditing;
import co.omnistream.domain.port.out.IAuditLog;
import co.omnistream.domain.port.out.IUnitOfWork;
import co.omnistream.domain.service.CatalogFeedService;
import co.omnistream.domain.service.TextEditingService;
import co.omnistream.domain.service.audit.AuditedAccessControl;
import co.omnistream.domain.service.audit.AuditedCatalogManagement;
import co.omnistream.domain.service.audit.AuditedSearch;
import co.omnistream.domain.service.audit.AuditedSync;
import co.omnistream.domain.service.audit.AuditedTextEditing;
import co.omnistream.presentation.rest.RestApiAdapter;
import co.omnistream.presentation.rest.TextEditingRelay;
import co.omnistream.presentation.web.WebUiAdapter;
import io.javalin.Javalin;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Starts the REST API and the web UI on {@link DemoLibrary}, to try them in a browser before the
 * services and the infrastructure exist. It only runs when asked for, from {@code src/}:
 *
 * <pre>
 * ./mvnw -pl presentation -am test -Ddemo=true -Dtest=DemoServer \
 *     -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * <p>Then open {@code http://localhost:7070} and log in as {@code admin}, {@code ana} or {@code
 * luis} (the password is the username). {@code -Ddemo.port=...} changes the port. Ctrl+C stops it.
 *
 * <p>It composes the demo like the composition root composes the application: the real audit
 * decorators (ADR 0025) around the demo's ports, the real live feed (ADR 0026) on the demo's event
 * bus, and the real text editing service and relay (ADR 0027) on the demo's files. Open two
 * browsers with different users to see the changes arrive, or open "Notes" as {@code ana} and as
 * {@code luis} to edit a file together.
 */
@EnabledIfSystemProperty(named = "demo", matches = "true")
class DemoServer {

  @Test
  void serveTheDemo() throws InterruptedException {
    System.setProperty("java.awt.headless", "true");
    int port = Integer.getInteger("demo.port", 7070);
    DemoLibrary library = new DemoLibrary();
    Clock clock = Clock.systemUTC();
    IAuditLog auditLog = library.auditLog();
    IUnitOfWork unitOfWork = DemoLibrary.unitOfWork();
    IAccessControl access =
        new AuditedAccessControl(library, library.userRepository(), auditLog, unitOfWork, clock);
    ICatalogManagement catalog =
        new AuditedCatalogManagement(
            library, library.catalogRepository(), auditLog, unitOfWork, clock);
    ISearch search = new AuditedSearch(library, auditLog, unitOfWork, clock);
    ISync sync = new AuditedSync(library, auditLog, unitOfWork, clock);
    ICatalogFeed feed =
        new CatalogFeedService(
            library.userRepository(),
            new AccessPolicy(),
            clock,
            library.eventBus(),
            library.catalogRepository());
    ITextEditing editing =
        new AuditedTextEditing(
            new TextEditingService(
                library.userRepository(),
                new AccessPolicy(),
                clock,
                library.catalogRepository(),
                library.fileStorage(),
                unitOfWork,
                new TextFilePolicy()),
            library.catalogRepository(),
            auditLog,
            unitOfWork,
            clock);
    RestApiAdapter api = new RestApiAdapter(catalog, library, search, access, sync, library, feed);
    TextEditingRelay editor = new TextEditingRelay(editing, access);
    WebUiAdapter web = new WebUiAdapter();
    Javalin.create(
            config -> {
              api.registerRoutes(config);
              editor.registerRoutes(config);
              web.registerRoutes(config);
            })
        .start(port);
    System.out.printf(
        "%nOmniStream demo on http://localhost:%d (admin, ana or luis; the password is the"
            + " username). Ctrl+C to stop.%n%n",
        port);
    Thread.currentThread().join();
  }
}
