#!/usr/bin/env python3
"""Verifica fronteiras Maven e registra acoplamentos de teste para migração por equipes."""
from pathlib import Path
import xml.etree.ElementTree as ET

NS = {"m": "http://maven.apache.org/POM/4.0.0"}

def read_pom(path):
    return ET.parse(path).getroot()

def value(node, name, default=""):
    return node.findtext("m:" + name, default, NS).strip()

def main():
    deployable = {value(read_pom(p), "artifactId") for folder in ("services", "lambdas")
                  for p in Path(folder).glob("*/pom.xml")}
    violations = []
    for folder in ("services", "lambdas", "libs"):
        for path in sorted(Path(folder).glob("*/pom.xml")):
            for dep in read_pom(path).findall("m:dependencies/m:dependency", NS):
                artifact = value(dep, "artifactId")
                if value(dep, "groupId") != "br.com.includelabs" or artifact not in deployable:
                    continue
                edge = f"{path.parent} -> {artifact}"
                if value(dep, "scope", "compile") == "test":
                    print("Acoplamento de teste a substituir por contrato:", edge)
                else:
                    violations.append(edge)
    if violations:
        raise SystemExit("Dependências indevidas entre aplicações:\n" + "\n".join(violations))
    print("Fronteiras Maven diretas verificadas.")

if __name__ == "__main__":
    main()
