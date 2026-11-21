package br.com.includelabs.inpay.payment.bootstrap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.includelabs.inpay.messaging.OutboxPublisher;
import br.com.includelabs.inpay.payment.domain.port.PaymentStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Verifica a documentação HTTP real gerada pelo Springdoc, sem depender de banco ou broker. */
@SpringBootTest(
        classes = Application.class,
        properties = {
            "spring.profiles.active=local",
            "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                    + "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration"
        })
@AutoConfigureMockMvc
class SwaggerDocumentationTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private PaymentStore paymentStore;
    @MockitoBean private JdbcTemplate jdbcTemplate;
    @MockitoBean private KafkaTemplate<String, String> kafkaTemplate;
    @MockitoBean private OutboxPublisher outboxPublisher;

    @Test
    void publishesInPayContractWithDocumentedEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("INPAY Gateway"))
                .andExpect(
                        jsonPath("$.paths['/v1/payments'].post.summary")
                                .value("Registrar pagamento idempotente"))
                .andExpect(
                        jsonPath("$.paths['/v1/payments/{id}/captures'].post.responses['501']")
                                .exists())
                .andExpect(
                        jsonPath("$.components.schemas.GatewayOrderResponse.properties.charges")
                                .exists())
                .andExpect(
                        jsonPath(
                                        "$.components.schemas.GatewayChargeResponse.properties.processing_status")
                                .exists())
                .andExpect(
                        jsonPath("$.components.securitySchemes.localBasic.scheme").value("basic"));
    }

    @Test
    void servesSwaggerUiInLocalProfile() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Swagger UI")));
    }

    @Test
    void publicDocumentationDoesNotMakeBusinessApiPublic() throws Exception {
        mockMvc.perform(get("/v1/payments/22222222-2222-2222-2222-222222222222"))
                .andExpect(status().isUnauthorized());
    }
}
