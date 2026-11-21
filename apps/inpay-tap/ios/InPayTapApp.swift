import SwiftUI

/// Protótipo de interface. A ativação depende do SDK e dos entitlements do PSP contratado.
@main
struct InPayTapApp: App {
    var body: some Scene {
        WindowGroup { TapCheckoutView() }
    }
}

/// O aplicativo nunca lê PAN/CVV diretamente pelo Core NFC.
struct TapCheckoutView: View {
    @State private var amount = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Receber por aproximação") {
                    TextField("Valor em reais", text: $amount)
                        .keyboardType(.decimalPad)
                    Text("Credenciamento, terminal e SDK aguardam homologação.")
                    Button("Receber com Tap to Pay") {}
                        .disabled(true)
                }
                Section("Situação da integração") {
                    Label("PSP não configurado", systemImage: "lock.shield")
                    Text("Nenhuma transação é iniciada por este protótipo.")
                }
            }
            .navigationTitle("InPay Tap")
        }
    }
}
