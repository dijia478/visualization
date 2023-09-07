package com.dijia478.visualization;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class VisualizationApplication {

	public static void main(String[] args) {
		SpringApplication.run(VisualizationApplication.class, args);
	}

}
