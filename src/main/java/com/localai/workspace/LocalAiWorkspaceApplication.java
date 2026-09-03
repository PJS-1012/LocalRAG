package com.localai.workspace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LocalAiWorkspaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalAiWorkspaceApplication.class, args);
    }
}
