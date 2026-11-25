<?php
declare(strict_types=1);

namespace InPay\Gateway\Model;

use Magento\Framework\Api\AttributeValueFactory;
use Magento\Framework\Api\ExtensionAttributesFactory;
use Magento\Framework\App\Config\ScopeConfigInterface;
use Magento\Framework\Model\Context;
use Magento\Framework\Registry;
use Magento\Payment\Helper\Data;
use Magento\Payment\Model\InfoInterface;
use Magento\Payment\Model\Method\AbstractMethod;
use Magento\Payment\Model\Method\Logger;
use Magento\Sales\Model\Order\Payment;

/** Meio habilitado por padrão. A jornada é definida no virtual type de cada adquirente ou banco. */
class GatewayMethod extends AbstractMethod
{
    protected $_isGateway = true;
    protected $_canAuthorize = true;
    protected $_canUseCheckout = true;
    protected $_isOffline = false;

    public function __construct(
        Context $context,
        Registry $registry,
        ExtensionAttributesFactory $extensionFactory,
        AttributeValueFactory $customAttributeFactory,
        Data $paymentData,
        ScopeConfigInterface $scopeConfig,
        Logger $logger,
        private readonly GatewayClient $client,
        private readonly string $code = "inpay_pix",
        private readonly string $journey = "pix",
        array $data = [],
    ) {
        parent::__construct(
            $context,
            $registry,
            $extensionFactory,
            $customAttributeFactory,
            $paymentData,
            $scopeConfig,
            $logger,
            $data,
        );
        $this->_code = $code;
    }

    public function authorize(InfoInterface $payment, $amount)
    {
        if (!$payment instanceof Payment) {
            return $this;
        }
        $cents = (int) round(((float) $amount) * 100);
        $result = $this->client->start($payment, $this->journey, $cents);
        $payment->setTransactionId($result["id"]);
        $payment->setIsTransactionClosed(false);
        $payment->setAdditionalInformation("inpay_status", $result["status"]);
        $payment->setAdditionalInformation("inpay_journey", $this->journey);
        return $this;
    }
}
