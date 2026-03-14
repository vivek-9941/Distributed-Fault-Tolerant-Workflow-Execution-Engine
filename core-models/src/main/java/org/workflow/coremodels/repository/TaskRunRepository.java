package org.workflow.coremodels.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.workflow.coremodels.model.TaskRun;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRunRepository extends JpaRepository<TaskRun, UUID> {
    List<TaskRun> findByWorkflowRunId(UUID workflowRunId);
    List<TaskRun> findByStatus(TaskRun.Status status);
    List<TaskRun> findByWorkflowRunIdAndStatus(UUID workflowRunId, TaskRun.Status status);
    List<TaskRun> findByStatusAndLastHeartbeatBefore(TaskRun.Status status, LocalDateTime threshold);
    Optional<TaskRun> findByWorkflowRunIdAndTaskDefinitionId(UUID workflowRunId, String taskDefinitionId);

    @Modifying
    @Query("UPDATE TaskRun t SET t.status = :newStatus, t.workerId = :workerId, t.startedAt = :startedAt, t.lastHeartbeat = :heartbeat WHERE t.id = :id AND t.status = :expectedStatus")
    int claimTask(@Param("id") UUID id, @Param("newStatus") TaskRun.Status newStatus, @Param("workerId") String workerId, @Param("startedAt") LocalDateTime startedAt, @Param("heartbeat") LocalDateTime heartbeat, @Param("expectedStatus") TaskRun.Status expectedStatus);
}
