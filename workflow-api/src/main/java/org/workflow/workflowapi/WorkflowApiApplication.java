package org.workflow.workflowapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "org.workflow.coremodels.model")
@EnableJpaRepositories(basePackages = "org.workflow.coremodels.repository")
public class WorkflowApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(WorkflowApiApplication.class, args);
	}

}
