package co.omnistream.presentation.rest;

import co.omnistream.domain.exception.AccessDenied;
import co.omnistream.domain.exception.CyclicCollection;
import co.omnistream.domain.exception.DomainException;
import co.omnistream.domain.exception.ExpiredLink;
import co.omnistream.domain.exception.FileRejected;
import co.omnistream.domain.exception.InvalidCredentials;
import co.omnistream.domain.exception.ResourceNotFound;
import co.omnistream.domain.exception.VersionConflict;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;

/**
 * Turns every exception that reaches the API into the error response of the contract ({@code
 * docs/reference/rest-api.md}, "Errors"): a status and {@code {"error": ..., "message": ...}}.
 *
 * <p>Domain exceptions are expected outcomes: they are logged at {@code DEBUG}, without a stack
 * trace. Anything unexpected answers 500 without details, which go to the log (ADR 0021).
 */
public final class ApiErrors {

  private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);

  private static final Map<Class<? extends DomainException>, Integer> DOMAIN_STATUS =
      Map.of(
          InvalidCredentials.class, 401,
          AccessDenied.class, 403,
          ResourceNotFound.class, 404,
          VersionConflict.class, 409,
          CyclicCollection.class, 409,
          ExpiredLink.class, 410,
          FileRejected.class, 422);

  private ApiErrors() {}

  /**
   * The body of every error.
   *
   * @param error what went wrong, as a stable name a client can check
   * @param message an explanation for people
   */
  public record ApiError(String error, String message) {}

  /**
   * An error ready to send.
   *
   * @param status the HTTP status
   * @param body what goes in the body
   */
  record ErrorResponse(int status, ApiError body) {}

  /** Registers the handlers that answer every exception with the contract's error response. */
  public static void register(JavalinConfig config) {
    config.routes.exception(Exception.class, ApiErrors::handle);
    // Javalin answers its own exceptions (404 for an unknown route, 405...) unless told otherwise
    config.routes.exception(HttpResponseException.class, ApiErrors::handle);
  }

  private static void handle(Exception e, Context ctx) {
    ErrorResponse response = toResponse(e);
    if (response.status() >= 500) {
      LOG.error("Unexpected error in {} {}", ctx.method(), ctx.path(), e);
    } else {
      LOG.debug("{} {} answered {}: {}", ctx.method(), ctx.path(), response.status(), e.toString());
    }
    ctx.status(response.status()).json(response.body());
  }

  /** The response the contract gives to {@code e}. */
  static ErrorResponse toResponse(Exception e) {
    if (e instanceof DomainException domain) {
      Integer status = DOMAIN_STATUS.get(domain.getClass());
      if (status != null) {
        return new ErrorResponse(
            status, new ApiError(domain.getClass().getSimpleName(), domain.getMessage()));
      }
      // A new domain exception without a row in the contract's table
      return unexpected();
    }
    if (e instanceof HttpResponseException http) {
      return new ErrorResponse(
          http.getStatus(), new ApiError(httpError(http.getStatus()), message(e)));
    }
    if (e instanceof IllegalArgumentException || e instanceof JacksonException) {
      return new ErrorResponse(400, new ApiError("BadRequest", message(e)));
    }
    if (e instanceof IllegalStateException) {
      return new ErrorResponse(409, new ApiError("Conflict", message(e)));
    }
    return unexpected();
  }

  private static ErrorResponse unexpected() {
    return new ErrorResponse(500, new ApiError("InternalError", "Unexpected error"));
  }

  /** The name of an HTTP status without spaces ("Not Found" becomes "NotFound"). */
  private static String httpError(int status) {
    if (status == 401) {
      return "Unauthenticated";
    }
    return HttpStatus.forStatus(status).getMessage().replace(" ", "");
  }

  private static String message(Exception e) {
    return e.getMessage() == null ? "" : e.getMessage();
  }
}
