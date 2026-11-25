# InPay Gateway para Magento

Módulo Magento 2 (`InPay_Gateway`) com todos os meios ativos por padrão. Cada meio chama `POST /gateway/v1/journeys`. A loja não recebe PAN nem CVV. Os stubs `X-Mock` dos conectores permanecem nos próprios conectores.

## Loja de demonstração já no ar com o Compose

A vitrine e o painel que acompanham o demo usam o mesmo catálogo de meios:

- Loja: http://localhost:8099
- Painel: http://localhost:8099/admin (senha `STORE_ADMIN_PASSWORD`)
- Swagger do gateway: http://localhost:8084/swagger-ui/index.html

## Instalar numa loja Magento Open Source

Copie esta pasta para `app/code/InPay/Gateway` e habilite:

```bash
bin/magento module:enable InPay_Gateway
bin/magento setup:upgrade
bin/magento cache:flush
```

No ambiente do PHP da loja, defina `INPAY_URL`, `INPAY_AUTHORIZATION` (Basic do gateway local) e `INPAY_TENANT`. O painel Magento lista os meios em Lojas → Configuração → Vendas → Formas de pagamento. O padrão de cada meio é habilitado.
