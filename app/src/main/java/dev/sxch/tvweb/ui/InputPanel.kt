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
        setTextColor(Ui.color(context, R.color.text))
        setHintTextColor(Ui.color(context, R.color.text_dim))
        hint = "example.com or search terms"
        isSingleLine = true
        imeOptions = EditorInfo.IME_ACTION_GO
        setPadding(Ui.dp(context, 20f), Ui.dp(context, 16f), Ui.dp(context, 20f), Ui.dp(context, 16f))
        background = Ui.rounded(context, R.color.surface_raised, 14f, 245)
        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submit()
                true
            } else {
                false
            }
        }
    }

    private val voiceButton = TvButton(context, R.drawable.ic_mic, "Voice")
    private val goButton = TvButton(context, R.drawable.ic_chevron_right, "Go", accent = true)
    private val cancelButton = TvButton(context, R.drawable.ic_close, "Cancel")

    init {
        subtitle("Type with the on-screen keyboard, or use the microphone")
        addCustom(input)
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, Ui.dp(context, 10f), 0, 0)
        }
        voiceButton.setOnClickListener { onVoice?.invoke() }
        goButton.setOnClickListener { submit() }
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
