package br.com.includelabs.inpay.tap

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Protótipo sem dependência de SDK financeiro. Não lê cartões nem inicia cobranças. */
class InPayTapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        layout.addView(TextView(this).apply {
            text = "InPay Tap\nReceber por aproximação"
            textSize = 24f
        })
        layout.addView(EditText(this).apply {
            hint = "Valor em reais"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        })
        layout.addView(TextView(this).apply {
            text = "PSP não configurado. Credenciamento, terminal e SDK aguardam homologação."
        })
        layout.addView(Button(this).apply {
            text = "Receber por aproximação"
            isEnabled = false
        })
        setContentView(layout)
    }
}
