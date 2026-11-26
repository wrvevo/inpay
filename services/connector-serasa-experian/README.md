# connector-serasa-experian

Porta 8129.

Conector de antifraude Serasa Experian. `X-Mock: true` (ou cabeçalho ausente) devolve o stub `serasa-experian-mock-v1`. Com `X-Mock: false` e o conector desligado, a resposta é 503. A consulta real à Serasa não está habilitada.
