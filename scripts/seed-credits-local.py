#!/usr/bin/env python3
"""Seed idempotente de dados fictícios. Somente HTTP loopback e credenciais do perfil local."""
import base64
import json
import os
import urllib.parse
import urllib.request
import uuid

base = os.environ.get('MARKETPLACE_LOCAL_URL', 'http://127.0.0.1:8110')
parsed = urllib.parse.urlparse(base)
if parsed.scheme != 'http' or parsed.hostname not in ('127.0.0.1', 'localhost') or parsed.username or parsed.query:
    raise SystemExit('Use exclusivamente o serviço local em loopback')
password = os.environ.get('LOCAL_PASSWORD', 'inpay@123')

def key(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'inpay-marketplace-local-v1/' + name))

def post(path, name, body, user='developer'):
    data = json.dumps({'operationId': key(name), **body}).encode()
    auth = base64.b64encode((user + ':' + password).encode()).decode()
    request = urllib.request.Request(base + '/credits/v1/' + path, data=data,
        headers={'Content-Type': 'application/json', 'Authorization': 'Basic ' + auth}, method='POST')
    with urllib.request.urlopen(request, timeout=15) as response:
        if response.status != 200:
            raise SystemExit('Operação local não confirmada')

post('admin/accounts', 'customer-account', {'owner': 'customer', 'creditor': 'PlacarPlay DEMONSTRAÇÃO', 'contract': 'DEMO-SEM-VALOR-JURIDICO'})
for kind, amount in [('CREDIT_LIMIT', 100000), ('SETTLED_REPASS', 20000), ('VERIFIED_VOUCHER', 5000)]:
    name = 'customer-' + kind
    post('admin/adjustments', name, {'owner': 'customer', 'kind': kind, 'amount': amount, 'evidence': 'SIMULATION-' + name})
    post('admin/adjustments/' + key(name) + '/approve', name + '-approve', {}, 'reviewer')
for sku, title, provider in [
    ('DEMO-GAME-100', 'Gift card gamer demonstrativo', 'SIMULATED'),
    ('DEMO-TIMEOUT', 'Pedido com retorno desconhecido', 'SIMULATED_TIMEOUT'),
    ('DEMO-REJECT', 'Pedido recusado pelo fornecedor', 'SIMULATED_REJECT'),
    ('DEMO-COMPENSATION', 'Pedido com compensação pendente', 'SIMULATED_CAPTURE_FAILURE')]:
    post('admin/products', sku, {'product': {'sku': sku, 'title': title, 'price': 10000, 'provider': provider, 'active': True}})
print('Dados locais preparados. Abra o painel, ative modo conectado e acesse Marketplace ou Meu crédito e compras.')
