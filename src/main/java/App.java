import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import engine.DurableContext;
import engine.SQLiteStore;
import examples.onboarding.EmployeeOnboardingWorkflow;

public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:durable.db");
        SQLiteStore store = new SQLiteStore(connection);

        DurableContext ctx = new DurableContext("wf-001", store);
        EmployeeOnboardingWorkflow workflow = new EmployeeOnboardingWorkflow();

        Scanner scanner = new Scanner(System.in);
        log.info("Type 'exit' anytime to simulate a crash. Press Enter to continue.");
        String input = scanner.nextLine();
        if ("exit".equalsIgnoreCase(input)) {
            log.info("Simulating crash...");
            System.exit(0);
        }

        workflow.run(ctx);

        log.info("You can re-run this program to resume workflow if interrupted.");
        connection.close();
    }
}
