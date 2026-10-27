package dev.sxch.tvweb.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import dev.sxch.tvweb.R

class InputPanel(context: Context) : PanelView(context, "Search or enter address") {

    var onSubmit: ((String) -> Unit)? = null
    var onVoice: (() -> Unit)? = null

    private val input = EditText(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        setTextColor(Ui.solidText(context, R.color.text))
        setHintTextColor(Ui.color(context, R.color.text_dim))
        hint = "example.com or search terms"
        isSingleLine = true
        imeOptions = EditorInfo.IME_ACTION_GO
        setPadding(Ui.dp(context, 20f), Ui.dp(context, 16f), Ui.dp(context, 20f), Ui.dp(context, 16f))
        background = Ui.cardBackground(context, 8f)
        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submit()
                true
            } else {
                false
            }
        }
    }

    private val voiceButton = Chip(context, "Voice search")
    private val goButton = Chip(context, "Go", accent = true)
    private val cancelButton = Chip(context, "Cancel")

    init {
        subtitle("Type with the on-screen keyboard, or use your remote's mic")
        addCustom(input)
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(context, 12f), 0, 0)
        }
        voiceButton.setOnClickListener { onVoice?.invoke() }
        goButton.setOnClickListener { submit() }
        cancelButton.setOnClickListener { }
        row.addView(voiceButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            rightMargin = Ui.dp(context, 12f)
        })
        row.addView(goButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            rightMargin = Ui.dp(context, 12f)
        })
        row.addView(cancelButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addCustom(row)
        rows += voiceButton
        rows += goButton
        rows += cancelButton
    }

    fun prefill(value: String) {
        input.hint = value.ifBlank { "example.com or search terms" }
        input.setText("")
    }

    fun onCancel(action: () -> Unit) {
        cancelButton.setOnClickListener { action() }
    }

    override fun focusFirst() {
        input.requestFocus()
    }

    private fun submit() {
        val value = input.text.toString().trim()
        if (value.isNotEmpty()) onSubmit?.invoke(value)
    }
}
