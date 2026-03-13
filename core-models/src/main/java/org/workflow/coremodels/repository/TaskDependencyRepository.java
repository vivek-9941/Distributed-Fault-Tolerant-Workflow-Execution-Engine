package org.workflow.coremodels.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.workflow.coremodels.model.TaskDependency;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskDependencyRepository extends JpaRepository<TaskDependency, Long> {
    List<TaskDependency> findByWorkflowId(UUID workflowId);
    List<TaskDependency> findByParentTaskId(String parentTaskId);
    List<TaskDependency> findByChildTaskId(String childTaskId);
}
