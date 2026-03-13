package org.workflow.coremodels.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.workflow.coremodels.model.TaskDefinition;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskDefinitionRepository extends JpaRepository<TaskDefinition, String> {
    List<TaskDefinition> findByWorkflowId(UUID workflowId);
}
