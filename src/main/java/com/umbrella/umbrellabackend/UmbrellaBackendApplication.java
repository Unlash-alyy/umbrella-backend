package com.umbrella.umbrellabackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.umbrella.umbrellabackend.mapper")
public class UmbrellaBackendApplication {
	public static void main(String[] args) {
		SpringApplication.run(UmbrellaBackendApplication.class, args);
	}
}