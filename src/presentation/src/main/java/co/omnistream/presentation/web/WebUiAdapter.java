package co.omnistream.presentation.web;

import io.javalin.config.JavalinConfig;
import io.javalin.http.staticfiles.Location;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Primary adapter that serves the web UI: an HTML page with its script and styles, packaged in the
 * jar under {@value #RESOURCES}.
 *
 * <p>It knows no port. The page runs in the browser and calls the REST API ({@code
 * docs/reference/rest-api.md}) like any other client, so this adapter only hands out files.
 *
 * <p>Routes: {@code /} and the files next to {@code index.html}, plus {@code /s/{token}}, the
 * address of a shared link, which answers the same page; the page then opens the link through the
 * API.
 */
public final class WebUiAdapter {

  /** Classpath folder with the files of the web UI. */
  public static final String RESOURCES = "/web";

  private final String resourceFolder;

  /** A web UI served from {@value #RESOURCES}. */
  public WebUiAdapter() {
    this(RESOURCES);
  }

  WebUiAdapter(String resourceFolder) {
    this.resourceFolder = Objects.requireNonNull(resourceFolder, "resourceFolder");
  }

  /** Registers the web UI on a Javalin configuration, next to the REST API. */
  public void registerRoutes(JavalinConfig config) {
    // Read first: without its page, the web UI cannot start
    String page = indexPage();
    config.staticFiles.add(files -> {
      files.hostedPath = "/";
      files.directory = resourceFolder;
      files.location = Location.CLASSPATH;
    });
    config.routes.get("/s/{token}", ctx -> ctx.html(page));
  }

  private String indexPage() {
    String name = resourceFolder + "/index.html";
    try (InputStream page = WebUiAdapter.class.getResourceAsStream(name)) {
      if (page == null) {
        throw new IllegalStateException("The web UI is missing " + name);
      }
      return new String(page.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + name, e);
    }
  }
}
