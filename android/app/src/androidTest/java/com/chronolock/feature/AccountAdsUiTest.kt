package com.chronolock.feature

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.chronolock.account.AccountState
import com.chronolock.ads.AdState
import com.chronolock.billing.BillingState
import com.chronolock.billing.BillingStatus
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AccountAdsUiTest {
    @get:Rule val ui = createComposeRule()

    private fun show(state: MainState, banner: Boolean = false, signIn: () -> Unit = {}, deleteAccount: () -> Unit = {}) {
        ui.setContent { ChronoTheme {
            ChronoScreen(state.copy(loading = false), LocalDate.of(2026, 10, 18), 0, 3, {}, remember { SnackbarHostState() },
                { _, _ -> }, {}, {}, {}, {}, {},
                banner = if (banner) ({ Text("AD", Modifier.fillMaxWidth().height(50.dp).testTag("fake-banner")) }) else null,
                signIn = signIn, deleteAccount = deleteAccount)
        } }
    }

    private fun scrollTo(text: String) = ui.onNodeWithTag("main-list").performScrollToNode(hasText(text))

    @Test fun unconfiguredServices_hideAccountAndAdCards() {
        show(MainState())
        ui.onAllNodesWithText("Google로 로그인").assertCountEquals(0)
        ui.onAllNodesWithText("광고 개인정보 설정").assertCountEquals(0)
        ui.onAllNodesWithTag("fake-banner").assertCountEquals(0)
    }

    @Test fun signedOut_offersOptionalGoogleSignIn() {
        var clicked = false
        show(MainState(account = AccountState(configured = true)), signIn = { clicked = true })
        scrollTo("Google로 로그인")
        ui.onNodeWithText("Google로 로그인").performClick()
        assertTrue(clicked)
    }

    @Test fun signedIn_showsEmailAndDeletion() {
        var deleting = false
        show(MainState(account = AccountState(configured = true, signedIn = true, email = "a@example.com")), deleteAccount = { deleting = true })
        scrollTo("계정 삭제")
        ui.onNodeWithText("로그인됨: a@example.com").assertExists()
        ui.onNodeWithText("계정 삭제").performClick()
        assertTrue(deleting)
    }

    @Test fun adsCard_andBannerSlot() {
        show(MainState(ads = AdState(configured = true, canRequestAds = true, privacyOptionsRequired = true)), banner = true)
        // The banner sits at the end of Settings, not over the screen.
        ui.onNodeWithTag("main-list").performScrollToNode(hasTestTag("settings-ad"))
        ui.onNodeWithTag("fake-banner").assertIsDisplayed()
        scrollTo("광고 개인정보 설정")
        // Nothing to buy: the ad-free subscription is no longer sold.
        ui.onAllNodesWithText("광고 제거", substring = true).assertCountEquals(0)
    }

    @Test fun subscriber_seesManageInsteadOfBuy() {
        show(MainState(ads = AdState(configured = true), billing = BillingState(status = BillingStatus.READY, price = "₩990", adFree = true)))
        scrollTo("구독 관리")
        ui.onNodeWithText("광고 없이 이용 중입니다.").assertExists()
        ui.onNodeWithText("광고 제거 구독은 새로 판매하지 않아요. 지금 구독은 해지하기 전까지 그대로 유지돼요.").assertExists()
        ui.onAllNodesWithText("광고 제거 · 월 ₩990").assertCountEquals(0)
    }
}
