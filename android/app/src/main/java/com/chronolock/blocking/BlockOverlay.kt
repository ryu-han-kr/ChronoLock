package com.chronolock.blocking

import android.accessibilityservice.AccessibilityService
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.chronolock.R
import com.chronolock.feature.UiText
import com.chronolock.feature.forLanguage
import com.chronolock.data.AppLanguage
import com.chronolock.data.AppTheme
import com.chronolock.data.MascotKind
import com.chronolock.data.Pose
import com.chronolock.domain.OpenReason
import com.chronolock.domain.Reason
import com.chronolock.domain.RuleDecision

/** Window adapter only. All restriction policy comes from the domain decision. */
class BlockOverlay(private val service: AccessibilityService, private val onHome: () -> Unit, private val onSettings: () -> Unit) {
    private val manager = service.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var heading: TextView? = null
    private var picture: ImageView? = null
    private var mascot = MascotKind.DEFAULT
    private var pose = Pose.LIMIT
    private var detail: TextView? = null
    private var settingsButton: Button? = null
    private var continueButton: Button? = null
    private var reasonTitle: TextView? = null
    private var reasonRow: LinearLayout? = null
    /** Records a reason for the app on screen; null where the screen does not ask (sites, pauses, check-ins). */
    private var onReason: ((OpenReason) -> Unit)? = null
    /** The app whose reason was already picked while this screen stayed up, so repeated redraws do not ask again. */
    private var answeredFor: String? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var countdown: Runnable? = null
    /** What is on screen: "block", "site", "pause:<package>" or "checkin:<package>"; lets the service avoid restarting a countdown. */
    var mode: String? = null
        private set
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

    /** The character on the block screen; it changes pose with what the screen is about. */
    fun setMascot(value: MascotKind) {
        mascot = value
        picture?.setImageResource(mascot.art(pose))
    }

    private fun showPose(value: Pose) {
        pose = value
        picture?.setImageResource(mascot.art(value))
    }

    private fun themeBackground() = android.graphics.drawable.GradientDrawable(
        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
        (theme.pageColors.takeIf { it.size > 1 } ?: listOf(theme.background, theme.background)).map { it.toInt() }.toIntArray())

    private fun applyTheme(content: LinearLayout) {
        content.background = themeBackground()
        for (index in 0 until content.childCount) {
            when (val child = content.getChildAt(index)) {
                is Button -> {
                    child.backgroundTintList = ColorStateList.valueOf(theme.primary.toInt())
                    child.setTextColor(theme.onPrimary.toInt())
                }
                // The reason buttons: lighter than the main actions, so going home stays the obvious choice.
                is LinearLayout -> for (i in 0 until child.childCount) (child.getChildAt(i) as? Button)?.apply {
                    backgroundTintList = ColorStateList.valueOf(theme.primary.toInt() and 0x00FFFFFF or 0x33000000)
                    setTextColor(theme.text.toInt())
                }
                is TextView -> child.setTextColor(when (index) {
                    1 -> theme.primary.toInt()
                    2 -> theme.text.toInt()
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
            // Index 0; applyTheme colours the labels after it by position.
            picture = ImageView(service).apply { setImageResource(mascot.art(pose)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            content.addView(picture, LinearLayout.LayoutParams(dp(132), dp(132)).apply { gravity = Gravity.CENTER_HORIZONTAL })
            label("ChronoLock", 14f, theme.primary.toInt(), font = R.font.pretendard_medium)
            heading = label("", 28f, theme.text.toInt(), font = R.font.pretendard_bold)
            detail = label("", 17f, theme.muted.toInt())
            label(text("잠깐 멈추고, 지금 하고 싶은 일을 골라 보세요."), 15f, theme.muted.toInt())
            // A small alternative to reaching for the app again; it changes daily.
            label(text("대신 이건 어때요? %1\$s", text(SUGGESTIONS[(java.time.LocalDate.now().toEpochDay() % SUGGESTIONS.size).toInt()])), 15f, theme.primary.toInt())
            reasonTitle = label("", 15f, theme.muted.toInt()).apply { visibility = View.GONE }
            reasonRow = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL; visibility = View.GONE
                OpenReason.entries.forEach { reason ->
                    addView(Button(service).apply {
                        text = text(reason.label); textSize = 13f; isAllCaps = false; setPadding(dp(4), 0, dp(4), 0)
                        setOnClickListener { pickReason(reason) }
                    }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
                }
            }
            content.addView(reasonRow, LinearLayout.LayoutParams(-1, -2))
            content.addView(Button(service).apply { text = text("홈 화면으로 돌아가기"); setOnClickListener { onHome() } }, LinearLayout.LayoutParams(-1, dp(56)))
            settingsButton = Button(service).apply { text = text("내 제한 설정 보기"); setOnClickListener { onSettings() } }
            content.addView(settingsButton, LinearLayout.LayoutParams(-1, dp(56)))
            continueButton = Button(service).apply { visibility = View.GONE }
            content.addView(continueButton, LinearLayout.LayoutParams(-1, dp(56)))
            applyTheme(content)
            val parameters = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE)
            parameters.gravity = Gravity.TOP or Gravity.START
            // Cover the punch-hole band too, so no strip of the blocked app stays touchable in landscape.
            parameters.layoutInDisplayCutoutMode = if (android.os.Build.VERSION.SDK_INT >= 30)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            manager.addView(content, parameters)
            root = content
            content.requestFocus()
        }
    }

    /** A website blocked in a browser; [domain] is the entry the person added, not the page address. */
    fun showSite(domain: String) {
        attach()
        mode = "site"; stopCountdown(); continueButton?.visibility = View.GONE; askReason(null, null)
        lastShown = { showSite(domain) }
        showPose(Pose.LIMIT)
        heading?.text = text("%1\$s\n지금은 열지 않기로 했어요", domain)
        detail?.text = text("차단 목록에 추가한 사이트입니다. 방문 기록은 저장하지 않습니다.")
    }

    // Each reason is a complete localized resource; only line separators are concatenated.
    @android.annotation.SuppressLint("SetTextI18n")
    fun show(appName: String, decision: RuleDecision, resistedToday: Int = 0, allowSettings: Boolean = true,
        note: String? = null, onReason: ((OpenReason) -> Unit)? = null) {
        attach()
        lastShown = { show(appName, decision, resistedToday, allowSettings, note, onReason) }
        mode = "block"; stopCountdown(); continueButton?.visibility = View.GONE
        askReason(appName, onReason)
        showPose(when {
            Reason.FOCUS in decision.reasons -> Pose.FOCUS
            Reason.SCHEDULE in decision.reasons -> Pose.SLEEP
            decision.reasons == listOf(Reason.COOLDOWN) -> Pose.TIMER
            else -> Pose.LIMIT
        })
        // Under strict mode or a lock the settings shortcut would only lead to changes that cannot be made now.
        settingsButton?.visibility = if (allowSettings) View.VISIBLE else View.GONE
        heading?.text = text("%1\$s\n지금은 쉬는 시간이에요", appName)
        detail?.text = decision.reasons.joinToString("\n") { reason ->
            when (reason) {
                Reason.DAILY_USAGE -> text("오늘 정한 사용 시간을 다 썼어요.")
                Reason.OPEN_COUNT -> text("오늘 허용한 실행 횟수를 다 썼어요.")
                Reason.COOLDOWN -> decision.retryAfterSeconds?.let { text("다시 열기까지 %1\$d초 남았어요.", it) } ?: text("다시 열기 전 대기 시간이에요.")
                Reason.SESSION_USAGE -> text("이번에 쓸 수 있는 시간을 다 썼어요.")
                Reason.SCHEDULE -> text("쓰지 않기로 정한 시간대예요.")
                Reason.FOCUS -> text("집중 시간이에요.")
                Reason.GROUP_USAGE -> text("그룹의 오늘 사용 시간을 다 썼어요.")
                Reason.GROUP_OPENS -> text("그룹의 오늘 실행 횟수를 다 썼어요.")
            }
        } + (if (decision.reasons.any { it in setOf(Reason.DAILY_USAGE, Reason.OPEN_COUNT, Reason.GROUP_USAGE, Reason.GROUP_OPENS) }) "\n" + text("하루 한도는 자정에 다시 채워져요.") else "") +
            (if (resistedToday > 0) "\n\n" + text("오늘 %1\$d번 참았어요. 잘하고 있어요!", resistedToday) else "") +
            // The person's own words to themselves, quoted as they wrote them.
            (if (!note.isNullOrBlank()) "\n\n“$note”" else "")
    }

    /**
     * The breathing pause before [packageName] opens: [seconds] to wait, then "open anyway" calls [proceed].
     * Home stays available the whole time.
     */
    fun showPause(packageName: String, appName: String, seconds: Int, opensToday: Int, proceed: () -> Unit) {
        attach()
        lastShown = { showPause(packageName, appName, seconds, opensToday, proceed) }
        mode = "pause:$packageName"; askReason(null, null)
        showPose(Pose.REST)
        settingsButton?.visibility = View.GONE
        heading?.text = text("숨 한 번 쉬어요")
        detail?.text = text("%1\$s · 오늘 %2\$d번째 열기예요. 지금 꼭 필요한가요?", appName, opensToday + 1)
        val button = continueButton ?: return
        button.visibility = View.VISIBLE
        button.setOnClickListener { proceed() }
        stopCountdown()
        var left = seconds
        val tick = object : Runnable {
            override fun run() {
                button.isEnabled = left <= 0
                button.text = if (left > 0) text("그래도 열기 (%1\$d)", left) else text("그래도 열기")
                if (left-- > 0) handler.postDelayed(this, 1000)
            }
        }
        countdown = tick
        tick.run()
    }

    /** After [minutes] of continuous use: keep going (calls [proceed]) or go home. */
    fun showCheckIn(packageName: String, appName: String, minutes: Int, proceed: () -> Unit) {
        attach()
        lastShown = { showCheckIn(packageName, appName, minutes, proceed) }
        mode = "checkin:$packageName"; askReason(null, null)
        showPose(Pose.WARNING)
        stopCountdown()
        settingsButton?.visibility = View.GONE
        heading?.text = text("%1\$d분 지났어요", minutes)
        detail?.text = text("%1\$s, 계속 쓸까요?", appName)
        continueButton?.apply { visibility = View.VISIBLE; isEnabled = true; text = text("계속 쓰기"); setOnClickListener { proceed() } }
    }

    private fun stopCountdown() { countdown?.let(handler::removeCallbacks); countdown = null }

    /** One tap on why the app was opened; after it, a thank-you line stays until the screen goes away. */
    private fun askReason(key: String?, record: ((OpenReason) -> Unit)?) {
        onReason = record
        val asking = record != null && key != null && answeredFor != key
        if (record == null || key == null) answeredFor = null
        reasonRow?.visibility = if (asking) View.VISIBLE else View.GONE
        reasonTitle?.visibility = if (record != null && key != null) View.VISIBLE else View.GONE
        reasonTitle?.text = if (asking) text("왜 열려고 했나요?") else text("기록했어요. 히스토리 탭에서 볼 수 있어요.")
        if (asking) pendingKey = key
    }

    private var pendingKey: String? = null

    private fun pickReason(reason: OpenReason) {
        val record = onReason ?: return
        answeredFor = pendingKey
        record(reason)
        askReason(pendingKey, record)
    }

    private companion object {
        val SUGGESTIONS = listOf("물 한 잔 마시기", "1분 스트레칭", "창밖을 20초 바라보기", "할 일 하나 적기", "심호흡 다섯 번", "자리에서 일어나 걷기")
    }

    fun hide() {
        stopCountdown()
        root?.let { manager.removeView(it) }
        root = null; heading = null; picture = null; detail = null; lastShown = null; settingsButton = null; continueButton = null; mode = null
        reasonTitle = null; reasonRow = null; onReason = null; answeredFor = null; pendingKey = null
    }
    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()
}
