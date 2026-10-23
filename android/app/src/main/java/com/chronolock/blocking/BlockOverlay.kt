package com.chronolock.blocking

import android.accessibilityservice.AccessibilityService
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.chronolock.R
import com.chronolock.feature.UiText
import com.chronolock.feature.forLanguage
import com.chronolock.data.AppLanguage
import com.chronolock.data.AppTheme
import com.chronolock.domain.Reason
import com.chronolock.domain.RuleDecision

/** Window adapter only. All restriction policy comes from the domain decision. */
class BlockOverlay(private val service: AccessibilityService, private val onHome: () -> Unit, private val onSettings: () -> Unit) {
    private val manager = service.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var heading: TextView? = null
    private var detail: TextView? = null
    private var theme = AppTheme.DARK
    private var language = AppLanguage.KOREAN
    private var text = UiText(service.forLanguage(language))
    /** Redraws the current message, as a language change needs. */
    private var lastShown: (() -> Unit)? = null

    fun setLanguage(value: AppLanguage) {
        language = value
        text = UiText(service.forLanguage(value))
        val previous = lastShown
        if (visible && previous != null) { hide(); previous() }
    }
    val visible get() = root != null

    fun setTheme(value: AppTheme) {
        theme = value
        (root as? LinearLayout)?.let(::applyTheme)
    }

    private fun themeBackground() = android.graphics.drawable.GradientDrawable(
        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(theme.background.toInt(), (theme.gradientEnd ?: theme.background).toInt()))

    private fun applyTheme(content: LinearLayout) {
        content.background = themeBackground()
        for (index in 0 until content.childCount) {
            when (val child = content.getChildAt(index)) {
                is Button -> {
                    child.backgroundTintList = ColorStateList.valueOf(theme.primary.toInt())
                    child.setTextColor(theme.onPrimary.toInt())
                }
                is TextView -> child.setTextColor(when (index) {
                    0 -> theme.primary.toInt()
                    1 -> theme.text.toInt()
                    else -> theme.muted.toInt()
                })
            }
        }
    }

    private fun attach() {
        if (root == null) {
            val content = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(28), dp(48), dp(28), dp(48))
                background = themeBackground()
                isFocusableInTouchMode = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            fun label(text: String, size: Float, color: Int, font: Int = R.font.pretendard_regular) = TextView(service).apply {
                this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER
                typeface = androidx.core.content.res.ResourcesCompat.getFont(service, font)
                setPadding(0, dp(12), 0, dp(12))
                content.addView(this, LinearLayout.LayoutParams(-1, -2))
            }
            label("ChronoLock", 14f, theme.primary.toInt(), font = R.font.pretendard_medium)
            heading = label("", 28f, theme.text.toInt(), font = R.font.pretendard_bold)
            detail = label("", 17f, theme.muted.toInt())
            label(text("잠깐 멈추고, 지금 하고 싶은 일을 선택하세요."), 15f, theme.muted.toInt())
            content.addView(Button(service).apply { text = text("홈 화면으로 돌아가기"); setOnClickListener { onHome() } }, LinearLayout.LayoutParams(-1, dp(56)))
            content.addView(Button(service).apply { text = text("내 제한 설정 보기"); setOnClickListener { onSettings() } }, LinearLayout.LayoutParams(-1, dp(56)))
            applyTheme(content)
            val parameters = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE)
            parameters.gravity = Gravity.TOP or Gravity.START
            manager.addView(content, parameters)
            root = content
            content.requestFocus()
        }
    }

    /** A website blocked in a browser; [domain] is the entry the person added, not the page address. */
    fun showSite(domain: String) {
        attach()
        lastShown = { showSite(domain) }
        heading?.text = text("%1\$s\n지금은 열지 않기로 했어요", domain)
        detail?.text = text("차단 목록에 추가한 사이트입니다. 주소창의 도메인만 확인하며 방문 기록은 저장하지 않습니다.")
    }

    // Each reason is a complete localized resource; only line separators are concatenated.
    @android.annotation.SuppressLint("SetTextI18n")
    fun show(appName: String, decision: RuleDecision) {
        attach()
        lastShown = { show(appName, decision) }
        heading?.text = text("%1\$s\n지금은 쉬는 시간입니다", appName)
        detail?.text = decision.reasons.joinToString("\n") { reason ->
            when (reason) {
                Reason.DAILY_USAGE -> text("오늘 설정한 사용시간을 모두 사용했습니다.")
                Reason.OPEN_COUNT -> text("오늘 허용한 실행 횟수를 모두 사용했습니다.")
                Reason.COOLDOWN -> decision.retryAfterSeconds?.let { text("재실행까지 %1\$d초 남았습니다.", it) } ?: text("재실행 대기 시간이 적용 중입니다.")
                Reason.SESSION_USAGE -> text("이번 사용시간 제한에 도달했습니다.")
                Reason.SCHEDULE -> text("사용하지 않기로 정한 시간대입니다.")
                Reason.FOCUS -> text("집중 시간이 진행 중입니다.")
                Reason.GROUP_USAGE -> text("함께 묶은 앱들의 오늘 사용시간을 모두 사용했습니다.")
                Reason.GROUP_OPENS -> text("함께 묶은 앱들의 오늘 실행 횟수를 모두 사용했습니다.")
            }
        } + if (decision.reasons.any { it in setOf(Reason.DAILY_USAGE, Reason.OPEN_COUNT, Reason.GROUP_USAGE, Reason.GROUP_OPENS) }) "\n" + text("일일 한도는 현지 시각 자정에 초기화됩니다.") else ""
    }

    fun hide() {
        root?.let { manager.removeView(it) }
        root = null; heading = null; detail = null; lastShown = null
    }
    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()
}
