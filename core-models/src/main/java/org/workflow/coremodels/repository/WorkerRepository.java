package org.workflow.coremodels.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.workflow.coremodels.model.Worker;

@Repository
public interface WorkerRepository extends JpaRepository<Worker, String> {
}
