# Distributed Fault-Tolerant CI/CD Workflow Execution Engine

## Abstract
The Distributed Fault-Tolerant CI/CD Workflow Execution Engine is a microservices-based system designed to execute dynamic, DAG-based CI/CD pipelines in a scalable and resilient manner. It allows users to submit workflows describing sequences of tasks (e.g., repository cloning, compilation, testing, and report generation) along with their dependencies. Instead of hardcoding pipeline logic, workflows are defined dynamically and processed through an event-driven architecture that ensures flexible scheduling and parallel execution.

This engine mimics enterprise distributed compute engines like Apache Airflow, Netflix Conductor, and Uber Cadence—handling true distributed systems challenges including graph scheduling, back-pressure management, exactly-once mitigation, and both infrastructure and application resilience.

## Core Features
*   **Dynamic DAG Execution**: Analyzes Directed Acyclic Graphs (DAGs) using Depth-First Search (DFS) for acyclic validity, scheduling tasks in topological order based on "in-degree" reduction.
*   **Event-Driven & Loosely Coupled**: Uses Apache Kafka as the event backbone. This strictly decouples orchestration from compute-heavy worker execution, preventing CPU and memory starvation of the scheduler logic.
*   **Resilience & Self-Healing**: Detects stalled or failed tasks via a centralized database and last-heartbeat timeouts. Failed tasks are retried via exponential backoff; irreparably failed tasks are routed to a Dead Letter Queue (DLQ).
*   **Exactly-Once Execution Semantics**: Eliminates duplicate execution by mandating atomic database transactions paired with Kafka offset commits. 
*   **Highly Concurrent**: Kubernetes (HPA) automatically scales worker pods based on Kafka consumer lag. Within instances, workers use `ThreadPoolTaskExecutor` to execute processes without blocking the Kafka consumer stream.

## System Architecture

The architecture distinguishes "Infrastructure Healing" (Kubernetes pod restarts) from "Logical Healing" (Orchestrator reassignment and duplicate prevention). It consists of three main microservices:

### 1. Workflow API Service
*   **Responsibility**: The REST entry point. Validates initial DAG payload, saves basic metadata and edge relationships (parent-child nodes), starts a unified database transaction, and kicks off the workflow by emitting `workflow.started`.
*   **Rule**: Performs NO scheduling, execution, or retry handling.

### 2. Orchestrator Service
*   **Responsibility**: The central "brain". Evaluates the DAG, calculates in-degree requirements, and moves zero-requirement tasks into `task.ready`. Listens for `task.completed` or `task.failed` events to unlock dependents, orchestrate task retries, timeout zombie tasks, and record workflow state.
*   **Rule**: Does NOT run heavy tasks like shell or system commands. 

### 3. Worker Service
*   **Responsibility**: The scalable execution layer. Listens for `task.ready`. Executes operations (e.g., `git`, `mvn`) through Java's `ProcessBuilder`. Implements real-time asynchronous stream gobblers to capture logs (stdout/stderr). Emits completion/failure metrics back to Kafka.

## Technology Stack
*   **Backend & Concurrency**: Java 17+, Spring Boot, ExecutorService
*   **Messaging**: Apache Kafka, Spring Kafka
*   **Database**: PostgreSQL
*   **Containerization & Orchestration**: Docker, Kubernetes (HPA, StatefulSets)
*   **Observability & Telemetry**: Prometheus, Grafana, Spring Actuator

## Database Entities (PostgreSQL)
The engine maintains transactional state to power distributed consistency:
*   `workflows` / `workflow_runs`: DAG definitions and execution run instances.
*   `task_definitions` / `task_dependencies`: Task configurations and parent-child edges.
*   `task_runs` / `task_logs`: Highly volatile execution logs and states tracking `PENDING` $\rightarrow$ `READY` $\rightarrow$ `RUNNING` $\rightarrow$ `SUCCESS`/`FAILED`.
*   `workers`: Tracks actively registered worker instances and their health checks.

## Event Backbone (Kafka Topics)
Kafka represents the system's nervous system:
1.  `workflow.started`: Triggers Orchestrator to unpack the DAG.
2.  `task.ready`: Dispatches unblocked tasks to Workers.
3.  `task.completed`: Emitted by Workers to inform the Orchestrator to unblock children.
4.  `task.failed`: Emitted by Workers to trigger orchestrated retries.
5.  `task.dlq`: Terminal queue for tasks over maximum configured retries.

## Getting Started: Implementation Phases

### Phase 1: Pure Local
*   Run API, Orchestrator, and Workers locally via IDE.
*   Use Docker Compose for purely stateful infrastructure: Kafka and Postgres.

### Phase 2: Local Kubernetes
*   Package services with Docker. Deploy components using Helm charts onto **Minikube** or **Kind**.
*   Test Horizontal Pod Autoscaler scaling worker instances against high Kafka volume lag over simulated large DAG workflows.

### Phase 3: Cloud Deployment
*   Deploy to managed systems (AWS EKS or GCP GKE).
*   Add Prometheus and Grafana dashboards tracking real-time metrics including *Task Latency*, *Consumer Lag*, *Success Rate*, and *Worker Heartbeat*.

## Deployment Demonstration Scenario
1.  Submit hundreds of parallel workflow requests simultaneously via the REST API.
2.  Observe Kafka consumer lag spike.
3.  Kubernetes automatically detects lag through metrics and scales `Worker Service` replicas via HPA up to Kafka partition maximum counts.
4.  Simulate hardware failure by purposefully terminating a working pod mid-execution. 
5.  Watch the `Orchestrator Service` intelligently timeout the zombie task and re-queue it seamlessly without duplicate operations.
