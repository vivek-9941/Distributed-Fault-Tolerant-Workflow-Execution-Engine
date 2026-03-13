package org.workflow.coremodels.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "task_dependencies")
public class TaskDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "parent_task_id", nullable = false)
    private String parentTaskId;

    @Column(name = "child_task_id", nullable = false)
    private String childTaskId;

    public TaskDependency() {
    }

    public Long getId() {
        return id;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(UUID workflowId) {
        this.workflowId = workflowId;
    }

    public String getParentTaskId() {
        return parentTaskId;
    }

    public void setParentTaskId(String parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    public String getChildTaskId() {
        return childTaskId;
    }

    public void setChildTaskId(String childTaskId) {
        this.childTaskId = childTaskId;
    }
}
