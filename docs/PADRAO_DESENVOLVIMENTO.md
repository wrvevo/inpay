# Padrão de desenvolvimento InPay

## Legibilidade e documentação

- Java: 4 espaços, formatação do Spotless configurado no Maven e nomes de classes/métodos em inglês para preservar os contratos. JavaDoc em português nos métodos novos ou alterados: finalidade, regras, restrições, efeitos e erros relevantes. Use `@param`, `@return` e `@throws` quando esclarecem informações que a assinatura não oferece.
- TypeScript/JavaScript: 2 espaços, componentes e funções pequenas, blocos explícitos em fluxos de validação, nomes descritivos. Documente em português as responsabilidades e os limites de segurança.
- Python e PHP: 4 espaços, funções com propósito único, comentários ou docstrings em português. Nunca registrar tokens, segredos, PAN, CVV ou conteúdo de documentos.
- Evite comentários redundantes que apenas repetem uma linha de código. Explique as decisões de negócio, os contratos externos e os casos de falha. Mantenha a arquitetura hexagonal e separe infraestrutura de domínio.

## Validação antes do merge

```bash
mvn -B spotless:apply
mvn -B spotless:check verify
cd frontend && npm ci && npm test && npm run build && npm run test:e2e
```

Na raiz do repositório, execute `bash scripts/docker-e2e.sh` em Linux com Docker Compose. O script utiliza somente dados sintéticos e remove os volumes do projeto isolado `inpay-ci`. Não execute sobre uma instalação com dados reais.

O workflow `verify` deve confirmar Java, frontend e Docker E2E no SHA mais recente do PR. Não desabilite testes nem reduza a cobertura para obter sinal verde. Uma operação mockada não é homologação financeira real.
