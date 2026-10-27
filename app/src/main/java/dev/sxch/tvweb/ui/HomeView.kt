package dev.sxch.tvweb.ui

import android.content.Context
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
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
        clipChildren = false
        clipToPadding = false
    }
    private val tabsButton = TvButton(context, R.drawable.ic_tabs, "Tabs")
    private val actionRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(Ui.dp(context, 48f), Ui.dp(context, 30f), Ui.dp(context, 48f), Ui.dp(context, 26f))

        val eyebrow = TextView(context).apply {
            styleText(12f, R.color.accent)
            text = "START PAGE"
            letterSpacing = 0.18f
        }
        val title = TextView(context).apply {
            styleText(40f)
            text = "Browse"
            setPadding(0, Ui.dp(context, 8f), 0, Ui.dp(context, 26f))
        }

        addView(eyebrow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(View(context), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        actionRow.addView(
            TvButton(context, R.drawable.ic_search, "Search").apply {
                setOnClickListener { onSearch?.invoke() }
            },
            actionParams(),
        )
        tabsButton.setOnClickListener { onTabs?.invoke() }
        actionRow.addView(tabsButton, actionParams())
        actionRow.addView(
            TvButton(context, R.drawable.ic_bookmark, "Bookmarks").apply {
                setOnClickListener { onBookmarks?.invoke() }
            },
            actionParams(),
        )
        actionRow.addView(
            TvButton(context, R.drawable.ic_sliders, "Settings").apply {
                setOnClickListener { onSettings?.invoke() }
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
        )
        addView(actionRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun render(shortcuts: List<Shortcut>, tabCount: Int) {
        updateTabCount(tabCount)
        grid.removeAllViews()
        shortcuts.forEachIndexed { index, shortcut ->
            val host = shortcut.url
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .trimEnd('/')
                .substringBefore('/')

            val card = TvCard(
                context,
                Ui.glyphForHost(host),
                shortcut.title,
            ) { onNavigate?.invoke(shortcut.url) }
            card.setOnLongClickListener {
                onRemoveShortcut?.invoke(shortcut)
                true
            }

            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / COLUMNS),
                GridLayout.spec(index % COLUMNS, 1f),
            ).apply {
                width = 0
                height = Ui.dp(context, 104f)
                setMargins(Ui.dp(context, 8f), Ui.dp(context, 8f), Ui.dp(context, 8f), Ui.dp(context, 8f))
            }
            grid.addView(card, params)
        }
    }

    fun updateTabCount(tabCount: Int) {
        tabsButton.setLabel(if (tabCount > 1) "Tabs $tabCount" else "Tabs")
    }

    fun focusDefault() {
        val first = (0 until grid.childCount)
            .map { grid.getChildAt(it) }
            .firstOrNull { it.isFocusable }
        (first ?: actionRow.getChildAt(0))?.requestFocus()
    }

    private fun actionParams(): LayoutParams =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            rightMargin = Ui.dp(context, 12f)
        }

    private companion object {
        const val COLUMNS = 4
    }
}
