package com.chronolock.blocking

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.chronolock.domain.Reason
import com.chronolock.domain.RuleDecision

/** Window adapter only. All restriction policy comes from the domain decision. */
class BlockOverlay(private val service: AccessibilityService, private val onHome: () -> Unit, private val onSettings: () -> Unit) {
    private val manager = service.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var heading: TextView? = null
    private var detail: TextView? = null
    val visible get() = root != null

    fun show(appName: String, decision: RuleDecision) {
        if (root == null) {
            val content = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(28), dp(48), dp(28), dp(48))
                setBackgroundColor(Color.rgb(16, 20, 31))
                isFocusableInTouchMode = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            fun label(text: String, size: Float, color: Int) = TextView(service).apply {
                this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(12))
                content.addView(this, LinearLayout.LayoutParams(-1, -2))
            }
            label("CHRONOLOCK", 14f, Color.rgb(184, 242, 126))
            heading = label("", 28f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD }
            detail = label("", 17f, Color.rgb(190, 198, 216))
            label("잠깐 멈추고, 지금 하고 싶은 일을 선택하세요.", 15f, Color.rgb(190, 198, 216))
            content.addView(Button(service).apply { text = "홈 화면으로 돌아가기"; setOnClickListener { onHome() } }, LinearLayout.LayoutParams(-1, dp(56)))
            content.addView(Button(service).apply { text = "내 제한 설정 보기"; setOnClickListener { onSettings() } }, LinearLayout.LayoutParams(-1, dp(56)))
            val parameters = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE)
            parameters.gravity = Gravity.TOP or Gravity.START
            manager.addView(content, parameters)
            root = content
            content.requestFocus()
        }
        heading?.text = "$appName\n지금은 쉬는 시간입니다"
        detail?.text = decision.reasons.joinToString("\n") { reason ->
            when (reason) {
                Reason.DAILY_USAGE -> "오늘 설정한 사용시간을 모두 사용했습니다."
                Reason.OPEN_COUNT -> "오늘 허용한 실행 횟수를 모두 사용했습니다."
                Reason.COOLDOWN -> decision.retryAfterSeconds?.let { "재실행까지 ${it}초 남았습니다." } ?: "재실행 대기 시간이 적용 중입니다."
                Reason.SESSION_USAGE -> "이번 사용시간 제한에 도달했습니다."
                Reason.SCHEDULE -> "사용하지 않기로 정한 시간대입니다."
                Reason.FOCUS -> "집중 시간이 진행 중입니다."
            }
        } + if (decision.reasons.any { it == Reason.DAILY_USAGE || it == Reason.OPEN_COUNT }) "\n일일 한도는 현지 시각 자정에 초기화됩니다." else ""
    }

    fun hide() {
        root?.let { manager.removeView(it) }
        root = null; heading = null; detail = null
    }
    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()
}
