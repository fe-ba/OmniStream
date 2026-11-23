package co.omnistream.infrastructure.database;

import java.sql.Connection;

/**
 * Creates the tables the persistence adapter needs, if they do not exist. The same script runs on
 * SQLite and on PostgreSQL, so both profiles store exactly the same data.
 *
 * <p>Snapshots are stored flat, one table per kind of row, as the mappers expect (ADR 0011).
 * Catalog items point to their collections through {@code collection_items}; files, collections,
 * users and sessions refer to each other by identifier.
 */
public final class Schema {

  private static final String[] STATEMENTS = {
    """
    CREATE TABLE IF NOT EXISTS catalog (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      version BIGINT NOT NULL
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS files (
      id TEXT PRIMARY KEY,
      catalog_id TEXT NOT NULL,
      name TEXT NOT NULL,
      path TEXT NOT NULL,
      fingerprint_algorithm TEXT NOT NULL,
      fingerprint_value TEXT NOT NULL,
      type TEXT NOT NULL,
      size_bytes BIGINT NOT NULL,
      duration_nanos BIGINT NOT NULL,
      status TEXT NOT NULL,
      ingested_at BIGINT NOT NULL
    )
    """,
    "CREATE INDEX IF NOT EXISTS files_catalog ON files (catalog_id)",
    "CREATE INDEX IF NOT EXISTS files_fingerprint ON files (fingerprint_value)",
    """
    CREATE TABLE IF NOT EXISTS file_metadata (
      file_id TEXT NOT NULL,
      entry_key TEXT NOT NULL,
      entry_value TEXT NOT NULL,
      source TEXT NOT NULL,
      PRIMARY KEY (file_id, entry_key)
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS file_tags (
      file_id TEXT NOT NULL,
      name TEXT NOT NULL,
      source TEXT NOT NULL,
      PRIMARY KEY (file_id, name)
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS collections (
      id TEXT PRIMARY KEY,
      catalog_id TEXT NOT NULL,
      name TEXT NOT NULL,
      description TEXT NOT NULL
    )
    """,
    "CREATE INDEX IF NOT EXISTS collections_catalog ON collections (catalog_id)",
    """
    CREATE TABLE IF NOT EXISTS collection_items (
      collection_id TEXT NOT NULL,
      item_id TEXT NOT NULL,
      item_order INTEGER NOT NULL,
      PRIMARY KEY (collection_id, item_id)
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      username TEXT NOT NULL UNIQUE,
      role TEXT NOT NULL,
      active INTEGER NOT NULL
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS user_permissions (
      user_id TEXT NOT NULL,
      resource_id TEXT NOT NULL,
      level TEXT NOT NULL,
      PRIMARY KEY (user_id, resource_id)
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS user_identities (
      user_id TEXT NOT NULL,
      issuer TEXT NOT NULL,
      subject TEXT NOT NULL,
      PRIMARY KEY (user_id, issuer, subject)
    )
    """,
    "CREATE INDEX IF NOT EXISTS identities_lookup ON user_identities (issuer, subject)",
    """
    CREATE TABLE IF NOT EXISTS playback_sessions (
      id TEXT PRIMARY KEY,
      file_id TEXT NOT NULL,
      user_id TEXT NOT NULL,
      position_nanos BIGINT NOT NULL,
      total_nanos BIGINT NOT NULL,
      status TEXT NOT NULL,
      started_at BIGINT NOT NULL
    )
    """,
    "CREATE INDEX IF NOT EXISTS sessions_user_file ON playback_sessions (user_id, file_id)",
    """
    CREATE TABLE IF NOT EXISTS shared_links (
      id TEXT PRIMARY KEY,
      token TEXT NOT NULL UNIQUE,
      collection_id TEXT NOT NULL,
      guest_id TEXT NOT NULL,
      level TEXT NOT NULL,
      created_by TEXT NOT NULL,
      expires_at BIGINT NOT NULL,
      revoked INTEGER NOT NULL
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS audit_entries (
      id TEXT PRIMARY KEY,
      happened_at BIGINT NOT NULL,
      actor TEXT NOT NULL,
      action TEXT NOT NULL,
      resource TEXT,
      subject TEXT,
      detail TEXT NOT NULL
    )
    """,
    "CREATE INDEX IF NOT EXISTS audit_happened ON audit_entries (happened_at)",
    """
    CREATE TABLE IF NOT EXISTS change_log (
      event_id TEXT PRIMARY KEY,
      occurred_at BIGINT NOT NULL,
      event_type TEXT NOT NULL,
      item_id TEXT NOT NULL
    )
    """,
    "CREATE INDEX IF NOT EXISTS change_log_occurred ON change_log (occurred_at)",
    """
    CREATE TABLE IF NOT EXISTS outbox (
      event_id TEXT PRIMARY KEY,
      occurred_at BIGINT NOT NULL,
      event_type TEXT NOT NULL,
      item_id TEXT NOT NULL,
      published INTEGER NOT NULL DEFAULT 0
    )
    """,
    "CREATE INDEX IF NOT EXISTS outbox_pending ON outbox (published, occurred_at)",
    """
    CREATE TABLE IF NOT EXISTS credentials (
      username TEXT PRIMARY KEY,
      hash TEXT NOT NULL
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS settings (
      setting_key TEXT PRIMARY KEY,
      setting_value TEXT NOT NULL
    )
    """
  };

  private Schema() {}

  /** Creates every table and index that does not exist yet. */
  public static void create(Connection connection) {
    for (String statement : STATEMENTS) {
      Sql.execute(connection, statement);
    }
  }
}
