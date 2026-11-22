#!/usr/bin/env python3
"""Executa a loja PHP contra o Gateway real e simulador ou Cielo sandbox."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
from urllib.parse import urlparse


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--live', action='store_true', help='Usa somente Cielo sandbox oficial')
    parser.add_argument('--env-file', default='.env')
    parser.add_argument('--maven', default='mvn')
    parser.add_argument('--php', default=os.environ.get('PHP_BIN') or shutil.which('php'))
    args, extra = parser.parse_known_args()
    if extra[:1] == ['--']:
        extra = extra[1:]
    if not args.php:
        parser.error('Instale PHP 8.3+ ou informe --php /caminho/php')
    root = Path(__file__).resolve().parents[1]
    env = os.environ.copy()
    env['PHP_BIN'] = args.php
    env['CIELO_STORE_E2E'] = str(args.live).lower()
    jvm = []
    if args.live:
        secret_file = Path(args.env_file).resolve()
        if secret_file.exists():
            for line in secret_file.read_text().splitlines():
                if line.startswith('CIELO_') and '=' in line:
                    key, value = line.split('=', 1)
                    env[key] = value.strip().strip('"').strip("'")
        for key, value in {
            'CIELO_BASE_URL': 'https://apisandbox.cieloecommerce.cielo.com.br',
            'CIELO_QUERY_URL': 'https://apiquerysandbox.cieloecommerce.cielo.com.br',
        }.items():
            if env.get(key) != value:
                parser.error(f'{key} deve apontar exatamente para o sandbox oficial')
        if not all(env.get(key) for key in ['CIELO_MERCHANT_ID', 'CIELO_MERCHANT_KEY']):
            parser.error('Configure credenciais sandbox no arquivo local ignorado ou ambiente')
        proxy = urlparse(env.get('HTTPS_PROXY', ''))
        if proxy.hostname:
            jvm = [f'-Dhttps.proxyHost={proxy.hostname}', f'-Dhttps.proxyPort={proxy.port or 80}',
                   '-Dhttp.nonProxyHosts=localhost|127.*']
            # Truststore específico pode ser informado pelo operador em -- -DargLine=...
        build = subprocess.call([args.maven, '-B', '-pl', 'services/connector-cielo', '-am',
                                 'package', '-DskipTests', *extra], cwd=root, env=env)
        if build:
            return build
    command = [args.maven, '-B', '-pl', 'services/payment-core-service', '-am', 'test',
               '-Dtest=StoreJourneyIntegrationTest', '-Dsurefire.failIfNoSpecifiedTests=false']
    if jvm:
        command.append('-DargLine=' + ' '.join(jvm))
    return subprocess.call([*command, *extra], cwd=root, env=env)


if __name__ == '__main__':
    raise SystemExit(main())
