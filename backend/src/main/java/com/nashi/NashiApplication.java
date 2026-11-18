package com.nashi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NashiApplication {

    public static void main(String[] args) {
        SpringApplication.run(NashiApplication.class, args);
    }
}
