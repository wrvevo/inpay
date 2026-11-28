#!/usr/bin/env bash
set -euo pipefail
# Ambiente isolado com dados sintéticos. Não executar contra produção.
export LOCAL_PASSWORD=ci-local-only-change-me
export STORE_ADMIN_PASSWORD=ci-store-only-change-me
export DEMO_CONNECTOR_AUTHORIZATION="Basic $(printf 'inpay:%s' "$LOCAL_PASSWORD" | base64 -w0)"
export COMPOSE_PARALLEL_LIMIT=2
export INPAY_DOCUMENT_KEY=$(openssl rand -base64 32)
echo "::add-mask::$INPAY_DOCUMENT_KEY"
export INPAY_DOCUMENT_KEY_ID=ci INPAY_DOCUMENT_RETENTION_DAYS=30 INPAY_DOCUMENT_SYNTHETIC_FIXTURE=true
export C6_ENABLED=false ITAU_ENABLED=false SANTANDER_ENABLED=false BANK_ENABLED=false
export CIELO_ENABLED=false REDE_ENABLED=false GETNET_ENABLED=false STONE_ENABLED=false BIN_ENABLED=false GLOBAL_PAYMENTS_ENABLED=false PAGBANK_ENABLED=false SAFRAPAY_ENABLED=false
export CLEARSALE_ENABLED=false SERASA_ENABLED=false SERASA_EXPERIAN_ENABLED=false
C=(docker compose --env-file /dev/null -p inpay-ci -f docker-compose.yml -f docker-compose.banks.yml -f docker-compose.risk-mocks.yml -f docker-compose.demo.yml)
banks=(connector-c6 connector-itau connector-santander connector-bb connector-bradesco connector-caixa connector-banrisul)
cards=(connector-cielo connector-rede connector-getnet connector-stone connector-bin connector-global-payments connector-pagbank connector-safrapay)
risk=(connector-clearsale connector-serasa-experian)
mkdir -p test-results
# Preserva evidências e remove apenas os recursos do projeto Docker isolado.
cleanup() {
  local code=$?
  trap - EXIT
  "${C[@]}" ps -a > test-results/compose-ps.txt || true
  "${C[@]}" logs --no-color --tail=150 > test-results/compose.log || true
  if [ "$code" -ne 0 ]; then
    cat test-results/compose-ps.txt
    "${C[@]}" logs --no-color --tail=100 payment-core-service connector-c6 connector-itau connector-santander painel-gerencial game-store || true
    tail -n 150 test-results/compose.log
  fi
  "${C[@]}" down -v --remove-orphans || true
  exit "$code"
}
trap cleanup EXIT
"${C[@]}" config --quiet
# Reutiliza a camada Maven entre imagens e divide a execução para limitar memória.
"${C[@]}" build bank-mock-server payment-core-service "${banks[@]}" "${risk[@]}" fraud-service game-store connector-simulator "${cards[@]}" accounts-service marketplace-service credits-service billing-service painel-gerencial inpay-panel
# Aguarda o endpoint responder com sucesso antes de iniciar a próxima etapa.
wait_http() {
  local url="$1"
  for ((i=0;i<120;i++)); do
    if curl -fsS --max-time 3 "$url" >/dev/null; then return 0; fi
    sleep 2
  done
  echo "Service not ready: $url" >&2
  return 1
}
"${C[@]}" up -d --no-build --wait --wait-timeout 240 payment-core-service bank-mock-server "${banks[@]}" "${risk[@]}" fraud-service
wait_http http://127.0.0.1:8199/__admin/mappings
for p in {8131..8137} 8128 8129; do wait_http "http://127.0.0.1:$p/actuator/health"; done
python3 scripts/verify-wiremock-intents.py --running
"${C[@]}" stop "${banks[@]}" "${risk[@]}"
python3 scripts/docker-payment-load.py
# Inicia quatro adquirentes por vez, todos com o WireMock central.
"${C[@]}" up -d --no-build --wait --wait-timeout 240 "${cards[@]:0:4}"
python3 scripts/verify-wiremock-intents.py --cards --card-start 0 --card-end 4
"${C[@]}" stop "${cards[@]:0:4}"
"${C[@]}" up -d --no-build --wait --wait-timeout 240 "${cards[@]:4:4}"
python3 scripts/verify-wiremock-intents.py --cards --card-start 4 --card-end 8
"${C[@]}" stop "${cards[@]:4:4}"
"${C[@]}" up -d --no-build --wait --wait-timeout 240 game-store accounts-service marketplace-service credits-service billing-service painel-gerencial inpay-panel
wait_http http://127.0.0.1:8099/
wait_http http://127.0.0.1:3080/healthz
python3 scripts/docker-store-e2e.py
# Valida a orquestração HTTP com documentos sintéticos e revisores independentes.
"${C[@]}" stop marketplace-service credits-service billing-service
"${C[@]}" up -d --no-build --wait --wait-timeout 240 connector-c6 connector-itau connector-clearsale connector-serasa-experian connector-cielo
python3 scripts/docker-onboarding-e2e.py create
"${C[@]}" restart painel-gerencial
wait_http http://127.0.0.1:8130/actuator/health
python3 scripts/docker-onboarding-e2e.py verify
