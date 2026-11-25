<?php
declare(strict_types=1);

namespace InPay\Gateway\Model;

use Magento\Framework\Exception\LocalizedException;
use Magento\Sales\Model\Order\Payment;

/** Cliente da jornada do gateway. Credencial fica no servidor da loja. */
class GatewayClient
{
    /**
     * @return array{id: string, status: string}
     */
    public function start(Payment $payment, string $journey, int $amountCents): array
    {
        $base = rtrim((string) getenv("INPAY_URL") ?: "http://127.0.0.1:8084", "/");
        $authorization = getenv("INPAY_AUTHORIZATION");
        if (!$authorization) {
            throw new LocalizedException(
                __("Configure INPAY_AUTHORIZATION no servidor da loja."),
            );
        }
        $key = (string) $payment->getOrder()->getIncrementId();
        $body = json_encode(
            [
                "merchantOrder" => $key,
                "amount" => $amountCents,
                "method" => $journey,
                "instrument" =>
                    getenv("STORE_MODE") === "cielo-sandbox"
                        ? "sandbox-approved"
                        : "fixture-approved",
            ],
            JSON_THROW_ON_ERROR,
        );
        $handle = curl_init($base . "/gateway/v1/journeys");
        curl_setopt_array($handle, [
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => $body,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 25,
            CURLOPT_HTTPHEADER => [
                "Content-Type: application/json",
                "Authorization: " . $authorization,
                "Idempotency-Key: " . $key,
                "X-Merchant-Id: " .
                (getenv("INPAY_TENANT") ?:
                    "11111111-1111-1111-1111-111111111111"),
            ],
        ]);
        $raw = curl_exec($handle);
        $status = (int) curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
        curl_close($handle);
        if (!is_string($raw) || $status !== 200) {
            throw new LocalizedException(
                __("Gateway InPay recusou ou não respondeu. Consulte o pedido antes de cobrar de novo."),
            );
        }
        $decoded = json_decode($raw, true, 512, JSON_THROW_ON_ERROR);
        if (!isset($decoded["id"], $decoded["status"])) {
            throw new LocalizedException(__("Resposta inválida do Gateway."));
        }
        return ["id" => (string) $decoded["id"], "status" => (string) $decoded["status"]];
    }
}
