# Homologação C6

O overlay `docker-compose.c6-sandbox.yml` ativa o conector em um host isolado de homologação. Ele mantém o endereço `https://baas-api-sandbox.c6bank.info` fixo e usa a porta local `127.0.0.1:8131` do Compose bancário. Os demais ambientes continuam com a configuração demonstrativa.

## Configuração

- Obtenha o `C6_CLIENT_ID` e o `C6_CLIENT_SECRET` emitidos para o sandbox e confirme com o C6 qual contrato OpenAPI se aplica à conta.
- Aponte `C6_CERT_FILE` para o certificado PEM e `C6_PKCS8_KEY_FILE` para a chave privada em PKCS#8 PEM. O anexo validado usa outro formato. Faça a conversão para um arquivo protegido fora do checkout com `openssl pkcs8 -topk8 -nocrypt -in <origem> -out <destino>`.
- Configure `C6_PIX_KEY` somente quando houver uma chave de teste atribuída pelo C6. Injete os valores autorizados pelo Secrets Manager quando existir um serviço de homologação na AWS.
- Suba o conector com `docker compose -f docker-compose.banks.yml -f docker-compose.c6-sandbox.yml up --build connector-c6`. Para validar apenas a estrutura, acrescente `config --quiet` no lugar de `up --build connector-c6`. Evite imprimir a configuração resolvida ou os valores do OAuth.

## Sequência de testes

1. Confira a saúde do conector e liste o catálogo interno com uma identidade autorizada.
2. Valide a autenticação mTLS e OAuth no sandbox. Registre apenas o resultado, a data e o código HTTP.
3. Use uma operação de consulta confirmada no contrato do banco e fixtures de homologação. Exercite também a resposta a credencial inválida e indisponibilidade.
4. Com o contrato e os dados sintéticos confirmados, teste o fluxo do gateway até o resultado do conector, incluindo idempotência. Mantenha as rotas ainda não verificadas bloqueadas.

Nesta revisão, foi validada localmente a correspondência do certificado com a chave. O endpoint respondeu HTTP 403 a uma sondagem sem credenciais. A autenticação e os testes de ponta a ponta com o banco aguardam as credenciais OAuth de sandbox e o contrato aplicável.
