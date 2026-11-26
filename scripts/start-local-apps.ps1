# Sobe os mesmos processos das run configurations do IntelliJ (perfil local e as mesmas portas).
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $root "target\functional-logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$apps = @(
    @{ Name = "merchant-service"; Args = "--server.port=8081" },
    @{ Name = "payment-links-service"; Args = "--server.port=8082" },
    @{ Name = "checkout-service"; Args = "--server.port=8083" },
    @{ Name = "payment-core-service"; Args = "--server.port=8084 --inpay.bank-gateway.urls.c6=http://127.0.0.1:8121 --inpay.bank-gateway.urls.itau=http://127.0.0.1:8122 --inpay.bank-gateway.urls.santander=http://127.0.0.1:8123 --inpay.bank-gateway.urls.bb=http://127.0.0.1:8124 --inpay.bank-gateway.urls.bradesco=http://127.0.0.1:8125 --inpay.bank-gateway.urls.caixa=http://127.0.0.1:8126 --inpay.bank-gateway.urls.banrisul=http://127.0.0.1:8127 --inpay.journey.mode=local-simulator --inpay.journey.connector-url=http://127.0.0.1:8120 --inpay.journey.authorization=Basic ZGV2ZWxvcGVyOmxvY2FsLW9ubHktY2hhbmdlLW1l" },
    @{ Name = "routing-service"; Args = "--server.port=8085" },
    @{ Name = "token-service"; Args = "--server.port=8086" },
    @{ Name = "fraud-service"; Args = "--server.port=8087" },
    @{ Name = "webhook-ingress-service"; Args = "--server.port=8088" },
    @{ Name = "reconciliation-service"; Args = "--server.port=8089" },
    @{ Name = "notification-service"; Args = "--server.port=8090" },
    @{ Name = "transaction-query-service"; Args = "--server.port=8091" },
    @{ Name = "bank-mock-server"; Args = "--server.port=8098" },
    @{ Name = "credits-service"; Args = "--server.port=8110" },
    @{ Name = "accounts-service"; Args = "--server.port=8111" },
    @{ Name = "marketplace-service"; Args = "--server.port=8112" },
    @{ Name = "billing-service"; Args = "--server.port=8113" },
    @{ Name = "connector-cielo"; Args = "--server.port=8101" },
    @{ Name = "connector-rede"; Args = "--server.port=8102" },
    @{ Name = "connector-getnet"; Args = "--server.port=8103" },
    @{ Name = "connector-stone"; Args = "--server.port=8104" },
    @{ Name = "connector-bin"; Args = "--server.port=8105" },
    @{ Name = "connector-global-payments"; Args = "--server.port=8106" },
    @{ Name = "connector-pagbank"; Args = "--server.port=8107" },
    @{ Name = "connector-safrapay"; Args = "--server.port=8108" },
    @{ Name = "connector-c6"; Args = "--server.port=8121 --inpay.c6.kafka-enabled=true" },
    @{ Name = "connector-itau"; Args = "--server.port=8122 --inpay.bank.mock-base-url=http://127.0.0.1:8099" },
    @{ Name = "connector-santander"; Args = "--server.port=8123 --inpay.bank.mock-base-url=http://127.0.0.1:8099" },
    @{ Name = "connector-bb"; Args = "--server.port=8124 --inpay.bank.mock-base-url=http://127.0.0.1:8099" },
    @{ Name = "connector-bradesco"; Args = "--server.port=8125 --inpay.bank.mock-base-url=http://127.0.0.1:8099" },
    @{ Name = "connector-caixa"; Args = "--server.port=8126 --inpay.bank.mock-base-url=http://127.0.0.1:8099" },
    @{ Name = "connector-banrisul"; Args = "--server.port=8127 --inpay.bank.mock-base-url=http://127.0.0.1:8099" }
)
foreach ($app in $apps) {
    $jar = Join-Path $root "services\$($app.Name)\target\$($app.Name)-0.2.0-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) { throw "Jar ausente: $jar" }
    $out = Join-Path $logDir "$($app.Name).log"
    $err = Join-Path $logDir "$($app.Name).err"
    $tokens = @()
    foreach ($piece in ($app.Args -split " ")) {
        if ($piece.StartsWith("--") -or $tokens.Count -eq 0) { $tokens += $piece }
        else { $tokens[$tokens.Count - 1] = $tokens[$tokens.Count - 1] + " " + $piece }
    }
    Start-Process -FilePath "java" -ArgumentList (@("-Xms64m", "-Xmx256m", "-jar", $jar, "--spring.profiles.active=local") + $tokens) -RedirectStandardOutput $out -RedirectStandardError $err -WorkingDirectory $root | Out-Null
    Write-Output "started $($app.Name)"
    Start-Sleep -Seconds 2
}
