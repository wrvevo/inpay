#!/usr/bin/env python3
"""Verifica saúde e contrato Swagger dos serviços locais, sem alterar dados de negócio."""
import json
import sys
import urllib.request
from pathlib import Path


def check_services():
    """Retorna zero quando health e OpenAPI de todos os módulos estiverem disponíveis."""
    root = Path(__file__).resolve().parents[1]
    catalog = json.loads((root / "module-catalog.json").read_text())
    failures = []
    for service, (_, port, _) in catalog.items():
        try:
            for endpoint in ("/actuator/health", "/v3/api-docs"):
                with urllib.request.urlopen(f"http://localhost:{port}{endpoint}", timeout=10) as response:
                    payload = json.load(response)
                if endpoint.endswith("health") and payload.get("status") != "UP":
                    raise ValueError("health ainda não está UP")
                if endpoint.endswith("api-docs") and not payload.get("info", {}).get("title", "").startswith("InPay"):
                    raise ValueError("contrato InPay não encontrado")
            print(f"OK  {service}: {port}")
        except Exception as exception:
            failures.append(service)
            print(f"ERRO {service}: {exception}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(check_services())
