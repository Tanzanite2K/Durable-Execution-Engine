package engine;

public class StepRecord {

    private final String workflowId;
    private final String stepId;
    private final String status;
    private final String output;
    private final long updatedAt;

    public StepRecord(String workflowId,
                      String stepId,
                      String status,
                      String output,
                      long updatedAt) {
        this.workflowId = workflowId;
        this.stepId = stepId;
        this.status = status;
        this.output = output;
        this.updatedAt = updatedAt;
    }

    public String getWorkflowId() { return workflowId; }
    public String getStepId() { return stepId; }
    public String getStatus() { return status; }
    public String getOutput() { return output; }
    public long getUpdatedAt() { return updatedAt; }
}
