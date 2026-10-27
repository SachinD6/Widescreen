package dev.sxch.tvweb.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.sxch.tvweb.R
import dev.sxch.tvweb.data.Shortcut

class HomeView(context: Context) : LinearLayout(context) {

    var onNavigate: ((String) -> Unit)? = null
    var onSearch: (() -> Unit)? = null
    var onTabs: (() -> Unit)? = null
    var onBookmarks: (() -> Unit)? = null
    var onSettings: (() -> Unit)? = null
    var onRemoveShortcut: ((Shortcut) -> Unit)? = null

    private val grid = GridLayout(context).apply {
        columnCount = COLUMNS
        alignmentMode = GridLayout.ALIGN_BOUNDS
    }
    private val actionRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
    }
    private val tabsChip = Chip(context, "Tabs")

    init {
        orientation = VERTICAL
        setPadding(Ui.dp(context, 56f), Ui.dp(context, 40f), Ui.dp(context, 56f), Ui.dp(context, 36f))

        val header = TextView(context).apply {
            styleText(38f)
            setTextColor(Ui.solidText(context, R.color.accent))
            text = "TV Web"
        }
        val tagline = TextView(context).apply {
            styleText(15f, R.color.text_dim)
            text = "D-pad browsing, no ads, no clutter"
            setPadding(0, Ui.dp(context, 6f), 0, Ui.dp(context, 28f))
        }

        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(tagline, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(View(context), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        actionRow.addView(chip("Search", onClick = { onSearch?.invoke() }))
        tabsChip.setOnClickListener { onTabs?.invoke() }
        actionRow.addView(tabsChip, chipParams())
        actionRow.addView(chip("Bookmarks", onClick = { onBookmarks?.invoke() }))
        actionRow.addView(chip("Settings", onClick = { onSettings?.invoke() }))
        addView(actionRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun render(shortcuts: List<Shortcut>, tabCount: Int) {
        tabsChip.text = if (tabCount > 1) "Tabs ($tabCount)" else "Tabs"
        grid.removeAllViews()
        shortcuts.forEachIndexed { index, shortcut ->
            val card = shortcutCard(shortcut)
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / COLUMNS),
                GridLayout.spec(index % COLUMNS, 1f),
            ).apply {
                width = 0
                height = Ui.dp(context, 96f)
                setMargins(0, 0, Ui.dp(context, 14f), Ui.dp(context, 14f))
            }
            grid.addView(card, params)
        }
    }

    fun updateTabCount(tabCount: Int) {
        tabsChip.text = if (tabCount > 1) "Tabs ($tabCount)" else "Tabs"
    }

    fun focusDefault() {
        val firstCard = (0 until grid.childCount).map { grid.getChildAt(it) }.firstOrNull { it.isFocusable }
        (firstCard ?: tabsChip).requestFocus()
    }

    private fun shortcutCard(shortcut: Shortcut): View {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.cardBackground(context)
            setPadding(Ui.dp(context, 20f), Ui.dp(context, 16f), Ui.dp(context, 20f), Ui.dp(context, 16f))
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { onNavigate?.invoke(shortcut.url) }
            setOnLongClickListener {
                onRemoveShortcut?.invoke(shortcut)
                true
            }
        }
        val name = TextView(context).apply {
            styleText(21f)
            setTextColor(Ui.brightText(context))
            text = shortcut.title
            maxLines = 1
        }
        val host = TextView(context).apply {
            styleText(12f, R.color.text_dim)
            text = shortcut.url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
                .trimEnd('/')
                .take(38)
            maxLines = 1
            setPadding(0, Ui.dp(context, 6f), 0, 0)
        }
        card.addView(name, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        card.addView(host, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        return card
    }

    private fun chip(label: String, onClick: () -> Unit): Chip =
        Chip(context, label).apply {
            setOnClickListener { onClick() }
            layoutParams = chipParams()
        }

    private fun chipParams(): LayoutParams = LayoutParams(
        LayoutParams.WRAP_CONTENT,
        LayoutParams.WRAP_CONTENT,
    ).apply { rightMargin = Ui.dp(context, 12f) }

    private companion object {
        const val COLUMNS = 4
    }
}
