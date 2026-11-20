# InPay · Painel gerencial

Frontend React + TypeScript + Vite, com proxy Node de rotas restritas. Interface em português, responsiva, com demonstração explicitamente identificada e modo conectado às APIs existentes. Código formatado com Prettier, dividido em páginas, componentes, contratos e transporte.

## Executar com Docker

Na raiz do repositório:

```bash
docker compose up --build -d
```

Abra **http://localhost:3080**. O painel inicia em demonstração. Para usar os serviços locais, abra **Configurações**, mantenha o lojista local e selecione **Usar modo conectado** sem preencher token. O proxy local usa `LOCAL_PASSWORD` no servidor. Não exponha este modo à internet.

Somente a demonstração, sem iniciar PostgreSQL/Kafka/Java:

```bash
docker compose up --build -d inpay-panel
```

## Executar com Node

Requer Node 22.12+ ou 24 LTS e npm:

```bash
cd frontend
npm ci
npm run build
npm start
```

Abra http://127.0.0.1:8080. Para desenvolvimento, use `npm run dev` em outro terminal e configure `PANEL_ORIGINS=http://localhost:5173,http://127.0.0.1:5173` no processo do proxy. Para uma conexão local com serviços Java, configure `PANEL_AUTH_MODE=local` e `LOCAL_PASSWORD` explicitamente. Sem isso, não existe fallback para Basic.

## Funcionalidades e fronteiras

| Área | Entrega |
|---|---|
| Visão geral | KPIs, evolução do volume, meios, períodos e exportação sobre a mesma amostra demonstrativa. Sem métricas inventadas no modo conectado. |
| Transações | Busca, filtros, paginação, detalhes e CSV na demonstração; consulta real por UUID na projeção. |
| Links | Criação, consulta e revogação reais; confirmação antes de revogar. O backend não possui checkout público nem listagem. Registros consultados ficam nesta sessão de tela. |
| Antifraude | Simulação da política demo-rules-v1; no modo conectado, avaliação persistida pelo serviço. Não altera a transação. |
| Meios de pagamento | Formulários específicos dos oito connectors; ambientes, parâmetros, particularidades, referências oficiais e exportação de templates/exemplos. Não aplica configurações ao servidor. |
| Clientes, recebedores, assinaturas, equipe, contestações | Consulta e edição demonstrativas em memória, reiniciadas ao trocar de página. Não provisionam registros, permissões ou produtos reais. |
| Split | Simulação de divisão percentual preservando centavos e exportação. Não realiza liquidação. |
| Financeiro | Abas de recebíveis, extrato, conciliação e transferências demonstrativas. Não representa saldo real. |
| Roteamento | Proposta exportável de prioridade/estratégia, com validação de afiliações e bloqueio de retry UNKNOWN. Não altera tráfego. |
| Jornada | Mapa dos módulos e do estágio de implementação. Não é rastreamento ao vivo. |
| Desenvolvedores | Exemplo REST, referências, idempotência e fronteiras de webhooks/auditoria. |
| Configurações | Seleção de modo e contexto; access token em memória. Login interativo PKCE ainda pendente. |

Não há equivalência completa com Pagar.me: a referência orienta o inventário de interfaces, não atribui capacidades de PSP à InPay. Pix, boleto, vault, 3DS interativo, planos/recorrência, split, transferências, chargebacks, callbacks e relatórios agregados precisam de seus respectivos contratos e implementações. Não são habilitados simplesmente por um provedor oferecer o produto.

## Configuração do proxy

| Variável | Uso |
|---|---|
| `HOST`, `PORT` | Bind do servidor; defaults 127.0.0.1 e 8080. Docker usa 0.0.0.0 internamente e publica somente em loopback. |
| `PANEL_AUTH_MODE` | `local` habilita Basic de desenvolvimento explicitamente. Qualquer outro valor exige Bearer. |
| `LOCAL_PASSWORD` | Somente no servidor; não é entregue ao bundle/browser. |
| `PANEL_ORIGINS` | Origens exatas permitidas, separadas por vírgula; padrão localhost/127.0.0.1:3080. Ajustar ao endereço real. |
| `LINKS_URL` | Base do serviço de links. Local: http://127.0.0.1:8082. |
| `QUERY_URL` | Base da projeção. Local: http://127.0.0.1:8091. |
| `FRAUD_URL` | Base do antifraude. Local: http://127.0.0.1:8087. |

Fora de `local`, as URLs upstream exigem HTTPS. Configure TLS na entrada do painel. Os serviços Java continuam responsáveis por validar JWT, expiração, scopes e vínculo de `client_id` com o tenant. Não coloque client secret no navegador. O painel não cria tokens M2M nem configura Cognito. Tokens não são salvos em localStorage, cookies ou URLs, e são removidos ao encerrar/recarregar a sessão.

O proxy aceita apenas:

- `GET /api/transactions/:uuid` → `GET /v1/transactions/:uuid`;
- `POST /api/links` → `POST /v1/payment-links`;
- `GET /api/links/:uuid` → `GET /v1/payment-links/:uuid`;
- `POST /api/links/:uuid/revoke` → `POST /v1/payment-links/:uuid/revoke`;
- `POST /api/fraud` → `POST /internal/v1/assessments`.

Destinos não vêm do browser; redirects são rejeitados. Há limites de payload, timeout, validação de Origin e Sec-Fetch-Site, CSP e sanitização de erros. Nenhuma rota coleta PAN/CVV ou expõe operações financeiras diretas das adquirentes. Um bearer fornecido nunca é substituído por Basic após falha.

## Configuração das adquirentes

`src/data/providers.json` contém os parâmetros derivados dos `provider.yml` e perfis produtivos em `services/connector-*/src/main/resources`, com notas do contrato. Os campos de segredos não são editáveis: o template exporta marcadores para configuração no servidor. O indicador permanece “Não verificado”; exportar não prova conectividade ou homologação. Templates permanecem com `*_ENABLED=false`.

Bin requer certificado mTLS montado no connector. Global Payments exige confirmação de BR/BRL. Rede requer URL OAuth produtiva explicitamente informada no onboarding. Stone usa Host específico e a opção JVM documentada. Consulte [CONNECTORS.md](../docs/CONNECTORS.md) antes da implantação.

## Validação

```bash
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

A suíte cobre valores monetários, CSV, divisão, política de risco, allowlist, autenticação, isolamento de dados, contratos HTTP, navegação, formulários, downloads e layout móvel. Os cenários conectados de navegador utilizam respostas HTTP controladas; não substituem teste com PostgreSQL, Cognito e adquirentes. O ambiente de criação não possui Docker Engine, portanto Compose é validado estruturalmente.

## Referências

Consultadas em 19/11/2026:

- [Inventário de dashboard Pagar.me](https://docs.pagar.me/v1/docs/dashboard-pagarme) — referência histórica de áreas de gestão.
- [Meios de pagamento Pagar.me](https://docs.pagar.me/docs/meios-de-pagamento).
- [Split v5 Pagar.me](https://docs.pagar.me/reference/split-1) — regras específicas daquele produto, não aplicadas genericamente aos connectors.
- [Cielo: ambientes e autenticação](https://docs.cielo.com.br/ecommerce-cielo/reference/sobre-a-api).
- [PagBank: Orders](https://developer.pagbank.com.br/reference/criar-pedido).
- Demais referências oficiais estão no catálogo de provedores e em [CONNECTORS.md](../docs/CONNECTORS.md).

## Marketplace e portal do titular

As rotas `#marketplace` e `#my-credit` exibem administração e portal de compras, respectivamente.
No modo conectado, `MARKETPLACE_URL` aponta para o serviço isolado na porta 8110. APIs do gateway
não são usadas para armazenar saldos. Consulte `docs/MARKETPLACE.md` na raiz para seed local,
Cognito, dupla aprovação, estados de pedidos e limitações de integração real.
