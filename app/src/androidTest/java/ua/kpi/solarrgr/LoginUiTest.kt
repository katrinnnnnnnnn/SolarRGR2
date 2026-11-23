package ua.kpi.solarrgr

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class LoginUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun loginScreenIsShown() {
        rule.onNodeWithText("Вхід (навчальна автентифікація)").assertIsDisplayed()
    }

    @Test fun wrongPasswordShowsError() {
        rule.onNodeWithText("Пароль").performTextInput("bad")
        rule.onNodeWithText("Увійти").performClick()
        rule.onNodeWithText("Невірний пароль").assertIsDisplayed()
    }

    @Test fun adminLoginOpensForecast() {
        rule.onNodeWithText("Пароль").performTextInput("solar1234")
        rule.onNodeWithText("Увійти").performClick()
        rule.onNodeWithText("Прогноз генерації (Open-Meteo)").assertIsDisplayed()
    }
}
