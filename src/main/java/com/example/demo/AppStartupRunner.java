package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class AppStartupRunner {

	@Autowired
    private  KafkaConsumerService kafkaConsumerService;

    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
            // Start the listen method in a new thread to avoid blocking
             kafkaConsumerService.listen();
        };
    }
}
