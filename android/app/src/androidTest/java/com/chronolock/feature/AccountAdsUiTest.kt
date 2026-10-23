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

    private fun show(state: MainState, banner: Boolean = false, signIn: () -> Unit = {}, deleteAccount: () -> Unit = {}, retryBilling: () -> Unit = {}) {
        ui.setContent { ChronoTheme {
            ChronoScreen(state.copy(loading = false), LocalDate.of(2026, 10, 18), 0, 3, {}, remember { SnackbarHostState() },
                { _, _ -> }, {}, {}, {}, {}, {},
                banner = if (banner) ({ Text("AD", Modifier.fillMaxWidth().height(50.dp).testTag("fake-banner")) }) else null,
                signIn = signIn, deleteAccount = deleteAccount, retryBilling = retryBilling)
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
        ui.onNodeWithTag("fake-banner").assertIsDisplayed()
        scrollTo("광고 개인정보 설정")
        ui.onNodeWithText("광고 제거 상품을 확인하는 중입니다.").assertExists()
        ui.onAllNodesWithText("다시 시도").assertCountEquals(0)
    }

    @Test fun unavailablePlan_explainsAndRetries() {
        var retried = false
        show(MainState(ads = AdState(configured = true), billing = BillingState(status = BillingStatus.UNAVAILABLE)), retryBilling = { retried = true })
        scrollTo("다시 시도")
        ui.onNodeWithText("광고 제거 상품을 불러오지 못했습니다. Play 스토어에서 설치한 앱인지 확인해 주세요.").assertExists()
        ui.onNodeWithText("다시 시도").performClick()
        assertTrue(retried)
    }

    @Test fun offlinePlan_asksToReconnect() {
        show(MainState(ads = AdState(configured = true), billing = BillingState(status = BillingStatus.OFFLINE)))
        scrollTo("다시 시도")
        ui.onNodeWithText("Google Play에 연결하지 못해 광고 제거 상품을 불러오지 못했습니다. 네트워크를 확인한 뒤 다시 시도해 주세요.").assertExists()
    }

    @Test fun availablePlan_opensPurchase() {
        var buying = false
        ui.setContent { ChronoTheme {
            ChronoScreen(MainState(loading = false, ads = AdState(configured = true), billing = BillingState(status = BillingStatus.READY, price = "₩990")),
                LocalDate.of(2026, 10, 18), 0, 3, {}, remember { SnackbarHostState() }, { _, _ -> }, {}, {}, {}, {}, {},
                buyAdFree = { buying = true })
        } }
        scrollTo("광고 제거 · 월 ₩990")
        ui.onNodeWithText("광고 제거 · 월 ₩990").performClick()
        assertTrue(buying)
    }

    @Test fun subscriber_seesManageInsteadOfBuy() {
        show(MainState(ads = AdState(configured = true), billing = BillingState(status = BillingStatus.READY, price = "₩990", adFree = true)))
        scrollTo("구독 관리")
        ui.onNodeWithText("광고 없이 이용 중입니다.").assertExists()
        ui.onAllNodesWithText("광고 제거 · 월 ₩990").assertCountEquals(0)
    }
}
