# WireMock central — operações bancárias e parceiros de risco

## Inventário verificado estaticamente
- 280 arquivos existentes (40 operações comuns para cada um dos sete bancos: C6, Itaú, Santander, BB, Bradesco, Caixa, Banrisul).
- 93 novos intents explícitos dos catálogos especializados: 58 C6 e 35 Itaú.
- 60 intents adicionais de cartão: 40 comuns (cinco operações em oito adquirentes) e 20 da coleção Cielo.
- 12 novos intents sintéticos de ClearSale e Serasa Experian: avaliação ACCEPT/REVIEW/REJECT, CUSTOMER_REVIEW, DOCUMENT_PENDING, PROVIDER_UNAVAILABLE.
- Nenhuma ausência ou duplicidade encontrada na conferência de nomes dos catálogos e novos mappings. Isso não comprova os contratos reais de provedor, nem a semântica de todos os fixtures antigos.

## Execução local (sem credenciais bancárias)
```sh
python3 scripts/verify-wiremock-intents.py
docker compose -p inpay -f docker-compose.yml -f docker-compose.banks.yml -f docker-compose.risk-mocks.yml config
docker compose -p inpay -f docker-compose.yml -f docker-compose.banks.yml -f docker-compose.risk-mocks.yml up --build -d
python3 scripts/verify-wiremock-intents.py --running
```

Execute o smoke após os serviços estarem saudáveis. Se a loja demo também for necessária, acrescente -f docker-compose.demo.yml antes do comando up; verifique config/ports.

Portas host: WireMock 8199, health do mock server 8198; bancos 8131–8137 (C6, Itaú, Santander, BB, Bradesco, Caixa e Banrisul). Gateway 8084; ClearSale 8128 e Serasa Experian 8129. Dentro do Compose os bancos mantêm portas 8111–8117; WireMock 8099. O overlay aponta o Gateway às URLs DNS internas. Não exponha WireMock/admin ou perfis local à internet.

X-Mock true com INPAY_INTENT_MOCK_URL configurado usa transporte HTTP interno /intents; nenhuma informação de cliente ou segredo é enviada. O código legado de mock direto ainda existe para testes unitários sem configuração; não há fallback após falha do WireMock configurado. Ausência/false segue caminho real, que fica desabilitado no laboratório. Não habilitar operações sem paths e contratos confirmados. Mocks especializados testam a intenção, não a serialização da requisição externa real.

## Pendências
- Os cenários cadastrais dos parceiros existem como fixtures no WireMock; falta integrar a API persistente de homologação, revisão humana e evidências. As APIs externas contratadas não estão disponíveis; não há aprovação cadastral real, nem ativação por resultado simulado.
- O endpoint local `/internal/v1/provider-mocks/{operation}` nos oito adquirentes usa `X-Mock: true` e não grava no journal financeiro. É um simulador de intenção, não o caminho nativo real, e não está conectado à saga do Gateway. O fluxo agregado de antifraude ainda precisa de integração com os parceiros.
- A branch remota consultada não contém as evoluções locais relatadas para upload documental, Accounts e provisionamento de loja. Integrar somente após push do Cursor e revisão de conflitos.
- Build Maven, Spotless, testes de frontend e runtime Compose não executados neste ambiente (clone Git indisponível por DNS). Não declarar homologação concluída ou merge seguro. Executar a validação e corrigir falhas antes de merge.

## Validação do PR #1
O CI confirmou o inventário de 445 mapeamentos (280 comuns bancários, 93 especializados C6/Itaú, 12 de risco, 60 de cartão). Os resultados de build e integração devem ser consultados no GitHub Actions para o SHA em revisão. A aprovação de laboratório no painel não provisiona Accounts; a submissão e decisão do backend de laboratório exigem atores distintos e bloqueiam edição enquanto em revisão. Não há importação real de documentos nesta implementação.
