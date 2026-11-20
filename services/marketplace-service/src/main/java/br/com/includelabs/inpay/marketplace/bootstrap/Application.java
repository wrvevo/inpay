package br.com.includelabs.inpay.marketplace.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Serviço opcional de marketplace; não compartilha dados nem regras com o gateway. */
@EnableScheduling
@SpringBootApplication(
        scanBasePackages = {
            "br.com.includelabs.inpay.marketplace",
            "br.com.includelabs.inpay.messaging",
            "br.com.includelabs.inpay.openapi"
        })
public class Application {
    /** Inicializa os módulos de contas, pedidos, aprovações e conciliação. */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
