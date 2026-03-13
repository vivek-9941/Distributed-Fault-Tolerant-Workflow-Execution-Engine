package org.workflow.workflowapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.workflow.coremodels.dto.WorkflowSubmissionRequest;
import org.workflow.coremodels.model.WorkflowRun;
import org.workflow.workflowapi.service.WorkflowService;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping("/submit")
    public ResponseEntity<WorkflowRun> submitWorkflow(@RequestBody WorkflowSubmissionRequest request) {
        WorkflowRun run = workflowService.submitWorkflow(request);
        return new ResponseEntity<>(run, HttpStatus.CREATED);
    }
}
