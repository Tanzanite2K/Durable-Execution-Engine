package engine;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

public class DurableEngineTest {

    @Test
    void testStepRecordCreation() {
        // Add a dummy updatedAt timestamp
        StepRecord record = new StepRecord(
                "wf1",
                "step1",
                "COMPLETED",
                "output",
                System.currentTimeMillis()  // <-- required long parameter
        );

        assertEquals("wf1", record.getWorkflowId());
        assertEquals("COMPLETED", record.getStatus());
        assertEquals("step1", record.getStepId());
        assertEquals("output", record.getOutput());
        assertNotNull(record.getUpdatedAt());
    }

    @Test
    void testSQLiteStoreInitialization() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SQLiteStore store = new SQLiteStore(connection);
        assertNotNull(store);
        connection.close();
    }
}
