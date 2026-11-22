# Evidências de integração — 22/11/2026

## Execuções distintas

1. **Conector → Cielo sandbox**, executado em 21/11/2026: relatório sanitizado
   [cielo-sandbox-2026-11-21.json](cielo-sandbox-2026-11-21.json). A suíte concluiu, mas isso não
   significa que todas as funcionalidades bancárias foram aprovadas.
2. **Loja PHP → Gateway Spring/PostgreSQL → simulador HTTP**: os cinco métodos passaram por
   criação, consulta e cancelamento. Teste de replay confirmou ausência de segunda chamada de
   criação. É integração local real entre processos, com a resposta bancária simulada.
3. **Loja PHP → Gateway → conector Cielo em outro processo → Cielo sandbox**: compra de cartão
   de crédito com captura, consulta com `PAID` e cancelamento com `CANCELLED` confirmados em
   22/11/2026. Um teste executado, um teste exclusivo do simulador ignorado. Credenciais da
   homologação fornecidas pelo proprietário foram lidas do arquivo ignorado, sem publicação.

O teste real da loja encontrou e corrigiu incompatibilidade do envelope: `amount` no nível
superior da operação interna é exclusivo de captura/cancelamento; criação usa `Payment.Amount`.
Os testes não usam cartão real, não movimentam produção e não entregam produtos digitais.

## Resultado por capacidade Cielo

| Capacidade | Observação do sandbox |
|---|---|
| Autorização, consulta, captura e cancelamento | Sucesso, inclusive captura/cancelamento parcial e cancelamento por pedido |
| Cartão recusado | Recusa esperada observada |
| Recorrência | Criação, consulta, alterações, desativação e reativação responderam conforme relatório |
| Débito | Criação respondeu no teste nativo; não equivale a checkout 3DS completo |
| Boleto | Emissão respondeu; liquidação real não foi testada |
| BIN | Consulta respondeu |
| Zero Auth | HTTP 200, mas `Valid=false`, código 11; não considerar cartão validado |
| Tokenização | HTTP 400; consulta/pagamento por token bloqueados pela ausência de token |
| Transferência | HTTP 400, código 119 no cenário testado |
| Pix Cielo2 | Não executado: documentação informa indisponibilidade no sandbox |
| C6 e demais bancos | Não executado: conectores/credenciais/contratos pendentes |

O fluxo da loja usa fixture restrita ao perfil local porque a tokenização não foi aprovada.
Não promover essa configuração a produção. O relatório não contém MerchantId/MerchantKey,
PAN, CVV nem IDs de pagamento. Testes Kafka, webhooks de ponta a ponta, carga, split financeiro,
liquidação, estornos concorrentes reais e falhas distribuídas ainda precisam de uma etapa própria.

Reprodução: [README da loja](../../examples/game-store/README.md). XML JUnit e relatórios Maven
ficam em `target` e são recriados pelos comandos; não são evidência de homologação comercial.

## Regressão final

- Maven reactor `verify`: aprovado; módulos alterados revalidados após ampliar testes.
- Relatórios JUnit finais: **159 testes, zero falhas/erros/ignorados** na suíte local.
- Painel: build TypeScript/Vite e **11 testes Node aprovados**.
- Validação de idempotência conflitante, acesso sem autenticação e tenant incorreto na jornada.
- Accounts: 12 bancos desabilitados, criação idempotente, conflito e ambiente nulo rejeitado.
- PHP: lint dos dois arquivos aprovado. Compose: configuração base + demo válida.
- Draw.io: XML válido com 12 páginas.
- Inspeção visual por navegador não concluída: Chromium local bloqueado por restrição do ambiente.
  A navegação HTTP da loja/admin foi exercitada pelo E2E, sem afirmar revisão visual.
