# Durable Execution Engine

## Sequence Tracking
Each step internally increments an AtomicLong counter.
The step_key is constructed as:
    stepId + "_" + sequenceNumber

This ensures uniqueness even in loops and conditionals.

## Thread Safety
All SQLite operations are synchronized at the method level.
This prevents SQLITE_BUSY during parallel execution.

## Zombie Step Handling
We insert a RUNNING record before executing a step.
If a crash happens before completion:
- The step is not marked COMPLETED.
- On restart, it executes again safely.

## How to Run

Start workflow:
    java App

Simulate crash after Step 1:
    java App crash

Re-run:
    java App

Completed steps will not re-execute.



# Durable Execution Engine – Design Documentation

## 1. Sequence Tracking & Loop Handling

### Problem

In durable workflow systems, loops (e.g., retry mechanisms, iterative approvals, polling steps) can cause:

* Re-execution of already completed steps
* Duplicate side effects
* Infinite repetition after crash recovery

A durable engine must guarantee **idempotent replay**.

---

### Our Approach

The engine uses **persistent step recording** via SQLite.

Each step execution stores a record in the database containing:

* `workflow_id`
* `step_id`
* `status`
* `output`
* `timestamp`

This enables deterministic replay.

---

### How Loop Handling Works

When a workflow enters a loop:

1. Each iteration is assigned a unique `step_id`

   * Example:

     ```
     approval_step_1
     approval_step_2
     approval_step_3
     ```

2. Before executing a step, the engine checks:

   ```
   Does this step already exist in the steps table?
   ```

3. If the step status is `COMPLETED`:

   * Execution is skipped
   * Stored output is reused

4. If the step does not exist:

   * It is executed
   * Result is persisted

---

### Why This Works

Because execution is **state-driven**, not instruction-driven.

Even if a loop re-runs after a crash:

* Previously completed iterations are skipped
* Only incomplete iterations execute

This guarantees:

* Idempotency
* Crash safety
* Deterministic replay
* No duplicate side effects

---

## 2. Thread Safety During Parallel Execution

### Problem

Parallel workflows introduce risks:

* Race conditions
* Dirty reads
* Lost updates
* Corrupted state

A durable system must protect shared state and persistence.

---

### Thread Safety Mechanisms Used

#### 1️⃣ Database-Level Consistency (SQLite)

SQLite provides:

* ACID transactions
* Write locking
* Serialized writes

Each step persistence is executed inside a controlled database operation.

This guarantees:

* Atomic writes
* No partial updates
* Crash-safe commits

---

#### 2️⃣ Connection Isolation

Each workflow execution uses its own database connection instance.

This prevents:

* Shared mutable connection state
* Cross-thread interference

---

#### 3️⃣ Immutable Step Records

`StepRecord` objects are treated as immutable after creation.

This ensures:

* No concurrent mutation
* Safe read access across threads

---

#### 4️⃣ Controlled Execution Model

The durable context ensures:

* A step is marked `COMPLETED` only after successful execution
* Parallel threads cannot overwrite completed step states
* Reads always check persistent state before execution

This makes execution:

* Deterministic
* Replay-safe
* Thread-consistent

---

## Execution Guarantees

The engine provides:

* At-least-once execution
* Idempotent step replay
* Crash recovery
* Consistent persistence
* Safe parallel execution

---

## Summary

Loop handling is achieved using:

* Persistent step tracking
* Unique step identifiers per iteration
* Idempotent execution checks

Thread safety is ensured using:

* SQLite transactional guarantees
* Isolated connections
* Immutable step records
* Deterministic replay logic


Here’s a **ready-to-use README.md** for your Durable Execution Engine project, updated with **Automatic Sequence ID** and **SLF4J logging** info:

---

# Durable Execution Engine

## Overview

The **Durable Execution Engine** allows native Java workflows to become resilient and "durable". Workflows can be interrupted at any point (e.g., program crash, power loss) and, upon restart, resume from the exact point of failure without re-executing completed steps.

Inspired by systems like **Temporal**, **Cadence**, and **Azure Durable Functions**, this engine supports:

* Persistent step results
* Automatic step retry and sequence management
* Concurrent execution
* Thread-safe SQLite database writes

---

## Features

1. **Workflow Runner**

   * Starts or resumes a workflow.
   * Tracks step completion using an internal SQLite database.

2. **Step Primitive**

   * Generic step method: `<T> T step(Callable<T> action)`
   * Automatically checkpoints output to SQLite.
   * Completed steps are skipped on rerun.

3. **Automatic Sequence ID**

   * Developers no longer need to provide a manual step ID.
   * Each step is automatically assigned a unique sequence number via an `AtomicInteger`.
   * Example:

   ```java
   String employeeId = ctx.step(() -> createEmployee());
   ```

4. **Concurrency Support**

   * Steps can run concurrently using `CompletableFuture`.
   * SQLite writes are synchronized to prevent `SQLITE_BUSY` errors.

5. **Zombie Step Handling**

   * Steps stuck "in-progress" beyond a timeout are retried safely on workflow restart.

6. **Logging**

   * Uses **SLF4J** for logging instead of `System.out.println`.
   * Logs workflow progress, step start/end, and errors.

   Example:

   ```java
   private static final Logger logger = LoggerFactory.getLogger(EmployeeOnboardingWorkflow.class);

   logger.info("Creating employee record...");
   ```

7. **Persistence Layer**

   * SQLite database stores:

     * `workflow_id`
     * `step_key` (combination of step name and sequence)
     * `status` (`IN_PROGRESS` or `COMPLETED`)
     * `output` (JSON-serialized result)
     * `updated_at` timestamp

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
├─ examples/onboarding/    # Sample Employee Onboarding workflow
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

* The CLI allows you to **simulate a crash** by typing `exit` at any point.
* Rerun the program, and previously completed steps are **skipped automatically**.

---

## Example: Employee Onboarding Workflow

1. Create Employee Record
2. Provision Laptop & Email (parallel steps)
3. Provision System Access (parallel step)
4. Schedule Orientation Session

```java
EmployeeOnboardingWorkflow workflow = new EmployeeOnboardingWorkflow();
workflow.run(ctx);
```

**Automatic sequence IDs** ensure step uniqueness, even with loops or repeated steps.

---

## Concurrency & Thread-Safety

* All database writes are synchronized using `DurableContext`’s internal lock.
* `CompletableFuture` allows multiple steps to run concurrently without race conditions.
* `SQLiteStore` uses retry logic to handle `SQLITE_BUSY` errors.

---

## Logging

SLF4J logging is enabled. Example log output:

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
* Ensures any return type can be stored and retrieved safely.

---

## Notes

* Loops, conditionals, and repeated steps are fully supported.
* Completed steps are **never re-executed**, providing durability.
* Automatic Sequence IDs remove the need for manual step key management.

