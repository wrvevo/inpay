# Manual dos módulos InPay

O seletor **Aplicação** troca a área de trabalho. A busca encontra páginas de todos os módulos aos quais a conta tem acesso. Na hospedagem demonstrativa, o painel trabalha com amostras fictícias. Nas instalações com backend, as páginas conectadas consultam as APIs do ambiente.

## Áreas do painel

| Módulo | Objetivo | Como usar |
| --- | --- | --- |
| Gateway | Acompanhar o ciclo de pagamentos | Abra **Visão geral** para indicadores, **Transações** para filtrar ou consultar registros e **Links de pagamento** para gerir links. **Jornada do pagamento** mostra as etapas e os componentes envolvidos. Consulte **Desenvolvedores** para contratos e idempotência. |
| Risco e integração | Avaliar sinais de fraude e configurar integrações | Use **Antifraude** para avaliações, **Gestão InRisk** para acompanhar as políticas e **Roteamento** para revisar prioridades. **ClearSale** e **Serasa Experian** mostram a configuração de seus provedores. |
| Accounts | Organizar contas financeiras, bancos e clientes | Abra **Contas financeiras e bancos** para revisar conexões. **Meios e adquirentes** oferece formulários e modelos de configuração. **Financeiro** organiza extrato, recebíveis e conciliação. |
| Marketplace | Administrar vendedores e divisão de valores | Use **Vendedores e repasses** para acompanhar a operação comercial, **Recebedores** para organizar participantes e **Split de pagamentos** para simular a divisão. |
| Credits | Acompanhar benefícios e uso de créditos | Use **Créditos e benefícios** para a visão administrativa e **Meu crédito e compras** para a jornada do cliente. |
| Billing | Organizar a gestão financeira interna | Abra **Gestão financeira** com uma conta autorizada para revisar os fluxos de cobrança. |
| Homologação de lojas | Acompanhar cadastro, envio e aprovação de uma loja | Selecione **Homologação** ao entrar. Revise o cadastro, acompanhe a submissão e use os papéis de revisão disponíveis. A jornada demonstrativa usa informações fictícias. |
| Equipe e preferências | Delegar acessos e administrar a sessão | **Equipe e permissões** define os menus visíveis. **Meus dados** reúne o perfil e **Configurações** ajusta o contexto da sessão. |

Assinaturas, contestações, split, repasses e outras telas demonstrativas podem conter fluxos simulados. Verifique o contrato e os serviços disponíveis antes de considerar uma operação concluída externamente.

## Serviços de backend

| Serviço | Responsabilidade |
| --- | --- |
| `painel-gerencial` | Identidade, permissões e jornada de homologação do painel. |
| `merchant-service` | Modelo e portas do cadastro de lojistas. |
| `payment-links-service` | Criação, consulta, expiração e revogação de links. |
| `checkout-service` | Modelo da sessão de checkout e portas para execução da jornada. |
| `payment-core-service` | Registro e idempotência de pagamentos e publicação de eventos. |
| `routing-service` | Política de seleção de provedor e tratamento de resultados incertos. |
| `token-service` | Geração e revogação de aliases locais. |
| `fraud-service` | Coleta de sinais, avaliação do risco e regras do InRisk. |
| `transaction-query-service` | Projeção de eventos de pagamento e consultas por lojista. |
| `webhook-ingress-service` | Contrato de entrada e normalização de callbacks. |
| `reconciliation-service` | Contratos e pontos de integração para conciliação. |
| `notification-service` | Modelo de notificações e assinatura de mensagens. |
| `accounts-service` | Cadastro e gestão de contas e configurações bancárias. |
| `marketplace-service` | Cadastro e operações comerciais de vendedores e participantes. |
| `credits-service` | Gestão de créditos, benefícios e consumo. |
| `billing-service` | Gestão de cobrança interna do InPay. |
| `connector-cielo`, `connector-rede`, `connector-getnet`, `connector-stone`, `connector-bin`, `connector-global-payments`, `connector-pagbank`, `connector-safrapay` | Adaptadores de adquirentes. Cada conector tem configuração e contrato próprios. |
| `connector-c6`, `connector-itau`, `connector-santander`, `connector-bb`, `connector-bradesco`, `connector-caixa`, `connector-banrisul` | Adaptadores bancários para os produtos liberados pelo banco e pelo ambiente. O procedimento do C6 está em [Homologação C6](C6_SANDBOX.md). |
| `connector-clearsale`, `connector-serasa-experian` | Adaptadores dos provedores de risco. |
| `bank-mock-server` | Simulador de contratos bancários utilizado em testes isolados. |

## Infraestrutura de apoio

- As bibliotecas compartilhadas definem contratos, transporte de conectores, mensageria, segurança, OpenAPI e recursos comuns.
- As Lambdas oferecem pontos de entrada para expiração de links, encaminhamento de callbacks e disparo de conciliação. A execução depende das funções do backend habilitadas para o ambiente.
- O [guia de acessos](../infra/dbeaver/README.md) contém os endereços e perfis do DBeaver. A [arquitetura](architecture/README.md) descreve a integração entre os serviços.
