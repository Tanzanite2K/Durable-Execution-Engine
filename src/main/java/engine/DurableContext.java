package engine;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DurableContext {

    private final String workflowId;
    private final SQLiteStore store;
    private final ObjectMapper mapper = new ObjectMapper();
    private final long zombieTimeoutMs = 5000;
    private final AtomicInteger sequenceCounter = new AtomicInteger(0);
    private static final Logger log = LoggerFactory.getLogger(DurableContext.class);

    public DurableContext(String workflowId, SQLiteStore store) {
        this.workflowId = workflowId;
        this.store = store;
    }

    // Step method with automatic sequence ID
    public <T> T step(Callable<T> action) throws Exception {
        String stepId = "step-" + sequenceCounter.incrementAndGet();
        return step(stepId, action);
    }

    // Original step method (still available if user wants manual ID)
    public <T> T step(String stepId, Callable<T> action) throws Exception {

        StepRecord existing = store.getStep(workflowId, stepId);

        if (existing != null) {

            if (existing.getStatus().equals(StepStatus.COMPLETED.name())) {
                return mapper.readValue(existing.getOutput(), Object.class) != null
                        ? (T) mapper.readValue(existing.getOutput(), Object.class)
                        : null;
            }

            if (existing.getStatus().equals(StepStatus.IN_PROGRESS.name())) {
                long age = Instant.now().toEpochMilli() - existing.getUpdatedAt();
                if (age < zombieTimeoutMs) {
                    throw new IllegalStateException("Step currently in progress.");
                }
            }
        }

        store.insertInProgress(workflowId, stepId);

        T result = action.call();

        String json = mapper.writeValueAsString(result);

        store.markCompleted(workflowId, stepId, json);

        return result;
    }

    public String getWorkflowId() {
        return workflowId;
    }
}
