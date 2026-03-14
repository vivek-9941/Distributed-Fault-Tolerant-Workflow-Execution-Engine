package org.workflow.workflowapi.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workflow.coremodels.dto.TaskDefinitionDTO;
import org.workflow.coremodels.dto.WorkflowSubmissionRequest;
import org.workflow.coremodels.event.WorkflowStartedEvent;
import org.workflow.coremodels.model.*;
import org.workflow.coremodels.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final TaskDefinitionRepository taskDefinitionRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final TaskRunRepository taskRunRepository;
    private final KafkaProducerService kafkaProducerService;

    public WorkflowService(WorkflowRepository workflowRepository,
                           WorkflowRunRepository workflowRunRepository,
                           TaskDefinitionRepository taskDefinitionRepository,
                           TaskDependencyRepository taskDependencyRepository,
                           TaskRunRepository taskRunRepository,
                           KafkaProducerService kafkaProducerService) {
        this.workflowRepository = workflowRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.taskDefinitionRepository = taskDefinitionRepository;
        this.taskDependencyRepository = taskDependencyRepository;
        this.taskRunRepository = taskRunRepository;
        this.kafkaProducerService = kafkaProducerService;
    }

    @Transactional
    public WorkflowRun submitWorkflow(WorkflowSubmissionRequest request) {
        // 1. Create Workflow Definition
        Workflow workflow = new Workflow();
        workflow.setName(request.workflowName());
        workflow.setVersion("1.0"); // Statically set for demonstration
        workflow = workflowRepository.save(workflow);

        // 2. Create Workflow Run
        WorkflowRun workflowRun = new WorkflowRun();
        workflowRun.setWorkflowId(workflow.getId());
        workflowRun.setStatus(WorkflowRun.Status.PENDING);
        workflowRun.setTriggeredBy(request.triggeredBy());
        workflowRun = workflowRunRepository.save(workflowRun);

        // 3. Process Task Definitions, Dependencies, and Task Runs
        if (request.tasks() != null) {
            for (TaskDefinitionDTO taskDto : request.tasks()) {
                TaskDefinition taskDef = new TaskDefinition();
                taskDef.setId(taskDto.id());
                taskDef.setWorkflowId(workflow.getId());
                taskDef.setName(taskDto.id());
                taskDef.setTaskType(TaskDefinition.TaskType.valueOf(taskDto.type().toUpperCase()));
                taskDef.setTimeoutSeconds(taskDto.timeoutSeconds() != null ? taskDto.timeoutSeconds() : 300);
                taskDef.setMaxRetries(taskDto.maxRetries() != null ? taskDto.maxRetries() : 3);
                taskDef.setCommand(taskDto.command() != null ? taskDto.command() : "echo No command specified");
                taskDefinitionRepository.save(taskDef);

                // Dependencies
                if (taskDto.dependsOn() != null) {
                    for (String parentId : taskDto.dependsOn()) {
                        TaskDependency dep = new TaskDependency();
                        dep.setWorkflowId(workflow.getId());
                        dep.setParentTaskId(parentId);
                        dep.setChildTaskId(taskDto.id());
                        taskDependencyRepository.save(dep);
                    }
                }

                // Initial Task Run created as PENDING
                TaskRun taskRun = new TaskRun();
                taskRun.setWorkflowRunId(workflowRun.getId());
                taskRun.setTaskDefinitionId(taskDef.getId());
                taskRun.setStatus(TaskRun.Status.PENDING);
                taskRunRepository.save(taskRun);
            }
        }

        // 4. Publish workflow.started event
        kafkaProducerService.publishWorkflowStarted(new WorkflowStartedEvent(
                workflowRun.getId(),
                workflow.getId(),
                workflowRun.getTriggeredBy()
        ));

        return workflowRun;
    }
}
