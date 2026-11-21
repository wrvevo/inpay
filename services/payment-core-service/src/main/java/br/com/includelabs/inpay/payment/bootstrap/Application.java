package br.com.includelabs.inpay.payment.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada do módulo InPay; limita a varredura aos pacotes deste serviço e às bibliotecas
 * de infraestrutura.
 */
@EnableScheduling
@SpringBootApplication(
        scanBasePackages = {
            "br.com.includelabs.inpay.payment",
            "br.com.includelabs.inpay.security",
            "br.com.includelabs.inpay.messaging",
            "br.com.includelabs.inpay.openapi",
            "br.com.includelabs.inpay.resources"
        })
public class Application {

    /**
     * Inicializa o serviço Spring Boot com as configurações do ambiente ativo.
     *
     * @param args argumentos de inicialização do processo Java
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
