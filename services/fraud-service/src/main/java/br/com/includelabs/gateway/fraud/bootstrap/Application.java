package br.com.includelabs.gateway.fraud.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@EnableScheduling
@SpringBootApplication(scanBasePackages={"br.com.includelabs.gateway.fraud", "br.com.includelabs.gateway.security", "br.com.includelabs.gateway.messaging"})
public class Application { public static void main(String[] args) { SpringApplication.run(Application.class,args); } }
