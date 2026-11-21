# Proposta comercial InPay — 21/11/2026

Preços sugeridos de software, separados de MDR, liquidação, antifraude, mensageria e impostos. São propostas, não tarifas publicadas nem contratos ativos por padrão. Não representam média estatística: produtos e modelos de adquirência diferem.

## Referências públicas consultadas

| Referência | Valores observados | Comparabilidade |
|---|---|---|
| Vindi | Recorrência Essential R$299/mês, Pro R$499, Premium a partir de R$1.119, mais taxas | Software de recorrência e serviços financeiros |
| Asaas | Pix/boleto R$1,99 recebido; oferta R$0,99. Cartão à vista R$0,49 + 2,99%, oferta 1,99% por 3 meses | Inclui serviços financeiros; promoção não é custo permanente |
| iugu | Processamento R$0,40, boleto R$2,19, Pix 0,99%, cartão 3,34%; mensalidade sob consulta | Componentes cobrados separadamente, condições contratuais |

Fontes: https://vindi.com.br/precos/ ; https://www.asaas.com/precos-e-taxas ; https://www.iugu.com/saiba-mais . Revalidar antes da publicação comercial.

## Catálogo versionado proposto

| Plano | Mensalidade | Tarifa InPay por operação liquidada |
|---|---:|---:|
| Start | R$0 | R$0,49 |
| Growth | R$149 | R$0,35 |
| Scale | R$499 | R$0,25 |
| Enterprise | Sob proposta | Conforme volume e contrato |

IDs `START-2026-11`, `GROW-2026-11`, `SCALE-2026-11`. Com tarifas de software isoladas, Growth passa a ser mais econômico que Start a partir de aproximadamente 1.065 operações/mês; Scale versus Growth a partir de 3.501. Não são projeções de lucro.

A implementação registra a tarifa por liquidação confirmada, inclusive boleto. Não cobra emissão de boleto nem mensalidade automática; a mensalidade permanece catálogo comercial. A tarifa registrada fica limitada ao bruto menos custo de provedor para não gerar líquido negativo; revisar esta regra no contrato antes de ativar comercialmente.

Cliente com credenciamento próprio paga adquirente conforme seu contrato. Se InPay repassar custo de provedor, a fatura comercial deve discriminar os componentes. Start atende o modelo sem mensalidade, mas custo externo depende do parceiro; não prometer boleto de custo zero ao InPay.

Margem real precisa considerar infraestrutura, chargeback/fraude, suporte, tributos e custos de cobrança. O dashboard apresenta receita bruta da tarifa InPay, não lucro líquido.
