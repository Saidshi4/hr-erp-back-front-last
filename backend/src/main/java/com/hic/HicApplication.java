package com.hic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class HicApplication {
    public static void main(String[] args) {
        SpringApplication.run(HicApplication.class, args);
    }
}
