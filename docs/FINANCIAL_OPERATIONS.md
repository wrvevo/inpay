# InPay — operações financeiras e cobrança

Entrega de 21/11/2026. A separação de quatro produtos permanece: Gateway (incluindo billing-service), Accounts, Marketplace e Credits. Billing não mantém conta bancária, não libera crédito e não substitui o razão de Credits.

## Implementado

| Área | Comportamento disponível |
|---|---|
| Painel financeiro | Visão global restrita ao time InPay, clientes, faturas, recorrências, custos, compromissos a pagar, comunicações e planos; paginação e CSV |
| Homologação | Solicitação com evidência e aprovação por identidade diferente; não equivale a KYC bancário/regulatório |
| Cobranças | Faturas únicas; condições em centavos/basis points; desconto por data, multa única, juros simples com teto |
| Recorrência | Geração mensal de faturas, dia âncora, lote SKIP LOCKED, unicidade por assinatura/período, cancelamento futuro |
| Conciliação operacional | Ingestão interna de liquidação, comparação do valor contratado, deduplicação de evento/operação, custos imutáveis e outbox atômico |
| Custos | Valor bruto, custo do provedor, tarifa InPay, plano aplicado, instante e referência de depósito; tarifa somente após confirmação |
| Comunicação | Agendamento com referência de consentimento/template; PENDING_PROVIDER até integração de entrega |
| ERP | REST de importação e SOAP 1.1 CreateInvoice com WSDL; vínculo de tenant, idempotência e parser sem DTD/entidades externas |
| Split | Simulação por método, valores fixos e percentuais do restante, maior resto para preservar centavos; persistência de alocações existente |
| Resiliência | Circuit breaker por instância de conector, limite de concorrência, timeout e retry GET limitado; nenhuma repetição automática POST/PUT/DELETE |
| Roteamento | Política de domínio considera bandeira, método, credenciamento/token e circuito; não habilita execução de rotas por si só |

## Execução local

`docker compose up --build -d billing-service inpay-panel`

Billing usa porta 8113 e banco/usuário `billing_service`; painel em 3080, menu **Time InPay → Gestão financeira**. Swagger: `http://localhost:8113/swagger-ui/index.html`. Use `LOCAL_PASSWORD` do ambiente. Perfil local possui developer (operação), reviewer (aprovação) e provider-worker (eventos). Nunca habilitar esse perfil em produção.

A criação automática do banco em `infra/local/init-databases.sql` só ocorre no primeiro boot do volume PostgreSQL. Volumes existentes requerem provisionar `billing_service` sem apagar dados. Migrações Flyway executam na inicialização.

`BILLING_SCHEDULER_ENABLED=false` por padrão. Ao ativar, gera até `BILLING_CYCLE_BATCH=100` faturas por minuto. Ciclos atrasados são recuperados em lotes, sem débito automático.

## Autorização

- JWT Cognito validado: issuer, assinatura, expiração, token_use e client_id conforme configuração existente.
- `billing/read` e `billing/write`: usuário humano + grupo `inpay-finance`.
- `billing/approve`: usuário humano + grupo `inpay-finance-reviewer`; solicitante não pode aprovar seu próprio cadastro.
- `billing/events`: client_credentials sem username + client_id em `BILLING_EVENT_CLIENTS`. Endpoint interno não está disponível no proxy do painel.
- `billing/erp`: vínculo de tenant do client_id aplicado pelo TenantContext. `X-Merchant-Id` não concede acesso a outro tenant.
- Configurar os grupos e scopes no Cognito antes da publicação. Terraform foi atualizado, não aplicado nesta entrega.

## APIs

Base `/billing/v1`: `GET dashboard`, `GET resources/{type}`, `POST merchants`, `POST merchants/{id}/approve`, `POST invoices`, `POST subscriptions`, `POST subscriptions/{id}/cancel`, `POST cycles/run`, `POST payables`, `POST reminders`, `GET cashflow`, `GET retry-advice` e `POST internal/settlements`.

Comandos usam `operationId` UUID com comparação de ator/payload. Mesma chave com conteúdo diferente gera conflito. Settlement usa `(provider,eventId)` e unicidade de `(provider,providerOperation)`; o consumidor autorizado deve primeiro verificar o evento/consultar o provedor. Este endpoint não aceita webhook público diretamente.

ERP: `POST /billing/v1/erp/invoices`, `POST /billing/v1/erp/soap`, `GET /billing/v1/erp/billing.wsdl`. WSDL inicial suporta condições financeiras zero. Configure a URL do serviço no cliente ERP. Não há adaptadores certificados para SAP, Oracle, Dynamics ou TOTVS nesta entrega.

## Cobertura de meios de pagamento

| Conector | Adição | Limite desta entrega |
|---|---|---|
| Cielo | Payload nativo Pix/Cielo2, expiração, retorno copia-e-cola | Contrato local testado; nova integração Pix indisponível no sandbox segundo documentação |
| PagBank | Pedido Pix e boleto, retorno QR/código de barras | Contratos locais testados; credenciamento/homologação externa pendentes |
| Rede, Getnet, Stone, Bin, Global Payments, Safrapay | Resiliência compartilhada | Pix não implementado nesses adaptadores; não anunciados como suportados |

Referências: https://docs.cielo.com.br/ecommerce-cielo/docs/pix-cielo ; https://docs.cielo.com.br/ecommerce-cielo/reference/qrcode-pix ; https://developer.pagbank.com.br/reference/criar-pedido-com-qr-code-pix-v2 ; https://developer.pagbank.com.br/reference/criar-pagar-pedido-com-boleto . Exemplos em `provider-examples` dos conectores. Atualizar datas de expiração antes de usar.

Os adaptadores aceitam payload nativo; a saga normalizada Gateway → cobrança → provedor → liquidação ainda precisa ser concluída/homologada. QR emitido/WAITING não significa pago. Artefatos QR/boleto não são persistidos no journal mínimo; reconsultar a operação após replay. Não armazenar PAN/CVV no painel, eventos ou logs.

## Pendências explícitas para operação comercial

- Executor de débito recorrente com mandato/token, emissão externa automática de faturas/links e integração dos eventos reais ao billing.
- Adaptadores de envio SMS/e-mail/WhatsApp e régua de inadimplência; o agendamento implementado não envia mensagens.
- Automação de execução de split por parceiro e configuração persistente de regras por método. Não há promessa de participantes ilimitados: contrato/API/volume impõem limites.
- Liquidação bancária e estornos/chargebacks com lançamentos compensatórios; o ledger atual registra confirmação de recebimento, não contabilidade fiscal completa.
- Aplicativo NFC: fontes de protótipo em `apps/inpay-tap`, dependente de PSP/SDK; sem processamento, build mobile ou homologação.
- Subadquirência, BaaS/Pix próprio, operações internacionais e licenças: dependem de contratos, habilitações e análise jurídica. IP/SCD da Include **não verificadas**; não apresentadas como licença emprestada a todos os serviços.
- Juros/multa no cartão bloqueados: exigem suporte do provedor, autorização e condições contratuais específicas. Nenhuma alegação de exclusividade de mercado.
- Teste de carga e dimensionamento: proteções de concorrência não constituem garantia de TPS/SLA. Medir latência p95/p99, lag Kafka, pool de conexões, outbox, erros e recuperação antes de definir capacidade.

Não foram executadas transações reais, homologação de Pix externo, implantação de infraestrutura ou envio de mensagens nesta entrega.
