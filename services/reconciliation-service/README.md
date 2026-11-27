# reconciliation-service

O endpoint interno `POST /internal/v1/reconciliation/run` exige o escopo `reconciliation/run` e resolve o lojista a partir da identidade autenticada. O job compara registros, sem movimentar fundos.

O PostgreSQL armazena os registros importados em `reconciliation_entries`. A origem `EXPECTED` representa o movimento esperado no InPay. A origem `SETTLED` representa a linha do extrato de liquidação. A carga autorizada deve fornecer lojista, provedor, data comercial, referência, origem, valor em unidades mínimas da moeda e código de moeda. Há uma entrada por referência e origem no mesmo recorte. O contrato de importação de cada provedor depende da fonte contratada.

O adaptador compara valor e moeda e sinaliza referências que aparecem em apenas um lado. Ele persiste `MATCHED`, `DIFFERENCES` ou `NO_DATA` com as contagens para um único caso por lojista, provedor e data comercial. Uma nova execução recalcula o mesmo caso.

Configure porta, banco, provedor, fuso e janela de consulta em `application.yml` e nas variáveis correspondentes. A janela padrão é o dia anterior em `America/Sao_Paulo`. Consulte [Configuração AWS do InPay](../../docs/AWS_RUNTIME.md) para os nomes dos segredos e a configuração da Lambda.
