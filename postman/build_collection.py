import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MERCHANT = "11111111-1111-1111-1111-111111111111"
PIX_KEY = "071246db-0e8f-46c6-90a5-649514f264ca"
CARD_TOKEN = "ptok_EXEMPLO_SIMULADO_1234567890"


def header(key, value):
    return {"key": key, "value": value, "type": "text"}


def auth(username):
    return {
        "type": "basic",
        "basic": [
            {"key": "username", "value": username, "type": "string"},
            {"key": "password", "value": "{{localPassword}}", "type": "string"},
        ],
    }


def script(lines):
    return {"listen": "test", "script": {"type": "text/javascript", "exec": lines}}


def expect(*codes, save=None):
    joined = ", ".join(str(code) for code in codes)
    lines = [
        f"pm.test('HTTP {joined}', function () {{",
        f"    pm.expect([{joined}]).to.include(pm.response.code);",
        "});",
    ]
    if save:
        lines += [
            "if (pm.response.code < 300) {",
            "    const body = pm.response.json();",
            save,
            "}",
        ]
    return [script(lines)]


def request(name, method, url, body=None, headers=None, tests=None, username=None, description=""):
    headers = list(headers or [])
    if body is not None and not any(item["key"].lower() == "content-type" for item in headers):
        headers.insert(0, header("Content-Type", "application/json"))
    item = {
        "name": name,
        "request": {
            "method": method,
            "header": headers,
            "url": url,
            "description": description,
        },
    }
    if body is not None:
        item["request"]["body"] = {
            "mode": "raw",
            "raw": json.dumps(body, ensure_ascii=False, indent=2),
            "options": {"raw": {"language": "json"}},
        }
    if username:
        item["request"]["auth"] = auth(username)
    if tests:
        item["event"] = tests
    return item


def merchant_headers(*extra):
    return [header("X-Merchant-Id", "{{merchantId}}"), *extra]


def idempotent(*extra):
    return merchant_headers(header("Idempotency-Key", "{{$guid}}"), *extra)


def bank(operation, description, kafka=False):
    headers = idempotent(header("X-Mock", "true"))
    if kafka:
        headers.append(header("X-Dispatch", "kafka"))
    payload = {
        "id": "abc123",
        "txid": "abc123",
        "e2eid": "abc123",
        "chave": "{{pixKey}}",
        "data": "2026-11-23",
        "valor": "10.00",
        "amount": 1000,
        "external_reference_id": "pedido123",
    }
    expected = (202,) if kafka else (200,)
    return request(
        operation + (" via Kafka" if kafka else ""),
        "POST",
        "{{gatewayUrl}}/gateway/v1/bank-operations/c6/" + operation,
        payload,
        headers,
        expect(*expected),
        description=description,
    )


def folder(name, description, items):
    return {"name": name, "description": description, "item": items}


pix_order = {
    "code": "pedido-pix",
    "customer_id": "{{customerId}}",
    "items": [
        {"code": "jogo", "description": "Jogo digital", "amount": 10000, "quantity": 1}
    ],
    "payments": [{"payment_method": "pix", "amount": 10000, "pix": {"expires_in": 300}}],
}
card_order = {
    "code": "pedido-cartao",
    "customer_id": "{{customerId}}",
    "items": [
        {"code": "assinatura", "description": "Assinatura mensal", "amount": 4990, "quantity": 1}
    ],
    "payments": [
        {
            "payment_method": "credit_card",
            "amount": 4990,
            "credit_card": {"card_token": CARD_TOKEN, "installments": 1, "capture": False},
        }
    ],
}
conditions = {
    "fineBps": 0,
    "dailyInterestBps": 0,
    "discountCents": 0,
    "discountUntil": None,
    "maxSurchargeBps": 0,
}

collection = {
    "info": {
        "name": "InPay Gateway — checkout, recorrência, conciliação e pagamentos",
        "description": (
            "Collection para o Collection Runner local. Importe também inpay-local.postman_environment.json. "
            "Suba payment-core (8084), payment-links (8082), billing (8113), marketplace (8112), "
            "reconciliation (8089) e connector-c6 (8121) com o perfil local, Postgres e Kafka. "
            "Auth Basic developer / local-password. Aprovação de recorrência usa reviewer. "
            "Liquidação interna usa provider-worker. Operações C6 saem com X-Mock: true. "
            "Captura, estorno e o job de conciliação respondem 501 enquanto o adaptador não está ligado. "
            "Valores em centavos. Não envie PAN, CVV nem client secret."
        ),
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "auth": auth("developer"),
    "variable": [
        {"key": "gatewayUrl", "value": "http://127.0.0.1:8084"},
        {"key": "linksUrl", "value": "http://127.0.0.1:8082"},
        {"key": "billingUrl", "value": "http://127.0.0.1:8113"},
        {"key": "marketplaceUrl", "value": "http://127.0.0.1:8112"},
        {"key": "reconciliationUrl", "value": "http://127.0.0.1:8089"},
        {"key": "localPassword", "value": "inpay@123"},
        {"key": "merchantId", "value": MERCHANT},
        {"key": "pixKey", "value": PIX_KEY},
        {"key": "customerId", "value": ""},
        {"key": "orderId", "value": ""},
        {"key": "chargeId", "value": ""},
        {"key": "paymentId", "value": ""},
        {"key": "journeyId", "value": ""},
        {"key": "journeyKey", "value": ""},
        {"key": "linkId", "value": ""},
        {"key": "billingMerchantId", "value": ""},
        {"key": "invoiceId", "value": ""},
        {"key": "subscriptionId", "value": ""},
        {"key": "recipientId", "value": ""},
        {"key": "splitId", "value": ""},
        {"key": "settlementId", "value": ""},
    ],
    "item": [
        folder(
            "01 Pagamentos",
            "Clientes, pedidos, cobranças, pagamento tokenizado e jornada. Rode esta pasta antes das demais.",
            [
                request(
                    "Saúde do gateway",
                    "GET",
                    "{{gatewayUrl}}/actuator/health",
                    headers=[],
                    tests=expect(200),
                    username=None,
                    description="Sem autenticação.",
                ),
                request(
                    "Listar provedores",
                    "GET",
                    "{{gatewayUrl}}/gateway/v1/providers",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                request(
                    "Criar cliente",
                    "POST",
                    "{{gatewayUrl}}/gateway/v1/customers",
                    {
                        "name": "Cliente Homologacao",
                        "email": "cliente@example.com",
                        "type": "individual",
                        "document": "12345678901",
                    },
                    idempotent(),
                    expect(201, save='pm.collectionVariables.set("customerId", body.id);'),
                ),
                request(
                    "Listar clientes",
                    "GET",
                    "{{gatewayUrl}}/gateway/v1/customers?page=1&size=20",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                request(
                    "Obter cliente",
                    "GET",
                    "{{gatewayUrl}}/gateway/v1/customers/{{customerId}}",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                request(
                    "Criar pedido Pix",
                    "POST",
                    "{{gatewayUrl}}/gateway/v1/orders",
                    pix_order,
                    idempotent(),
                    expect(
                        202,
                        save=(
                            'pm.collectionVariables.set("orderId", body.id);\n'
                            '    if (body.charges && body.charges.length) pm.collectionVariables.set("chargeId", body.charges[0].id);'
                        ),
                    ),
                    description="202 pending / not_dispatched. A soma dos pagamentos precisa fechar com os itens.",
                ),
                request(
                    "Criar pedido com cartão tokenizado",
                    "POST",
                    "{{gatewayUrl}}/gateway/v1/orders",
                    card_order,
                    idempotent(),
                    expect(202),
                    description="Aceita somente card_token ptok_. PAN e CVV são recusados.",
                ),
                request(
                    "Listar pedidos",
                    "GET",
                    "{{gatewayUrl}}/gateway/v1/orders?page=1&size=20",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                request(
                    "Obter pedido",
                    "GET",
                    "{{gatewayUrl}}/gateway/v1/orders/{{orderId}}",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                request(
                    "Listar cobranças",
                    "GET",
                    "{{gatewayUrl}}/gateway/v1/charges?page=1&size=20",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                request(
                    "Obter cobrança",
                    "GET",
                    "{{gatewayUrl}}/gateway/v1/charges/{{chargeId}}",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                request(
                    "Registrar pagamento tokenizado",
                    "POST",
                    "{{gatewayUrl}}/v1/payments",
                    {
                        "merchantOrderId": "PED-1048",
                        "amount": 15990,
                        "currency": "BRL",
                        "instrumentToken": CARD_TOKEN,
                    },
                    idempotent(),
                    expect(202, save='pm.collectionVariables.set("paymentId", body.id);'),
                    description="202 CREATED. Esta versão não autoriza o cartão.",
                ),
                request(
                    "Consultar pagamento",
                    "GET",
                    "{{gatewayUrl}}/v1/payments/{{paymentId}}",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                request(
                    "Iniciar jornada Pix",
                    "POST",
                    "{{gatewayUrl}}/gateway/v1/journeys",
                    {
                        "merchantOrder": "order-pix",
                        "amount": 1500,
                        "method": "pix",
                        "instrument": "fixture",
                    },
                    idempotent(),
                    expect(
                        200,
                        save=(
                            'pm.collectionVariables.set("journeyId", body.id);\n'
                            '    const key = pm.request.headers.get("Idempotency-Key");\n'
                            '    pm.collectionVariables.set("journeyKey", key);'
                        ),
                    ),
                ),
                request(
                    "Consultar jornada",
                    "GET",
                    "{{gatewayUrl}}/gateway/v1/journeys/{{journeyId}}",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                request(
                    "Recuperar jornada pela chave",
                    "GET",
                    "{{gatewayUrl}}/gateway/v1/journeys/by-key/{{journeyKey}}",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                request(
                    "Cobrança Pix imediata C6",
                    "POST",
                    "{{gatewayUrl}}/gateway/v1/bank-operations/c6/PIX_COB_PUT",
                    {"txid": "abc123", "valor": "10.00", "chave": "{{pixKey}}"},
                    idempotent(header("X-Mock", "true")),
                    expect(200),
                    description="Mock do conector. Sem X-Mock o conector desligado responde 503.",
                ),
                bank(
                    "PIX_COB_PUT",
                    "Mesma cobrança publicada em inpay.c6.commands.v1. Exige Kafka e C6_GATEWAY_KAFKA=true.",
                    kafka=True,
                ),
            ],
        ),
        folder(
            "02 Checkout",
            "Link de pagamento do gateway e checkout C6. O checkout-service ainda não expõe sessão pública.",
            [
                request(
                    "Criar link de pagamento",
                    "POST",
                    "{{linksUrl}}/v1/payment-links",
                    {
                        "merchantOrderId": "PED-CHECKOUT-1",
                        "amount": 15990,
                        "expiresAt": "2027-06-01T23:59:59Z",
                    },
                    merchant_headers(),
                    expect(200, save='pm.collectionVariables.set("linkId", body.id);'),
                ),
                request(
                    "Consultar link",
                    "GET",
                    "{{linksUrl}}/v1/payment-links/{{linkId}}",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                request(
                    "Revogar link",
                    "POST",
                    "{{linksUrl}}/v1/payment-links/{{linkId}}/revoke",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                bank("CHECKOUT_CREATE", "POST /v1/checkouts/ no sandbox C6, em mock."),
                bank("CHECKOUT_GET", "GET /v1/checkouts/{id}. O mock devolve a URL de checkout."),
                bank("CHECKOUT_CANCEL", "PUT /v1/checkouts/{id}/cancel."),
                bank(
                    "CHECKOUT_TOKENIZE",
                    "Rota ainda não confirmada na documentação pública. No mock responde 200; ao vivo, 422.",
                ),
            ],
        ),
        folder(
            "03 Recorrência",
            "Assinatura mensal no billing e recorrência C6 (checkout e Pix automático). Aprove com o usuário reviewer.",
            [
                request(
                    "Cadastrar lojista para recorrência",
                    "POST",
                    "{{billingUrl}}/billing/v1/merchants",
                    {
                        "operationId": "{{$guid}}",
                        "tenant": "{{merchantId}}",
                        "name": "Loja Homologacao",
                        "plan": "START-2026-11",
                        "evidence": "contract-local",
                    },
                    idempotent(),
                    expect(200, save='pm.collectionVariables.set("billingMerchantId", body.id);'),
                ),
                request(
                    "Aprovar lojista",
                    "POST",
                    "{{billingUrl}}/billing/v1/merchants/{{billingMerchantId}}/approve",
                    {"operationId": "{{$guid}}"},
                    idempotent(),
                    expect(200),
                    username="reviewer",
                    description="O solicitante não aprova o próprio cadastro.",
                ),
                request(
                    "Cadastrar recorrência mensal",
                    "POST",
                    "{{billingUrl}}/billing/v1/subscriptions",
                    {
                        "operationId": "{{$guid}}",
                        "merchantId": "{{billingMerchantId}}",
                        "customerRef": "cliente-recorrente",
                        "amount": 4990,
                        "method": "PIX",
                        "firstDue": "2026-12-31",
                        "anchorDay": 31,
                        "conditions": conditions,
                        "mandateReference": "mandate-local",
                    },
                    idempotent(),
                    expect(200, save='pm.collectionVariables.set("subscriptionId", body.id);'),
                ),
                request(
                    "Gerar ciclo de faturas",
                    "POST",
                    "{{billingUrl}}/billing/v1/cycles/run",
                    headers=merchant_headers(),
                    tests=expect(200),
                    description="Gera faturas vencidas até hoje. Uma recorrência com firstDue futuro não cria fatura neste ciclo.",
                ),
                request(
                    "Listar assinaturas",
                    "GET",
                    "{{billingUrl}}/billing/v1/resources/subscriptions?page=1&size=20",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                request(
                    "Cancelar recorrência",
                    "POST",
                    "{{billingUrl}}/billing/v1/subscriptions/{{subscriptionId}}/cancel",
                    {"operationId": "{{$guid}}"},
                    idempotent(),
                    expect(200),
                ),
                bank("CHECKOUT_RECURRENCE_CREATE", "Recorrência de checkout C6. Path não confirmado; mock 200, ao vivo 422."),
                bank("CHECKOUT_RECURRENCE_GET", "Consulta da recorrência de checkout C6."),
                bank("PIX_AUTO_REC_PUT", "PUT /v2/pix/rec/{id}"),
                bank("PIX_AUTO_REC_GET", "GET /v2/pix/rec/{id}"),
                bank("PIX_AUTO_REC_LIST", "GET /v2/pix/rec"),
                bank("PIX_AUTO_SOLIC_POST", "POST /v2/pix/solicrec"),
                bank("PIX_AUTO_SOLIC_GET", "GET /v2/pix/solicrec/{id}"),
                bank("PIX_AUTO_COBR_PUT", "PUT /v2/pix/cobr/{txid}"),
                bank("PIX_AUTO_COBR_GET", "GET /v2/pix/cobr/{txid}"),
                bank("PIX_AUTO_COBR_PATCH", "PATCH /v2/pix/cobr/{txid}"),
                bank("PIX_AUTO_RETRY", "POST /v2/pix/cobr/{txid}/retentativa/{data}"),
                bank("PIX_AUTO_WEBHOOK_REC_PUT", "PUT /v2/pix/webhookrec"),
                bank("PIX_AUTO_WEBHOOK_COBR_PUT", "PUT /v2/pix/webhookcobr"),
            ],
        ),
        folder(
            "04 Conciliação",
            "Consulta de jornada, extrato C6, recebíveis, split do marketplace e job de conciliação.",
            [
                request(
                    "Conciliar jornada no provedor",
                    "POST",
                    "{{gatewayUrl}}/gateway/v1/journeys/{{journeyId}}/refresh",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                request(
                    "Cancelar jornada",
                    "POST",
                    "{{gatewayUrl}}/gateway/v1/journeys/{{journeyId}}/cancel",
                    headers=merchant_headers(),
                    tests=expect(200, 409),
                    description="409 quando o estado ainda exige conciliação antes do cancelamento.",
                ),
                request(
                    "Criar fatura para liquidação",
                    "POST",
                    "{{billingUrl}}/billing/v1/invoices",
                    {
                        "operationId": "{{$guid}}",
                        "merchantId": "{{billingMerchantId}}",
                        "customerRef": "cliente-fatura",
                        "amount": 10000,
                        "method": "BOLETO",
                        "dueDate": "2026-11-23",
                        "conditions": conditions,
                        "contractReference": "contract-local",
                    },
                    idempotent(),
                    expect(200, save='pm.collectionVariables.set("invoiceId", body.id);'),
                    description="Depende do lojista aprovado na pasta Recorrência.",
                ),
                request(
                    "Consultar decisão de retentativa",
                    "GET",
                    "{{billingUrl}}/billing/v1/retry-advice?outcome=TIMEOUT&attempts=1",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                request(
                    "Consultar fluxo de caixa",
                    "GET",
                    "{{billingUrl}}/billing/v1/cashflow?from=2026-11-01&to=2026-12-31",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                request(
                    "Registrar liquidação confirmada",
                    "POST",
                    "{{billingUrl}}/billing/v1/internal/settlements",
                    {
                        "provider": "c6",
                        "eventId": "{{$guid}}",
                        "invoiceId": "{{invoiceId}}",
                        "providerOperation": "boleto-local",
                        "amount": 10000,
                        "providerCost": 0,
                        "paidOn": "2026-11-23",
                        "settledAt": "2026-11-23T18:00:00Z",
                        "depositReference": "deposit-local",
                    },
                    idempotent(),
                    expect(200),
                    username="provider-worker",
                    description="Somente o usuário provider-worker. Não chame a partir de um webhook público.",
                ),
                request(
                    "Painel financeiro",
                    "GET",
                    "{{billingUrl}}/billing/v1/dashboard?page=1&size=20",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                request(
                    "Cadastrar recebedor",
                    "POST",
                    "{{marketplaceUrl}}/marketplace/v1/recipients",
                    {
                        "name": "Recebedor Homologacao",
                        "document": "12345678901",
                        "provider_recipient_id": "c6-recipient-1",
                    },
                    idempotent(),
                    expect(200, save='pm.collectionVariables.set("recipientId", body.id);'),
                ),
                request(
                    "Criar plano de split",
                    "POST",
                    "{{marketplaceUrl}}/marketplace/v1/split-plans",
                    {
                        "order_id": "{{orderId}}",
                        "amount": 10000,
                        "allocations": [{"recipient_id": "{{recipientId}}", "amount": 10000}],
                    },
                    idempotent(),
                    expect(200, save='pm.collectionVariables.set("splitId", body.id);'),
                ),
                request(
                    "Prévia de split",
                    "POST",
                    "{{marketplaceUrl}}/marketplace/v1/split-plans/preview",
                    {
                        "amount": 10000,
                        "paymentMethod": "PIX",
                        "rules": [
                            {"recipientId": "{{recipientId}}", "fixedCents": 0, "percentageBps": 10000}
                        ],
                    },
                    merchant_headers(),
                    expect(200),
                ),
                request(
                    "Solicitar conciliação de split",
                    "POST",
                    "{{marketplaceUrl}}/marketplace/v1/settlements",
                    {"split_id": "{{splitId}}", "evidence_reference": "extrato-c6-local"},
                    idempotent(),
                    expect(200, save='pm.collectionVariables.set("settlementId", body.id);'),
                ),
                request(
                    "Listar conciliações de split",
                    "GET",
                    "{{marketplaceUrl}}/marketplace/v1/settlements?page=1&size=20",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                request(
                    "Obter conciliação de split",
                    "GET",
                    "{{marketplaceUrl}}/marketplace/v1/settlements/{{settlementId}}",
                    headers=merchant_headers(),
                    tests=expect(200),
                ),
                request(
                    "Executar repasse",
                    "POST",
                    "{{marketplaceUrl}}/marketplace/v1/settlements/{{settlementId}}/payouts",
                    headers=idempotent(),
                    tests=expect(501),
                    description="Contrato reservado. Resposta 501.",
                ),
                bank("STATEMENT_LIST", "GET /v1/statement/"),
                bank("STATEMENT_GET", "GET /v1/statement/{id}"),
                bank("BALANCE_GET", "GET /v1/balance/. Path não confirmado; mock 200, ao vivo 422."),
                bank("TRANSACTIONS_LIST", "GET /v1/c6pay/statement/transactions"),
                bank("RECEIVABLES_LIST", "GET /v1/c6pay/statement/receivables"),
                bank("PIX_GET", "GET /v2/pix/pix/{e2eid}"),
                bank("PIX_LIST", "GET /v2/pix/pix"),
                bank("WEBHOOK_CREATE", "POST /v1/webhooks/"),
                bank("WEBHOOK_LIST", "GET /v1/webhooks/"),
                bank("WEBHOOK_GET", "GET /v1/webhooks/{id}"),
                bank("WEBHOOK_DELETE", "DELETE /v1/webhooks/{id}"),
                request(
                    "Disparar job de conciliação",
                    "POST",
                    "{{reconciliationUrl}}/internal/v1/reconciliation/run",
                    headers=merchant_headers(),
                    tests=expect(501),
                    description="Escopo reconciliation/run. O adaptador ainda responde 501.",
                ),
            ],
        ),
        folder(
            "05 Pagamentos C6",
            "Pix, boleto, bolepix e agendamento pelo gateway, sempre em mock.",
            [
                bank("PIX_COB_POST", "POST /v2/pix/cob"),
                bank("PIX_COB_GET", "GET /v2/pix/cob/{txid}"),
                bank("PIX_COB_PATCH", "PATCH /v2/pix/cob/{txid}"),
                bank("PIX_COB_LIST", "GET /v2/pix/cob"),
                bank("PIX_COBV_PUT", "PUT /v2/pix/cobv/{txid}"),
                bank("PIX_COBV_GET", "GET /v2/pix/cobv/{txid}"),
                bank("PIX_COBV_PATCH", "PATCH /v2/pix/cobv/{txid}"),
                bank("PIX_COBV_LIST", "GET /v2/pix/cobv"),
                bank("PIX_LOTE_PUT", "PUT /v2/pix/lotecobv/{id}"),
                bank("PIX_LOTE_GET", "GET /v2/pix/lotecobv/{id}"),
                bank("PIX_LOTE_PATCH", "PATCH /v2/pix/lotecobv/{id}"),
                bank("PIX_REFUND_PUT", "PUT /v2/pix/pix/{e2eid}/devolucao/{id}"),
                bank("PIX_REFUND_GET", "GET /v2/pix/pix/{e2eid}/devolucao/{id}"),
                bank("PIX_WEBHOOK_PUT", "PUT /v2/pix/webhook/{chave}"),
                bank("PIX_WEBHOOK_GET", "GET /v2/pix/webhook/{chave}"),
                bank("PIX_WEBHOOK_DELETE", "DELETE /v2/pix/webhook/{chave}"),
                bank("BOLETO_CREATE", "POST /v1/bank_slips/"),
                bank("BOLETO_GET", "GET /v1/bank_slips/{id}"),
                bank("BOLETO_UPDATE", "PUT /v1/bank_slips/{id}"),
                bank("BOLETO_CANCEL", "PUT /v1/bank_slips/{id}/cancel"),
                bank("BOLEPIX_CREATE", "POST /v2/bank_slips/"),
                bank("BOLEPIX_GET", "GET /v2/bank_slips/{id}"),
                bank("BOLEPIX_CANCEL", "PUT /v2/bank_slips/{id}/cancel"),
                bank("SCHEDULE_BOLETO_CREATE", "Agendamento de boleto. Path não confirmado; mock 200, ao vivo 422."),
                bank("SCHEDULE_PIX_CREATE", "Agendamento de Pix. Path não confirmado; mock 200, ao vivo 422."),
                bank("SCHEDULE_GET", "GET /v1/schedule_payments/{id}. Path não confirmado."),
                bank("SCHEDULE_CANCEL", "DELETE /v1/schedule_payments/{id}. Path não confirmado."),
                bank("DDA_LIST", "Consulta DDA. Path não confirmado."),
                request(
                    "Capturar cobrança do pedido",
                    "POST",
                    "{{gatewayUrl}}/gateway/v1/charges/{{chargeId}}/capture",
                    {"amount": 10000},
                    idempotent(),
                    expect(501),
                ),
                request(
                    "Cancelar ou estornar cobrança do pedido",
                    "DELETE",
                    "{{gatewayUrl}}/gateway/v1/charges/{{chargeId}}",
                    {"amount": 10000},
                    idempotent(),
                    expect(501),
                ),
                request(
                    "Capturar pagamento",
                    "POST",
                    "{{gatewayUrl}}/v1/payments/{{paymentId}}/captures",
                    headers=merchant_headers(),
                    tests=expect(501),
                ),
                request(
                    "Estornar pagamento",
                    "POST",
                    "{{gatewayUrl}}/v1/payments/{{paymentId}}/refunds",
                    headers=merchant_headers(),
                    tests=expect(501),
                ),
                request(
                    "Desfazer pagamento",
                    "POST",
                    "{{gatewayUrl}}/v1/payments/{{paymentId}}/voids",
                    headers=merchant_headers(),
                    tests=expect(501),
                ),
            ],
        ),
    ],
}

# Health request should not inherit basic auth.
collection["item"][0]["item"][0]["request"]["auth"] = {"type": "noauth"}

environment = {
    "name": "InPay local",
    "values": [
        {"key": "gatewayUrl", "value": "http://127.0.0.1:8084", "enabled": True},
        {"key": "linksUrl", "value": "http://127.0.0.1:8082", "enabled": True},
        {"key": "billingUrl", "value": "http://127.0.0.1:8113", "enabled": True},
        {"key": "marketplaceUrl", "value": "http://127.0.0.1:8112", "enabled": True},
        {"key": "reconciliationUrl", "value": "http://127.0.0.1:8089", "enabled": True},
        {"key": "localPassword", "value": "inpay@123", "enabled": True},
        {"key": "merchantId", "value": MERCHANT, "enabled": True},
        {"key": "pixKey", "value": PIX_KEY, "enabled": True},
    ],
    "_postman_variable_scope": "environment",
}

(ROOT / "inpay-gateway.postman_collection.json").write_text(
    json.dumps(collection, ensure_ascii=False, indent=2), encoding="utf-8"
)
(ROOT / "inpay-local.postman_environment.json").write_text(
    json.dumps(environment, ensure_ascii=False, indent=2), encoding="utf-8"
)
print("requests", sum(len(folder["item"]) for folder in collection["item"]))
