import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Verificação de saúde do container sem depender de curl ou executar operações de negócio. */
public final class Healthcheck {
    /**
     * Consulta o endpoint de saúde e devolve código de processo diferente de zero em caso de falha.
     *
     * @param args endereço local do endpoint Actuator health
     * @throws Exception se a conexão não puder ser concluída no prazo
     */
    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(args[0]))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
        int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        System.exit(status == 200 ? 0 : 1);
    }
}
