import json
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent

gateway = [
    "merchant_service",
    "payment_links_service",
    "checkout_service",
    "payment_core_service",
    "routing_service",
    "token_service",
    "fraud_service",
    "webhook_ingress_service",
    "reconciliation_service",
    "notification_service",
    "transaction_query_service",
    "connector_cielo",
    "connector_rede",
    "connector_getnet",
    "connector_stone",
    "connector_bin",
    "connector_global_payments",
    "connector_pagbank",
    "connector_safrapay",
    "marketplace_service",
    "accounts_service",
    "commerce_marketplace",
    "billing_service",
]
demo = ["payment_core_service", "fraud_service", "billing_service"]
groups = [
    ("GATEWAY", [("postgres", "postgres", "local-admin-only", "postgres (admin)")] + [(db, db, "local-only", db) for db in gateway]),
    ("DEMO", [(db, db, "local-only", db) for db in demo]),
    ("PAINEL GERENCIAL", [("painel_gerencial", "painel_gerencial", "local-only", "painel_gerencial")]),
]

connections = {}
credentials = {}
for folder, items in groups:
    for database, user, password, label in items:
        key = "postgres-jdbc-inpay-" + folder.lower().replace(" ", "-") + "-" + database.replace("_", "-")
        connections[key] = {
            "provider": "postgresql",
            "driver": "postgres-jdbc",
            "name": label,
            "description": f"{folder} · {database}",
            "save-password": True,
            "read-only": False,
            "folder": folder,
            "configuration": {
                "host": "127.0.0.1",
                "port": "5432",
                "database": database,
                "url": f"jdbc:postgresql://127.0.0.1:5432/{database}",
                "configurationType": "MANUAL",
                "type": "dev",
                "closeIdleConnection": True,
                "auth-model": "native",
                "auth-properties": {"userName": user, "userPassword": password},
            },
        }
        credentials[key] = {"#connection": {"userName": user, "userPassword": password}}

data_sources = {
    "folders": {},
    "connections": connections,
    "connection-types": {
        "dev": {
            "name": "Development",
            "color": "255,255,255",
            "description": "Regular development database",
            "auto-commit": True,
            "confirm-execute": False,
            "confirm-data-change": False,
            "smart-commit": False,
            "smart-commit-recover": True,
            "auto-close-transactions": True,
            "close-transactions-period": 1800,
            "auto-close-connections": True,
            "close-connections-period": 14400,
        }
    },
}
payload = json.dumps(data_sources, indent=2, ensure_ascii=False) + "\n"
credentials_payload = json.dumps(credentials, indent=2, ensure_ascii=False) + "\n"
settings = json.dumps({"defaultConnectionType": "dev"}, indent=2) + "\n"
metadata = json.dumps({"projectName": "INPAY"}, indent=2) + "\n"
meta = """<?xml version="1.0" encoding="UTF-8"?>
<archive version="1">
  <source time="1764170000000" address="127.0.0.1" host="localhost"/>
  <projects>
    <project name="INPAY" description="Conexoes locais do InPay">
      <resource name=".dbeaver">
        <resource name="data-sources.json"/>
        <resource name="credentials-config.json"/>
        <resource name="project-settings.json"/>
        <resource name="project-metadata.json"/>
      </resource>
    </project>
  </projects>
</archive>
"""
(root / "data-sources.json").write_text(payload, encoding="utf-8")
dbp = root / "inpay-local.dbp"
if dbp.exists():
    dbp.unlink()
prefix = "projects/INPAY"
with zipfile.ZipFile(dbp, "w", compression=zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("meta.xml", meta.encode("utf-8"))
    archive.writestr(f"{prefix}/", b"")
    archive.writestr(f"{prefix}/.dbeaver/", b"")
    archive.writestr(f"{prefix}/.dbeaver/data-sources.json", payload.encode("utf-8"))
    archive.writestr(f"{prefix}/.dbeaver/credentials-config.json", credentials_payload.encode("utf-8"))
    archive.writestr(f"{prefix}/.dbeaver/project-settings.json", settings.encode("utf-8"))
    archive.writestr(f"{prefix}/.dbeaver/project-metadata.json", metadata.encode("utf-8"))
print(dbp)
print("connections", len(connections))
