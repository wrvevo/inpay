$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
$log = Join-Path $root "target\functional-logs"
New-Item -ItemType Directory -Force -Path $log | Out-Null
$groups = @(
    @(
        @{ n = "bank-mock-server"; p = 8098; a = "--server.port=8098" },
        @{ n = "payment-core-service"; p = 8084; a = "--server.port=8084 --inpay.bank-gateway.urls.c6=http://127.0.0.1:8121 --inpay.bank-gateway.urls.itau=http://127.0.0.1:8122 --inpay.bank-gateway.urls.santander=http://127.0.0.1:8123 --inpay.bank-gateway.urls.bb=http://127.0.0.1:8124 --inpay.bank-gateway.urls.bradesco=http://127.0.0.1:8125 --inpay.bank-gateway.urls.caixa=http://127.0.0.1:8126 --inpay.bank-gateway.urls.banrisul=http://127.0.0.1:8127 --inpay.journey.mode=local-simulator --inpay.journey.connector-url=http://127.0.0.1:8120 --inpay.journey.authorization=Basic ZGV2ZWxvcGVyOmxvY2FsLW9ubHktY2hhbmdlLW1l" },
        @{ n = "accounts-service"; p = 8111; a = "--server.port=8111" },
        @{ n = "credits-service"; p = 8110; a = "--server.port=8110" },
        @{ n = "marketplace-service"; p = 8112; a = "--server.port=8112" },
        @{ n = "billing-service"; p = 8113; a = "--server.port=8113" }
    ),
    @(
        @{ n = "connector-c6"; p = 8121; a = "--server.port=8121 --inpay.c6.kafka-enabled=true" },
        @{ n = "connector-itau"; p = 8122; a = "--server.port=8122 --inpay.bank.mock-base-url=http://127.0.0.1:8099" },
        @{ n = "connector-santander"; p = 8123; a = "--server.port=8123 --inpay.bank.mock-base-url=http://127.0.0.1:8099" },
        @{ n = "connector-bb"; p = 8124; a = "--server.port=8124 --inpay.bank.mock-base-url=http://127.0.0.1:8099" },
        @{ n = "connector-bradesco"; p = 8125; a = "--server.port=8125 --inpay.bank.mock-base-url=http://127.0.0.1:8099" },
        @{ n = "connector-caixa"; p = 8126; a = "--server.port=8126 --inpay.bank.mock-base-url=http://127.0.0.1:8099" },
        @{ n = "connector-banrisul"; p = 8127; a = "--server.port=8127 --inpay.bank.mock-base-url=http://127.0.0.1:8099" }
    ),
    @(
        @{ n = "connector-cielo"; p = 8101; a = "--server.port=8101" },
        @{ n = "connector-rede"; p = 8102; a = "--server.port=8102" },
        @{ n = "connector-getnet"; p = 8103; a = "--server.port=8103" },
        @{ n = "connector-stone"; p = 8104; a = "--server.port=8104" },
        @{ n = "connector-bin"; p = 8105; a = "--server.port=8105" },
        @{ n = "connector-global-payments"; p = 8106; a = "--server.port=8106" },
        @{ n = "connector-pagbank"; p = 8107; a = "--server.port=8107" },
        @{ n = "connector-safrapay"; p = 8108; a = "--server.port=8108" },
        @{ n = "merchant-service"; p = 8081; a = "--server.port=8081" },
        @{ n = "payment-links-service"; p = 8082; a = "--server.port=8082" },
        @{ n = "checkout-service"; p = 8083; a = "--server.port=8083" },
        @{ n = "routing-service"; p = 8085; a = "--server.port=8085" },
        @{ n = "token-service"; p = 8086; a = "--server.port=8086" },
        @{ n = "fraud-service"; p = 8087; a = "--server.port=8087" },
        @{ n = "webhook-ingress-service"; p = 8088; a = "--server.port=8088" },
        @{ n = "reconciliation-service"; p = 8089; a = "--server.port=8089" },
        @{ n = "notification-service"; p = 8090; a = "--server.port=8090" },
        @{ n = "transaction-query-service"; p = 8091; a = "--server.port=8091" }
    )
)
function Free-Mb { [int]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1024) }
foreach ($group in $groups) {
    if ((Free-Mb) -lt 2500) {
        Write-Output "stop-low-memory free=$(Free-Mb)"
        break
    }
    foreach ($app in $group) {
        if ((Free-Mb) -lt 1800) {
            Write-Output "skip $($app.n) free=$(Free-Mb)"
            continue
        }
        $url = "http://127.0.0.1:$($app.p)/actuator/health"
        $code = curl.exe -s -o NUL -w "%{http_code}" --max-time 2 $url
        if ($code -eq "200") { Write-Output "already $($app.n)"; continue }
        $jar = Join-Path $root "services\$($app.n)\target\$($app.n)-0.2.0-SNAPSHOT.jar"
        $javaArgs = @("-Xms64m", "-Xmx192m", "-jar", $jar, "--spring.profiles.active=local")
        foreach ($piece in ($app.a -split " ")) {
            if ($piece.StartsWith("--") -or $javaArgs.Count -eq 0) { $javaArgs += $piece }
            else { $javaArgs[$javaArgs.Count - 1] = $javaArgs[$javaArgs.Count - 1] + " " + $piece }
        }
        Start-Process -FilePath "java" -ArgumentList $javaArgs -RedirectStandardOutput (Join-Path $log "$($app.n).log") -RedirectStandardError (Join-Path $log "$($app.n).err") -WorkingDirectory $root | Out-Null
        $up = $false
        for ($i = 0; $i -lt 16; $i++) {
            Start-Sleep -Seconds 4
            $code = curl.exe -s -o NUL -w "%{http_code}" --max-time 3 $url
            if ($code -eq "200") { $up = $true; break }
        }
        Write-Output "$(if ($up) {'up'} else {'down'}) $($app.n) free=$(Free-Mb)"
    }
}
