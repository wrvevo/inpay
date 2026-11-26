import json
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
sources = json.loads((root / "data-sources.json").read_text(encoding="utf-8"))
credentials = {
    key: {
        "#connection": {
            "user": item["configuration"]["user"],
            "password": item["configuration"]["password"],
        }
    }
    for key, item in sources["connections"].items()
}
project = "InPay Local"
prefix = f"projects/{project}"
meta = """<?xml version="1.0" encoding="UTF-8"?>
<archive version="1">
  <source time="1764170000000" address="127.0.0.1" host="localhost"/>
  <projects>
    <project name="InPay Local" description="Conexoes locais do InPay">
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
settings = json.dumps({"defaultConnectionType": "dev"}, indent=2) + "\n"
metadata = json.dumps({"projectName": project}, indent=2) + "\n"
payload = json.dumps(sources, indent=2, ensure_ascii=False) + "\n"
credentials_payload = json.dumps(credentials, indent=2, ensure_ascii=False) + "\n"
dbp = root / "inpay-local.dbp"
if dbp.exists():
    dbp.unlink()
with zipfile.ZipFile(dbp, "w", compression=zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("meta.xml", meta.encode("utf-8"))
    archive.writestr(f"{prefix}/", b"")
    archive.writestr(f"{prefix}/.dbeaver/", b"")
    archive.writestr(f"{prefix}/.dbeaver/data-sources.json", payload.encode("utf-8"))
    archive.writestr(f"{prefix}/.dbeaver/credentials-config.json", credentials_payload.encode("utf-8"))
    archive.writestr(f"{prefix}/.dbeaver/project-settings.json", settings.encode("utf-8"))
    archive.writestr(f"{prefix}/.dbeaver/project-metadata.json", metadata.encode("utf-8"))
with zipfile.ZipFile(dbp) as archive:
    print("\n".join(archive.namelist()))
    assert "meta.xml" in archive.namelist()
print(dbp)
