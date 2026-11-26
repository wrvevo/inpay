# connector-clearsale

Porta 8128.

Conector de antifraude ClearSale. `X-Mock: true` (ou cabeçalho ausente) devolve o stub `clearsale-mock-v1`. Com `X-Mock: false` e o conector desligado, a resposta é 503. A chamada real à ClearSale não está habilitada.
