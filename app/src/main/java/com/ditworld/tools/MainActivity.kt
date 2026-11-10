package com.ditworld.tools

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(
        s: String, sp: Float, colorRes: Int, bold: Boolean = false, mono: Boolean = false,
    ) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(getColor(colorRes))
        typeface = when {
            mono -> Typeface.MONOSPACE
            bold -> Typeface.DEFAULT_BOLD
            else -> Typeface.DEFAULT
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        column.addView(text("DIT WORLD", 12f, R.color.orange, mono = true))
        column.addView(text("Tools", 34f, R.color.text, bold = true))
        column.addView(
            text("Piccoli, veloci, gratuiti. Scegli uno strumento.", 14f, R.color.dim).apply {
                setPadding(0, dp(4), 0, dp(20))
            }
        )

        Tools.all.forEach { tool -> column.addView(card(tool)) }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.bg))
            isFillViewport = true
            // Rispetta status bar / barra di navigazione
            fitsSystemWindows = true
            addView(column)
        })
    }

    private fun card(tool: Tool): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.card_bg)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            isFocusable = true
            contentDescription = tool.title
            setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, ToolActivity::class.java)
                        .putExtra(ToolActivity.EXTRA_TITLE, tool.title)
                        .putExtra(ToolActivity.EXTRA_URL, tool.url)
                )
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        card.addView(text(tool.number, 14f, R.color.orange, mono = true).apply {
            minWidth = dp(34)
        })

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(text(tool.title, 17f, R.color.text, bold = true))
        info.addView(text(tool.description, 13f, R.color.dim).apply { setPadding(0, dp(4), 0, 0) })
        card.addView(info)

        card.addView(text("→", 20f, R.color.cyan).apply { setPadding(dp(12), 0, 0, 0) })
        return card
    }
}
