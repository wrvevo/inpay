#!/usr/bin/env python3
"""Run real Cielo sandbox integration tests; secrets are read locally and never printed."""
import argparse
import os
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--env-file', default='.env')
    parser.add_argument('--maven', default='mvn', help='Maven executable; use JAVA_HOME for JDK 21')
    args, maven_args = parser.parse_known_args()
    if maven_args[:1] == ['--']:
        maven_args = maven_args[1:]
    root = Path(__file__).resolve().parents[1]
    secret_file = Path(args.env_file).resolve()
    environment = os.environ.copy()
    for line in secret_file.read_text().splitlines():
        if line.strip() and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            if key.strip().startswith('CIELO_'):
                environment[key.strip()] = value.strip().strip('"').strip("'")
    expected = {
        'CIELO_BASE_URL': 'https://apisandbox.cieloecommerce.cielo.com.br',
        'CIELO_QUERY_URL': 'https://apiquerysandbox.cieloecommerce.cielo.com.br',
    }
    for key, value in expected.items():
        if environment.get(key) != value:
            raise SystemExit(f'{key} must point exactly to the official sandbox')
    if not all(environment.get(k) for k in ('CIELO_MERCHANT_ID', 'CIELO_MERCHANT_KEY')):
        raise SystemExit('Configure sandbox credentials in the ignored local file')
    environment['CIELO_SANDBOX_TEST'] = 'true'
    command = [args.maven, '-B', '-pl', 'services/connector-cielo', '-am',
               '-Pcielo-sandbox', 'verify', *maven_args]
    return subprocess.call(command, cwd=root, env=environment)


if __name__ == '__main__':
    raise SystemExit(main())
