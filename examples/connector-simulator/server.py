"""Contrato HTTP de conector para laboratório. NÃO chama bancos nem cria Pix reais."""
import base64
import json
import os
import sqlite3
import threading
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

LOCK = threading.Lock()
DB = os.getenv('SIMULATOR_DB', '/tmp/inpay-simulator.sqlite')
PASSWORD = os.environ['LOCAL_PASSWORD']
AUTH = 'Basic ' + base64.b64encode(('developer:' + PASSWORD).encode()).decode()
with sqlite3.connect(DB) as connection:
    connection.execute('CREATE TABLE IF NOT EXISTS payments(id TEXT PRIMARY KEY, operation TEXT UNIQUE, payload TEXT, amount INTEGER, status INTEGER, method TEXT)')

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, status, data):
        content = json.dumps(data).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('X-InPay-Simulation', 'true')
        self.send_header('Content-Length', str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def do_GET(self):
        self.handle_operation()

    def do_POST(self):
        self.handle_operation()

    def handle_operation(self):
        if self.path == '/health':
            return self.reply(200, {'simulation': True})
        if self.headers.get('Authorization') != AUTH:
            return self.reply(401, {'error': 'Unauthorized'})
        try:
            parsed = urlparse(self.path)
            if not parsed.path.startswith('/internal/v1/cielo/operations/'):
                return self.reply(404, {})
            operation = parsed.path.rsplit('/', 1)[-1]
            length = int(self.headers.get('Content-Length', 0))
            if length > 65536:
                return self.reply(413, {})
            body = json.loads(self.rfile.read(length)) if length else {}
            with LOCK, sqlite3.connect(DB) as db:
                if operation == 'CREATE_SALE' and self.command == 'POST':
                    payload = body['payload']['Payment']
                    canonical = json.dumps(body['payload'], sort_keys=True)
                    existing = db.execute('SELECT id,payload FROM payments WHERE operation=?', (body['operationId'],)).fetchone()
                    if existing and existing[1] != canonical:
                        return self.reply(409, {})
                    identifier = existing[0] if existing else str(uuid.uuid4())
                    if not existing:
                        db.execute('INSERT INTO payments VALUES(?,?,?,?,?,?)', (identifier, body['operationId'], canonical, payload['Amount'], 2 if payload['Type'].endswith('Card') else 12, payload['Type']))
                elif operation == 'GET_PAYMENT' and self.command == 'GET':
                    identifier = parse_qs(parsed.query)['resourceId'][0]
                    # Consulta confirma automaticamente somente no simulador, para demonstração.
                    db.execute('UPDATE payments SET status=2 WHERE id=? AND status=12', (identifier,))
                elif operation == 'VOID_PAYMENT' and self.command == 'POST':
                    identifier = body['resourceId']
                    db.execute('UPDATE payments SET status=10 WHERE id=?', (identifier,))
                else:
                    return self.reply(501, {'simulation': True})
                row = db.execute('SELECT amount,status,method FROM payments WHERE id=?', (identifier,)).fetchone()
                if not row:
                    return self.reply(404, {})
                payment = {'PaymentId': identifier, 'Amount': row[0], 'Status': row[1]}
                if row[2] == 'Pix':
                    payment['QrCodeString'] = 'SIMULADO-NAO-PAGAVEL-' + identifier
                if row[2] == 'Boleto':
                    payment['DigitableLine'] = 'SIMULADO-NAO-PAGAVEL'
                return self.reply(200, {'httpStatus': 200, 'uncertain': False, 'replayed': False, 'data': {'Payment': payment}})
        except (ValueError, KeyError, TypeError):
            return self.reply(422, {'error': 'Invalid simulation contract'})

if __name__ == '__main__':
    ThreadingHTTPServer(('0.0.0.0', int(os.getenv('PORT', '8120'))), Handler).serve_forever()
