package org.workflow.coremodels.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.workflow.coremodels.model.TaskRun;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRunRepository extends JpaRepository<TaskRun, UUID> {
    List<TaskRun> findByWorkflowRunId(UUID workflowRunId);
    List<TaskRun> findByStatus(TaskRun.Status status);
}
