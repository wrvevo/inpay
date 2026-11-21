# InPay Tap — protótipo nativo, sem processamento financeiro

Fontes iniciais SwiftUI/iOS e Android/Kotlin. **Não são aplicativos completos ou homologados**: não há projeto Xcode/Gradle, SDK de adquirente, assinatura, distribuição ou teste em aparelho nesta entrega. Importar os fontes em projetos nativos após selecionar o PSP. O botão permanece desabilitado deliberadamente.

A API de e-commerce de cartões não substitui um SDK SoftPOS/Tap to Pay. No iPhone, a Apple exige integração com PSP e configuração certificada de terminal: https://developer.apple.com/tap-to-pay/regions/ . Não usar Core NFC/Android NFC para capturar PAN/CVV.

Contrato de integração a implementar após acesso ao SDK:

1. App autentica operador com Cognito Authorization Code + PKCE; tokens em Keychain/Keystore, nunca em código-fonte.
2. Backend valida vínculo operador/tenant/estabelecimento/terminal e credenciamento, criando sessão idempotente de valor em centavos.
3. Adaptador do PSP entrega token efêmero de sessão ao SDK; credenciais de adquirente permanecem no backend.
4. SDK coleta cartão e devolve referência. Callback do aparelho não comprova liquidação.
5. Backend consulta/valida evento do PSP antes de atualizar cobrança. Timeout produz UNKNOWN e consulta; não repetir débito.
6. Confirmar dispositivos/versões, integridade, requisitos PCI aplicáveis e homologação com o parceiro antes de ativar o botão.

Não há endpoint fictício que retorne pagamento aprovado. Próxima dependência concreta: escolher PSP com SDK Android e iOS disponível contratualmente e obter documentação/ambientes/entitlements.
