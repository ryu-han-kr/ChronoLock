package com.chronolock.feature

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronolock.data.AppLanguage

internal val LocalLanguage = staticCompositionLocalOf { AppLanguage.KOREAN }

internal fun Context.forLanguage(language: AppLanguage): Context = createConfigurationContext(
    Configuration(resources.configuration).apply { setLocales(LocaleList(language.locale())) })

@Composable
internal fun LocalizedApp(language: AppLanguage, content: @Composable () -> Unit) {
    val original = LocalContext.current
    val configuration = LocalConfiguration.current
    val localized = remember(original, configuration, language) { original.forLanguage(language) }
    CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration,
        LocalLanguage provides language, content = content)
}

internal class UiText(private val context: Context) {
    val locale get() = context.resources.configuration.locales[0]
    operator fun invoke(source: String, vararg args: Any): String {
        translationKeys[source]?.let { return context.getString(it, *args) }
        // Validation stays platform-independent in the domain module; translate its structured messages here.
        Regex("^(.+): 1~([0-9]+) 범위의 정수를 입력해 주세요\\.$").matchEntire(source)?.let {
            return invoke("%1\$s: 1~%2\$s 범위의 정수를 입력해 주세요.", invoke(it.groupValues[1]), it.groupValues[2])
        }
        Regex("^(.+): (.+) 항목에 0~([0-9]+)의 정수를 입력해 주세요\\.$").matchEntire(source)?.let {
            return invoke("%1\$s: %2\$s 항목에 0~%3\$s의 정수를 입력해 주세요.", invoke(it.groupValues[1]), invoke(it.groupValues[2]), it.groupValues[3])
        }
        Regex("^(.+)은 1초~24시간으로 설정해 주세요\\. 제한하지 않으려면 세 칸을 모두 비워 주세요\\.$").matchEntire(source)?.let {
            return invoke("%1\$s은 1초~24시간으로 설정해 주세요. 제한하지 않으려면 세 칸을 모두 비워 주세요.", invoke(it.groupValues[1]))
        }
        return source
    }
}

@Composable internal fun uiText(): UiText {
    val context = LocalContext.current
    val language = LocalLanguage.current
    return remember(context, language) { UiText(context.forLanguage(language)) }
}
@Composable internal fun tr(source: String, vararg args: Any): String = uiText()(source, *args)

@Composable
internal fun LanguagePicker(selected: AppLanguage, select: (AppLanguage) -> Unit) {
    CardBox {
        Text(tr("언어"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(tr("화면 언어를 선택하세요. 다음 실행에도 유지됩니다."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.selectableGroup()) {
            AppLanguage.values().forEach { language ->
                Row(Modifier.fillMaxWidth().selectable(selected == language, role = Role.RadioButton,
                    onClick = { select(language) }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == language, onClick = null)
                    Text(if (language == AppLanguage.SYSTEM) tr("기기 언어 따르기") else language.nativeName)
                }
            }
        }
    }
}
