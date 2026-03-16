package org.workflow.workflowapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workflow.coremodels.dto.TaskDefinitionDTO;
import org.workflow.coremodels.dto.WorkflowSubmissionRequest;
import org.workflow.coremodels.model.Workflow;
import org.workflow.coremodels.model.WorkflowRun;
import org.workflow.coremodels.repository.*;
import org.workflow.workflowapi.service.KafkaProducerService;
import org.workflow.workflowapi.service.WorkflowService;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WorkflowServiceTest {

    @Mock private WorkflowRepository workflowRepository;
    @Mock private WorkflowRunRepository workflowRunRepository;
    @Mock private TaskDefinitionRepository taskDefinitionRepository;
    @Mock private TaskDependencyRepository taskDependencyRepository;
    @Mock private TaskRunRepository taskRunRepository;
    @Mock private KafkaProducerService kafkaProducerService;

    @InjectMocks
    private WorkflowService workflowService;

    @Test
    void submitWorkflow_ValidDag_Success() {
        // Arrange
        TaskDefinitionDTO task1 = new TaskDefinitionDTO("task1", "BUILD", "echo 1", 300, 3, null);
        TaskDefinitionDTO task2 = new TaskDefinitionDTO("task2", "TEST", "echo 2", 300, 3, Collections.singletonList("task1"));
        
        WorkflowSubmissionRequest request = new WorkflowSubmissionRequest(
                "My Valid Workflow",
                "test-user",
                Arrays.asList(task1, task2)
        );

        Workflow mockWorkflow = new Workflow();
        mockWorkflow.setId(java.util.UUID.randomUUID());
        
        WorkflowRun mockRun = new WorkflowRun();
        mockRun.setId(java.util.UUID.randomUUID());

        when(workflowRepository.save(any(Workflow.class))).thenReturn(mockWorkflow);
        when(workflowRunRepository.save(any(WorkflowRun.class))).thenReturn(mockRun);

        // Act
        assertDoesNotThrow(() -> workflowService.submitWorkflow(request));

        // Assert
        verify(workflowRepository).save(any());
        verify(workflowRunRepository).save(any());
    }

    @Test
    void submitWorkflow_CyclicDependency_ThrowsException() {
        // Arrange
        // task1 -> task2 -> task3 -> task1
        TaskDefinitionDTO task1 = new TaskDefinitionDTO("task1", "BUILD", "echo 1", 300, 3, Collections.singletonList("task3"));
        TaskDefinitionDTO task2 = new TaskDefinitionDTO("task2", "TEST", "echo 2", 300, 3, Collections.singletonList("task1"));
        TaskDefinitionDTO task3 = new TaskDefinitionDTO("task3", "REPORT", "echo 3", 300, 3, Collections.singletonList("task2"));

        WorkflowSubmissionRequest request = new WorkflowSubmissionRequest(
                "My Cyclic Workflow",
                "test-user",
                Arrays.asList(task1, task2, task3)
        );

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> workflowService.submitWorkflow(request));
            
        assertTrue(exception.getMessage().contains("Cyclic dependency detected involving task"));
        
        // Ensure no DB saves occurred
        verify(workflowRepository, never()).save(any());
        verify(workflowRunRepository, never()).save(any());
    }

    @Test
    void submitWorkflow_SelfReferencingDependency_ThrowsException() {
        // Arrange
        TaskDefinitionDTO task1 = new TaskDefinitionDTO("task1", "BUILD", "echo 1", 300, 3, Collections.singletonList("task1"));

        WorkflowSubmissionRequest request = new WorkflowSubmissionRequest(
                "My Self Cyclic Workflow",
                "test-user",
                Collections.singletonList(task1)
        );

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> workflowService.submitWorkflow(request));
            
        assertTrue(exception.getMessage().contains("Cyclic dependency detected involving task: task1"));
    }

    @Test
    void submitWorkflow_MissingDependency_ThrowsException() {
        // Arrange
        TaskDefinitionDTO task1 = new TaskDefinitionDTO("task1", "BUILD", "echo 1", 300, 3, Collections.singletonList("non-existent-task"));

        WorkflowSubmissionRequest request = new WorkflowSubmissionRequest(
                "Workflow With Missing Dep",
                "test-user",
                Collections.singletonList(task1)
        );

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> workflowService.submitWorkflow(request));
            
        assertTrue(exception.getMessage().contains("Dependency 'non-existent-task' not found for task 'task1'"));
    }
}
