package org.workflow.coremodels.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "task_definitions")
public class TaskDefinition {

    @Id
    private String id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private TaskType taskType;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    @Column(name = "max_retries")
    private Integer maxRetries;

    @Column(name = "command", columnDefinition = "TEXT")
    private String command;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum TaskType {
        CLONE, BUILD, TEST, REPORT
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public TaskDefinition() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(UUID workflowId) {
        this.workflowId = workflowId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(TaskType taskType) {
        this.taskType = taskType;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
