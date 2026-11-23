package com.bagu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.bagu.mapper")
public class BaguApplication {

    public static void main(String[] args) {
        SpringApplication.run(BaguApplication.class, args);
    }
}
