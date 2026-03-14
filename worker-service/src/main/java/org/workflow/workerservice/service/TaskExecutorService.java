package org.workflow.workerservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Executes shell commands via ProcessBuilder.
 * Implements async stream gobblers to prevent buffer deadlocks.
 */
@Service
public class TaskExecutorService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutorService.class);

    public TaskExecutionResult executeCommand(String command, int timeoutSeconds) {
        log.info("Executing command: {}", command);
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        try {
            ProcessBuilder processBuilder;
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                processBuilder = new ProcessBuilder("/bin/sh", "-c", command);
            }
            processBuilder.redirectErrorStream(false);

            Process process = processBuilder.start();

            // Async stream gobblers to prevent buffer deadlock
            Thread stdoutGobbler = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append("\n");
                    }
                } catch (Exception e) {
                    log.warn("Error reading stdout: {}", e.getMessage());
                }
            }, "stdout-gobbler");

            Thread stderrGobbler = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append("\n");
                    }
                } catch (Exception e) {
                    log.warn("Error reading stderr: {}", e.getMessage());
                }
            }, "stderr-gobbler");

            stdoutGobbler.start();
            stderrGobbler.start();

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                return new TaskExecutionResult(false, "Command timed out after " + timeoutSeconds + "s",
                        stdout.toString(), stderr.toString());
            }

            // Wait for gobblers to finish
            stdoutGobbler.join(5000);
            stderrGobbler.join(5000);

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("Command completed successfully. Exit code: 0");
                return new TaskExecutionResult(true, null, stdout.toString(), stderr.toString());
            } else {
                String errorMsg = "Command failed with exit code: " + exitCode;
                log.error("{}. Stderr: {}", errorMsg, stderr.toString().trim());
                return new TaskExecutionResult(false, errorMsg, stdout.toString(), stderr.toString());
            }

        } catch (Exception e) {
            log.error("Exception executing command: {}", e.getMessage(), e);
            return new TaskExecutionResult(false, "Exception: " + e.getMessage(),
                    stdout.toString(), stderr.toString());
        }
    }

    public record TaskExecutionResult(
            boolean success,
            String errorMessage,
            String stdout,
            String stderr
    ) {}
}
