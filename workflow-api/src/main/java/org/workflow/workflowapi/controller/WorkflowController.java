package org.workflow.workflowapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.workflow.coremodels.dto.WorkflowSubmissionRequest;
import org.workflow.coremodels.model.WorkflowRun;
import org.workflow.coremodels.model.TaskRun;
import org.workflow.coremodels.repository.TaskRunRepository;
import org.workflow.coremodels.repository.WorkflowRunRepository;
import org.workflow.workflowapi.service.WorkflowService;

import java.util.*;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowRunRepository workflowRunRepository;
    private final TaskRunRepository taskRunRepository;

    public WorkflowController(WorkflowService workflowService,
                              WorkflowRunRepository workflowRunRepository,
                              TaskRunRepository taskRunRepository) {
        this.workflowService = workflowService;
        this.workflowRunRepository = workflowRunRepository;
        this.taskRunRepository = taskRunRepository;
    }

    @PostMapping("/submit")
    public ResponseEntity<WorkflowRun> submitWorkflow(@RequestBody WorkflowSubmissionRequest request) {
        WorkflowRun run = workflowService.submitWorkflow(request);
        return new ResponseEntity<>(run, HttpStatus.CREATED);
    }

    @GetMapping("/{runId}/status")
    public ResponseEntity<Map<String, Object>> getWorkflowStatus(@PathVariable UUID runId) {
        Optional<WorkflowRun> runOpt = workflowRunRepository.findById(runId);
        if (runOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        WorkflowRun run = runOpt.get();
        List<TaskRun> taskRuns = taskRunRepository.findByWorkflowRunId(runId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("workflowRunId", run.getId());
        response.put("workflowStatus", run.getStatus());
        response.put("startedAt", run.getStartedAt());

        List<Map<String, Object>> tasks = new ArrayList<>();
        for (TaskRun tr : taskRuns) {
            Map<String, Object> taskInfo = new LinkedHashMap<>();
            taskInfo.put("taskRunId", tr.getId());
            taskInfo.put("taskDefinitionId", tr.getTaskDefinitionId());
            taskInfo.put("status", tr.getStatus());
            taskInfo.put("retryCount", tr.getRetryCount());
            taskInfo.put("workerId", tr.getWorkerId());
            taskInfo.put("startedAt", tr.getStartedAt());
            taskInfo.put("completedAt", tr.getCompletedAt());
            taskInfo.put("errorMessage", tr.getErrorMessage());
            tasks.add(taskInfo);
        }
        response.put("tasks", tasks);
        return ResponseEntity.ok(response);
    }
}
