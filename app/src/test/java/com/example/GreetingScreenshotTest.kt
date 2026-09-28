package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.domain.model.Alert
import com.example.presentation.alert.AlertScreen
import com.example.presentation.alert.AlertUiState
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val dummyAlert = Alert(
        id = 1L,
        mailId = 1L,
        mailUid = 101L,
        senderName = "DevOps Alertmanager",
        senderAddress = "alerts@infrastructure.internal",
        subject = "[CRITICAL] Production Database Cluster unreachable",
        receivedTime = System.currentTimeMillis()
    )

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        AlertScreen(
            uiState = AlertUiState.Active(
                alert = dummyAlert,
                isRinging = true,
                isMuted = false,
                queueCount = 0
            ),
            onAcknowledge = {},
            onSnooze = { _, _ -> },
            onOpenMail = {},
            onMuteRingtone = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
