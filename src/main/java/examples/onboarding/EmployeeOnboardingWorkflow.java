package examples.onboarding;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import engine.DurableContext;

public class EmployeeOnboardingWorkflow {

    private static final Logger log = LoggerFactory.getLogger(EmployeeOnboardingWorkflow.class);

    public void run(DurableContext ctx) throws Exception {

        // Step 1: Create employee (sequential)
        String employeeId = ctx.step(() -> {
            log.info("Creating employee record...");
            return "EMP-001";
        });

        // Step 2 & 3: Assign laptop & provision access (parallel)
        CompletableFuture<String> itFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return ctx.step(() -> {
                    log.info("Assigning laptop & email for {}", employeeId);
                    return "IT-ASSIGNED";
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture<String> accessFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return ctx.step(() -> {
                    log.info("Provisioning system access for {}", employeeId);
                    return "ACCESS-PROVISIONED";
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture.allOf(itFuture, accessFuture).join();

        // Step 4: Schedule orientation (sequential)
        ctx.step(() -> {
            log.info("Scheduling orientation session...");
            return "ORIENTATION-SCHEDULED";
        });

        log.info("Workflow completed successfully.");
    }
}
