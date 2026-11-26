$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
$log = Join-Path $root "target\functional-logs"
$missing = @(
    @{ n = "payment-links-service"; p = 8082; a = "--server.port=8082" },
    @{ n = "fraud-service"; p = 8087; a = "--server.port=8087" },
    @{ n = "webhook-ingress-service"; p = 8088; a = "--server.port=8088" },
    @{ n = "bank-mock-server"; p = 8098; a = "--server.port=8098" },
    @{ n = "connector-cielo"; p = 8101; a = "--server.port=8101" },
    @{ n = "connector-rede"; p = 8102; a = "--server.port=8102" },
    @{ n = "connector-stone"; p = 8104; a = "--server.port=8104" },
    @{ n = "connector-bin"; p = 8105; a = "--server.port=8105" },
    @{ n = "connector-global-payments"; p = 8106; a = "--server.port=8106" },
    @{ n = "connector-pagbank"; p = 8107; a = "--server.port=8107" },
    @{ n = "accounts-service"; p = 8111; a = "--server.port=8111" },
    @{ n = "marketplace-service"; p = 8112; a = "--server.port=8112" },
    @{ n = "billing-service"; p = 8113; a = "--server.port=8113" },
    @{ n = "connector-itau"; p = 8122; a = "--server.port=8122 --inpay.bank.mock-base-url=http://127.0.0.1:8099" },
    @{ n = "connector-santander"; p = 8123; a = "--server.port=8123 --inpay.bank.mock-base-url=http://127.0.0.1:8099" },
    @{ n = "connector-bb"; p = 8124; a = "--server.port=8124 --inpay.bank.mock-base-url=http://127.0.0.1:8099" },
    @{ n = "connector-bradesco"; p = 8125; a = "--server.port=8125 --inpay.bank.mock-base-url=http://127.0.0.1:8099" },
    @{ n = "connector-banrisul"; p = 8127; a = "--server.port=8127 --inpay.bank.mock-base-url=http://127.0.0.1:8099" }
)
foreach ($app in $missing) {
    $url = "http://127.0.0.1:$($app.p)/actuator/health"
    $code = curl.exe -s -o NUL -w "%{http_code}" --max-time 2 $url
    if ($code -eq "200") {
        Write-Output "already $($app.n)"
        continue
    }
    $jar = Join-Path $root "services\$($app.n)\target\$($app.n)-0.2.0-SNAPSHOT.jar"
    $javaArgs = @("-Xms64m", "-Xmx256m", "-jar", $jar, "--spring.profiles.active=local")
    $javaArgs += $app.a -split " "
    Start-Process -FilePath "java" -ArgumentList $javaArgs -RedirectStandardOutput (Join-Path $log "$($app.n).log") -RedirectStandardError (Join-Path $log "$($app.n).err") -WorkingDirectory $root | Out-Null
    $up = $false
    for ($i = 0; $i -lt 12; $i++) {
        Start-Sleep -Seconds 5
        $code = curl.exe -s -o NUL -w "%{http_code}" --max-time 3 $url
        if ($code -eq "200") { $up = $true; break }
    }
    if ($up) { Write-Output "up $($app.n)" } else { Write-Output "down $($app.n)" }
}
