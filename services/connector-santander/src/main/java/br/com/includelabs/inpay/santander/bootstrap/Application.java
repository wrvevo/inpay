package br.com.includelabs.inpay.santander.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {
            "br.com.includelabs.inpay.santander",
            "br.com.includelabs.inpay.security"
        })
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
