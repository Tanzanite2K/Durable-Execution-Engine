# Durable Execution Engine

## Overview

The **Durable Execution Engine** is your safety net for workflows. It allows native Java programs to **survive crashes, power failures, or accidental exits**, and pick up exactly where they left off—without repeating already completed work.

Inspired by robust systems like **Temporal**, **Cadence**, and **Azure Durable Functions**, it’s designed to make your workflows:

* Resilient
* Deterministic
* Thread-safe
* Fully observable through logging

Whether you’re onboarding employees, provisioning resources, or running iterative batch jobs, this engine makes sure **no step is lost or duplicated**.

---

## Features

### 1. Workflow Runner

* Start or resume workflows seamlessly.
* Tracks every step in a persistent SQLite database.

### 2. Step Primitive

* Generic step method: `<T> T step(Callable<T> action)`
* Checkpoints output automatically.
* Completed steps are skipped on re-runs, ensuring **idempotent execution**.

### 3. Automatic Sequence ID

* No more manually assigning step IDs.
* Each step automatically gets a unique sequence number via an `AtomicInteger`.
* Example:

```java
String employeeId = ctx.step(() -> createEmployee());
```

*Even if your workflow loops or repeats steps, the engine ensures uniqueness.*

### 4. Concurrency Support

* Steps can run concurrently with `CompletableFuture`.
* Writes to SQLite are synchronized to prevent conflicts (`SQLITE_BUSY`).

### 5. Zombie Step Handling

* Steps are marked `RUNNING` before execution.
* If a crash occurs mid-step, incomplete steps are safely retried on workflow restart.
* Completed steps are never re-executed.

### 6. Logging

* Uses **SLF4J** for professional logging instead of `System.out.println`.
* Logs every workflow action, start/end of steps, and errors.
* Example:

```java
private static final Logger logger = LoggerFactory.getLogger(EmployeeOnboardingWorkflow.class);

logger.info("Creating employee record...");
```

### 7. Persistence Layer

* All steps are stored in SQLite with:

| Column      | Description                         |
| ----------- | ----------------------------------- |
| workflow_id | Unique identifier of the workflow   |
| step_key    | Step name + auto-generated sequence |
| status      | `IN_PROGRESS` or `COMPLETED`        |
| output      | JSON-serialized result              |
| updated_at  | Last update timestamp               |

---

## Sequence Tracking & Loop Handling

### Problem

Loops or iterative steps can cause:

* Re-execution of already completed steps
* Duplicate side effects
* Infinite repetitions after crashes

We need **idempotent replay**.

### Solution

* Every step is persisted in SQLite.
* Step keys are unique: `stepName + "_" + sequenceNumber`.
* Before running a step:

  ```java
  if (step already completed) {
      reuse stored output;
  } else {
      execute step;
      persist result;
  }
  ```

This approach guarantees:

* Crash safety
* Deterministic replay
* No duplicates

Even loops or retries behave correctly.

---

## Thread Safety During Parallel Execution

### Challenges

Parallel workflows risk:

* Race conditions
* Dirty reads
* Lost updates
* Corrupted state

### Safeguards

1. **Database-Level Consistency**

   * ACID transactions, serialized writes, atomic commits.

2. **Connection Isolation**

   * Each workflow uses its own SQLite connection.

3. **Immutable Step Records**

   * Once created, a `StepRecord` is never modified.

4. **Controlled Execution Model**

   * Steps marked `COMPLETED` only after successful execution.
   * Reads always check persistent state before execution.

---

## Execution Guarantees

* At-least-once execution
* Idempotent step replay
* Crash recovery
* Consistent persistence
* Safe parallel execution

---

## Project Structure

```
durable-engine/
│
├─ engine/                 # Core library
│  ├─ DurableContext.java
│  ├─ SQLiteStore.java
│  ├─ StepRecord.java
│  └─ StepStatus.java
│
├─ examples/onboarding/    # Sample workflow
│  └─ EmployeeOnboardingWorkflow.java
│
├─ App.java                # CLI entry point
├─ pom.xml                 # Maven configuration
└─ README.md
```

---

## How to Run

1. **Build & Compile**

```bash
mvn clean compile
```

2. **Run Tests**

```bash
mvn test
```

3. **Execute Workflow**

```bash
mvn exec:java
```

* Type `exit` during execution to simulate a crash.
* Re-run the program—completed steps will **never re-execute**.

---

## Example: Employee Onboarding Workflow

Steps:

1. Create Employee Record
2. Provision Laptop & Email (parallel steps)
3. Provision System Access (parallel step)
4. Schedule Orientation Session

```java
EmployeeOnboardingWorkflow workflow = new EmployeeOnboardingWorkflow();
workflow.run(ctx);
```

**Automatic sequence IDs** ensure step uniqueness—even with loops or retries.

---

## Logging

SLF4J logs workflow progress cleanly. Example:

```
INFO  [EmployeeOnboardingWorkflow] - Creating employee record...
INFO  [EmployeeOnboardingWorkflow] - Assigning laptop & email for EMP-001
INFO  [EmployeeOnboardingWorkflow] - Provisioning system access for EMP-001
INFO  [EmployeeOnboardingWorkflow] - Scheduling orientation session...
INFO  [DurableContext] - Workflow completed successfully
```

---

## Serialization

* Step results are serialized to JSON using **Jackson**.
* Supports storing and retrieving any return type safely.

---
## Sequence Diagram

<img width="1092" height="2246" alt="image" src="https://github.com/user-attachments/assets/167c4dd6-5083-4213-8e8f-d36dd955e29d" />


## Notes

* Loops, conditionals, and repeated steps are fully supported.
* Completed steps are **never re-executed**, providing full durability.
* Automatic Sequence IDs remove the need for manual step management.

