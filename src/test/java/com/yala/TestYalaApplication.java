package com.yala;

import org.springframework.boot.SpringApplication;

public class TestYalaApplication {

    public static void main(String[] args) {
        SpringApplication.from(YalaApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
