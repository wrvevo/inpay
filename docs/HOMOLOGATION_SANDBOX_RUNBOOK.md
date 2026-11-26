# Homologação persistente — ambiente de laboratório

Use o PR e o workflow verify como fonte de evidência; commits não equivalem a testes aprovados.

- Execute `bash scripts/docker-e2e.sh` em máquina Linux com Docker Compose, Maven disponível nos builds Docker, memória e disco suficientes. O script destrói somente os volumes do projeto isolado inpay-ci ao finalizar. Não utilize um projeto com dados reais.
- O painel em localhost:3080 usa os operadores locais PDV, backoffice e homologacao. Não use as contas de fixture em produção.
- Fluxo v2: cadastro, configuração de meios, quatro documentos, revisão independente, parceiros ClearSale e Serasa via mocks, aprovação cadastral, confirmação persistida Accounts, demo, evidências de conectores, revisão técnica. A loja em localhost:8099 recebe um identificador demo e restringe métodos; o checkout usa o simulador central, não o adquirente real. O Gateway mantém o tenant de laboratório; o isolamento por demo refere-se aos pedidos e configuração da vitrine.
- Documentos: AES-256-GCM no PostgreSQL, nonce por documento, contexto autenticado, hash, versionamento, auditoria de acesso, expiração e remoção do conteúdo. Para fixture, somente o PDF sintético exato é aceito; não representa aprovação antivírus. Para dados reais, configure chave segura de 32 bytes em Base64, identificador, retenção autorizada e scanner ClamAV em rede privada. Nunca versionar chaves. O scanner não é executado no teste sintético.
- Produção permanece bloqueada: integração oficial, credenciais/contratos, validação cadastral externa, KMS/rotação de chaves, infraestrutura e política de retenção/revisão LGPD, autorização de acesso real e certificação dos provedores precisam ser estabelecidos. Não há ativação automática real.
- Ações CI: Java/Spotless, testes frontend e Docker E2E. Resultados parciais não autorizam merge.
