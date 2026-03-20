package com.noteops.agent;

import com.noteops.agent.config.AiProperties;
import com.noteops.agent.config.CaptureUrlProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableConfigurationProperties({AiProperties.class, CaptureUrlProperties.class})
public class NoteOpsAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(NoteOpsAgentApplication.class, args);
    }
}
