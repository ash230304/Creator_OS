package com.creatoros;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * CreatorOS Backend — entry point.
 *
 * @EnableAsync enables Spring's @Async support for the video processing pipeline.
 * The async executor is configured in application.yml (spring.task.execution).
 */
@SpringBootApplication
@EnableAsync
public class CreatorOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreatorOsApplication.class, args);
    }
}
