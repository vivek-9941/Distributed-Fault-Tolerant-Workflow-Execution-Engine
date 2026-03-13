package org.workflow.coremodels.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "task_logs")
public class TaskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_run_id", nullable = false)
    private UUID taskRunId;

    @Column(name = "log_content", columnDefinition = "TEXT")
    private String logContent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public TaskLog() {}

    public Long getId() { return id; }
    
    public UUID getTaskRunId() { return taskRunId; }
    public void setTaskRunId(UUID taskRunId) { this.taskRunId = taskRunId; }

    public String getLogContent() { return logContent; }
    public void setLogContent(String logContent) { this.logContent = logContent; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
