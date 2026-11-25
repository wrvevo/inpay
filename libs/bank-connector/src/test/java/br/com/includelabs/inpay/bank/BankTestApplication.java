package br.com.includelabs.inpay.bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {"br.com.includelabs.inpay.bank", "br.com.includelabs.inpay.security"})
public class BankTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(BankTestApplication.class, args);
    }
}
