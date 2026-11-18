# Ambiente local completo

## Inicialização

Na raiz do projeto, executar `docker compose up --build -d`. O arquivo padrão é docker-compose.yml; não existe um segundo compose.yml com configurações concorrentes.

O Compose espera o PostgreSQL e o Kafka responderem aos healthchecks. O job kafka-init cria tópicos de eventos e suas DLTs. Serviços de aplicação esperam essa criação terminar. O job observability-init prepara a credencial de scrape antes do Prometheus.

Os três containers Lambda utilizam a emulação oficial AWS. Eles não criam agendamentos locais nem chamam adquirentes reais. O usuário deve invocá-los manualmente quando quiser testar.

## Verificações e diagnóstico

```bash
docker compose ps -a
docker compose logs --tail=100 kafka-init
docker compose logs --tail=100 payment-core-service
python3 scripts/check-services.py
```

No Windows, use `python scripts/check-services.py`. Esse script verifica health e documentação dos 19 serviços; não envia pagamentos.

O comando `docker compose config --quiet` valida configuração, mas não sobe containers. A disponibilidade das imagens, a execução dos processos e os recursos da máquina só são confirmados pelo ambiente Docker real.

## Subir um subconjunto

```bash
docker compose up --build -d payment-core-service token-service fraud-service transaction-query-service
```

O Compose inicia também as dependências declaradas. Grafana e demais módulos só entram se selecionados ou se usar `up` sem lista. Uma aplicação Spring isolada também pode rodar via JAR com profile local, mas nesse caso configure OpenTelemetry se desejar traces.

## Persistência e senhas

Os nomes de bancos e usuários foram mantidos. init-databases.sql só é executado em volume PostgreSQL vazio. Mudar o script não migra automaticamente uma base existente; use migrations ou comandos administrativos explícitos.

As senhas de banco no SQL são exemplos locais. LOCAL_PASSWORD configura Basic e scrape; GRAFANA_PASSWORD configura o primeiro login do Grafana. Se o volume Grafana já existir, alterar a variável não redefine automaticamente a senha do usuário já criado.

Recrie os containers após alterar .env: `docker compose up -d --force-recreate`. Não enviar .env com segredos ao git. Credenciais de desenvolvimento jamais devem ser usadas na AWS.

## Observabilidade

A imagem Spring inclui OpenTelemetry Java Agent 2.22.0 e envia traces OTLP HTTP ao Collector. Métricas vêm de Actuator/Prometheus. O agente não recebe configuração para capturar corpos ou headers Authorization. O Collector exporta traces em nível básico para logs; persistência/consulta de traces é uma extensão futura.

## Recursos e parada

Recomendação inicial: 16 GB de RAM e 4 CPUs disponíveis ao Docker. SERVICE_MEMORY_LIMIT ajusta o limite por serviço Spring; Lambdas têm limite local de 512 MB. Se a inicialização falhar por memória, aumente recursos ou suba um subconjunto. O primeiro build requer internet.

`docker compose down` preserva os dados; `down -v` remove os volumes e deve ser usado apenas quando quiser apagar o ambiente local.
