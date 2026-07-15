package com.refarch.flakyexternalservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FlakyExternalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlakyExternalServiceApplication.class, args);
    }
}
