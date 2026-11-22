# InPlay Games — laboratório InPay

Loja PHP com 10 produtos fictícios, carrinho, checkout, pedidos, consulta ao provedor,
cancelamento e administração do catálogo. Assinaturas e gift cards são SKUs de demonstração:
não há entrega de licenças nem renovação automática neste checkout.

O PHP chama `/gateway/v1/journeys`; o Gateway persiste a jornada no PostgreSQL e chama uma
aplicação conectora via HTTP. O Gateway não depende do JAR Cielo. A loja não recebe PAN/CVV
nem credenciais do adquirente. O armazenamento JSON com lock é exclusivo do laboratório.
Esta implementação leve foi escolhida porque Magento requer banco, busca e outros serviços;
não é um módulo Magento nem substitui uma loja de produção.

## Rodar com Docker Compose

Requisitos: Docker Engine e Compose v2. Na raiz do repositório, em Bash:

```bash
export LOCAL_PASSWORD='escolha-uma-senha-local'
export STORE_ADMIN_PASSWORD='escolha-outra-senha-local'
export DEMO_CONNECTOR_AUTHORIZATION="$(python3 -c 'import os,base64; print("Basic "+base64.b64encode(("developer:"+os.environ["LOCAL_PASSWORD"]).encode()).decode())')"
docker compose -f docker-compose.yml -f docker-compose.demo.yml up --build -d
```

Loja: http://localhost:8099 ; administração: http://localhost:8099/?page=admin ;
painel geral: http://localhost:3080. A senha da loja é `STORE_ADMIN_PASSWORD`.
Os dados da loja ficam no volume `game-store-data`. O simulador mantém dados temporários;
reiniciá-lo perde seus pagamentos. Para recomeçar use novos pedidos; não interprete pedidos
antigos como conciliados após recriar o simulador. Não use `down -v` se precisar preservar dados.

O simulador aprova cartões e confirma Pix/boleto/transferência ao consultar. Seus códigos são
marcados `SIMULADO-NAO-PAGAVEL`; não representam uma liquidação bancária. Todas as portas
expostas por esta sobreposição ficam em loopback. A configuração Compose foi validada;
a subida completa por Docker não foi executada no ambiente de desenvolvimento desta entrega.

## Testes sem Docker

Requisitos: Java 21, Maven, Python 3, PHP 8.3+ e sistema compatível com PostgreSQL embarcado.
Execute como usuário comum (PostgreSQL não inicia como root):

```bash
python3 scripts/test-store.py
python3 scripts/test-store.py --live --env-file .env
```

O primeiro teste inicia PHP, Gateway Spring, PostgreSQL e um simulador HTTP temporários.
O segundo também empacota e inicia o conector Cielo em outro processo Java e testa somente
cartão de crédito: compra, consulta e cancelamento. Requer `CIELO_MERCHANT_ID`,
`CIELO_MERCHANT_KEY`, `CIELO_BASE_URL=https://apisandbox.cieloecommerce.cielo.com.br` e
`CIELO_QUERY_URL=https://apiquerysandbox.cieloecommerce.cielo.com.br` no arquivo ignorado `.env`.
Nunca versionar os valores. O teste recusa hosts de produção e usa cartão fictício.
`--maven` e `--php` aceitam caminhos de executáveis; argumentos Maven adicionais seguem `--`.

Para PHP isolado, configure `INPAY_URL`, `INPAY_AUTHORIZATION`, `STORE_ADMIN_PASSWORD` e
opcionalmente `STORE_DATA`, depois execute `php -S 127.0.0.1:8099 -t examples/game-store/public`.
Isso exige Gateway já em execução; a loja isolada não simula o pagamento.

## Configuração da jornada

| Variável | Finalidade |
|---|---|
| `INPAY_JOURNEY_MODE` | `disabled` por padrão; `local-simulator` ou `cielo-sandbox` |
| `INPAY_JOURNEY_CONNECTOR_URL` | URL interna da aplicação conectora |
| `INPAY_JOURNEY_AUTHORIZATION` | Credencial de serviço, nunca credencial bancária |
| `INPAY_JOURNEY_SANDBOX_FIXTURES` | Habilita cartão fictício apenas com perfil `local` |
| `STORE_MODE` | `local-simulator` ou `cielo-sandbox` |
| `INPAY_TENANT` | Tenant autorizado da loja |

O exemplo usa Basic exclusivamente no perfil local. Produção exige Cognito com escopos e
renovação de tokens M2M; a renovação automática deste novo cliente REST ainda não foi ligada.
Não habilitar fixtures fora do laboratório. O modo Cielo desta jornada limita os meios aceitos;
não confundir com as operações nativas adicionais do conector.

Idempotência: mantenha a chave ao repetir uma solicitação sem resposta. Estados `UNKNOWN`,
`PROCESSING` ou `CANCEL_UNKNOWN` exigem investigação/consulta, não outra cobrança. Recuperação
por chave encontra a jornada local, mas recuperar uma operação interrompida antes de persistir
o ID do adquirente ainda exige conciliação operacional. A loja só marca cancelado após confirmação.
