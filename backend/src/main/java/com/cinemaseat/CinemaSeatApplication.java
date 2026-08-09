package com.cinemaseat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CinemaSeatApplication {
    public static void main(String[] args) {
        SpringApplication.run(CinemaSeatApplication.class, args);
    }
}