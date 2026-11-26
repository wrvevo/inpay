package br.com.includelabs.inpay.painel.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {
            "br.com.includelabs.inpay.painel",
            "br.com.includelabs.inpay.security",
            "br.com.includelabs.inpay.openapi"
        })
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
