package engine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

public class SQLiteStore {

    private final Connection connection;
    private final Object lock = new Object();

    public SQLiteStore(Connection connection) throws SQLException {
        this.connection = connection;
        initialize();
    }

    private void initialize() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA busy_timeout=5000;");
        }

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS steps (" +
                         "workflow_id TEXT," +
                         "step_id TEXT," +
                         "status TEXT," +
                         "output TEXT," +
                         "updated_at INTEGER," +
                         "PRIMARY KEY (workflow_id, step_id)" +
                         ");");
        }
    }

    public StepRecord getStep(String workflowId, String stepId) throws SQLException {
        String sql = "SELECT * FROM steps WHERE workflow_id=? AND step_id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            ps.setString(2, stepId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new StepRecord(
                        rs.getString("workflow_id"),
                        rs.getString("step_id"),
                        rs.getString("status"),
                        rs.getString("output"),
                        rs.getLong("updated_at")
                );
            }
            return null;
        }
    }

    public void insertInProgress(String workflowId, String stepId) throws SQLException {
        synchronized (lock) {
            executeWithRetry(() -> {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT OR REPLACE INTO steps (workflow_id, step_id, status, output, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?)"
                )) {
                    ps.setString(1, workflowId);
                    ps.setString(2, stepId);
                    ps.setString(3, StepStatus.IN_PROGRESS.name());
                    ps.setString(4, null);
                    ps.setLong(5, Instant.now().toEpochMilli());
                    ps.executeUpdate();
                    connection.commit();
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                }
                return null;
            });
        }
    }

    public void markCompleted(String workflowId, String stepId, String output) throws SQLException {
        synchronized (lock) {
            executeWithRetry(() -> {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE steps SET status=?, output=?, updated_at=? WHERE workflow_id=? AND step_id=?"
                )) {
                    ps.setString(1, StepStatus.COMPLETED.name());
                    ps.setString(2, output);
                    ps.setLong(3, Instant.now().toEpochMilli());
                    ps.setString(4, workflowId);
                    ps.setString(5, stepId);
                    ps.executeUpdate();
                    connection.commit();
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                }
                return null;
            });
        }
    }

    private interface SQLAction<T> {
        T execute() throws Exception;
    }

    private <T> T executeWithRetry(SQLAction<T> action) throws SQLException {
        int retries = 5;
        while (retries-- > 0) {
            try {
                return action.execute();
            } catch (Exception e) {
                if (e instanceof SQLException sql && sql.getMessage().contains("SQLITE_BUSY")) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                } else {
                    throw new SQLException(e);
                }
            }
        }
        throw new SQLException("Max retry reached due to SQLITE_BUSY");
    }
}
