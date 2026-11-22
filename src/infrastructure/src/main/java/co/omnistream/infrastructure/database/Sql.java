package co.omnistream.infrastructure.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Small helper over JDBC that keeps the adapters readable: it binds parameters, closes every
 * statement and turns {@link SQLException} into {@link PersistenceException}.
 *
 * <p>It is not a framework: the SQL stays in the adapters and the mappers, as ADR 0015 says.
 */
public final class Sql {

  /** Reads one row of a {@link ResultSet}. */
  @FunctionalInterface
  public interface RowMapper<T> {
    T map(ResultSet row) throws SQLException;
  }

  private Sql() {}

  /** Every row of {@code sql}, mapped. */
  public static <T> List<T> list(
      Connection connection, String sql, RowMapper<T> mapper, Object... parameters) {
    try (PreparedStatement statement = prepare(connection, sql, parameters);
        ResultSet rows = statement.executeQuery()) {
      List<T> result = new ArrayList<>();
      while (rows.next()) {
        result.add(mapper.map(rows));
      }
      return result;
    } catch (SQLException e) {
      throw failed(sql, e);
    }
  }

  /** The first row of {@code sql}, if there is one. */
  public static <T> Optional<T> one(
      Connection connection, String sql, RowMapper<T> mapper, Object... parameters) {
    List<T> rows = list(connection, sql, mapper, parameters);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
  }

  /** The first column of the first row as a {@code long}, or {@code otherwise} without rows. */
  public static long count(
      Connection connection, String sql, long otherwise, Object... parameters) {
    Optional<Long> value = one(connection, sql, row -> row.getLong(1), parameters);
    return value.orElse(otherwise);
  }

  /** Runs an {@code INSERT}, {@code UPDATE} or {@code DELETE}; answers how many rows it changed. */
  public static int update(Connection connection, String sql, Object... parameters) {
    try (PreparedStatement statement = prepare(connection, sql, parameters)) {
      return statement.executeUpdate();
    } catch (SQLException e) {
      throw failed(sql, e);
    }
  }

  /** Runs a statement that returns nothing. */
  public static void execute(Connection connection, String sql, Object... parameters) {
    try (PreparedStatement statement = prepare(connection, sql, parameters)) {
      statement.execute();
    } catch (SQLException e) {
      throw failed(sql, e);
    }
  }

  /** An {@code UPDATE}; if it changed no row, inserts with {@code insert}. */
  public static void updateOrInsert(
      Connection connection,
      String update,
      Object[] updateParameters,
      String insert,
      Object[] insertParameters) {
    if (update(connection, update, updateParameters) == 0) {
      update(connection, insert, insertParameters);
    }
  }

  /** Runs {@code work} with the connection and closes the statement resources it opens. */
  private static PreparedStatement prepare(Connection connection, String sql, Object... parameters)
      throws SQLException {
    PreparedStatement statement = connection.prepareStatement(sql);
    try {
      for (int i = 0; i < parameters.length; i++) {
        statement.setObject(i + 1, parameters[i]);
      }
      return statement;
    } catch (SQLException e) {
      statement.close();
      throw e;
    }
  }

  private static PersistenceException failed(String sql, SQLException cause) {
    return new PersistenceException("Database operation failed: " + sql, cause);
  }
}
