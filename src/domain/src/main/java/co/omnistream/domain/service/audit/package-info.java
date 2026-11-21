/**
 * Decorators that write the audit trail (Decorator, ADR 0025). Each one wraps a driving port: it
 * passes every call on to the service it wraps and, when the call changed something, records an
 * {@code AuditEntry} in the same unit of work. The services know nothing about the audit.
 *
 * <p>Owner: @fe-ba (ADR 0019).
 */
package co.omnistream.domain.service.audit;
