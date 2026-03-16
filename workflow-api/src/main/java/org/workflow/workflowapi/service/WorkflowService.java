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
        // 0. Validate DAG for Cycles before proceeding
        validateDag(request);

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

    /**
     * Validates that the submitted tasks form a valid Directed Acyclic Graph (DAG)
     * by performing a Depth-First Search (DFS) for cycle detection.
     */
    private void validateDag(WorkflowSubmissionRequest request) {
        if (request.tasks() == null || request.tasks().isEmpty()) {
            return;
        }

        // Build adjacency list: TaskId -> List of dependent task IDs
        java.util.Map<String, java.util.List<String>> adjacencyList = new java.util.HashMap<>();
        
        // Ensure all tasks exist in the map
        for (TaskDefinitionDTO task : request.tasks()) {
            adjacencyList.putIfAbsent(task.id(), new java.util.ArrayList<>());
        }

        for (TaskDefinitionDTO task : request.tasks()) {
            if (task.dependsOn() != null) {
                for (String parentId : task.dependsOn()) {
                    if (!adjacencyList.containsKey(parentId)) {
                        throw new IllegalArgumentException("Dependency '" + parentId + "' not found for task '" + task.id() + "'");
                    }
                    // Add edge from parent to current task
                    adjacencyList.get(parentId).add(task.id());
                }
            }
        }

        // Track states for cycle detection
        java.util.Set<String> visiting = new java.util.HashSet<>();
        java.util.Set<String> visited = new java.util.HashSet<>();

        for (String taskId : adjacencyList.keySet()) {
            if (hasCycle(taskId, adjacencyList, visiting, visited)) {
                throw new IllegalArgumentException("Cyclic dependency detected involving task: " + taskId);
            }
        }
    }

    private boolean hasCycle(String taskId, java.util.Map<String, java.util.List<String>> graph,
                             java.util.Set<String> visiting, java.util.Set<String> visited) {
        if (visiting.contains(taskId)) return true; // Cycle detected
        if (visited.contains(taskId)) return false; // Already processed

        visiting.add(taskId);
        for (String neighbor : graph.getOrDefault(taskId, java.util.Collections.emptyList())) {
            if (hasCycle(neighbor, graph, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(taskId);
        visited.add(taskId);

        return false;
    }
}
