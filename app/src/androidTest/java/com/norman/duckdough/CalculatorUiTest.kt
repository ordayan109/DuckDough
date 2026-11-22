package com.norman.duckdough

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class CalculatorUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun ingredientEditingAndDiameterModeWorkInTheNativeApp() {
        compose.onNodeWithTag("recipe_regular").performClick()
        compose.onNodeWithTag("reset").performClick()
        compose.onNodeWithTag("amount_flour").performScrollTo().performTextReplacement("1000")
        compose.onNodeWithTag("amount_water").assertTextEquals("620")
        compose.onNodeWithTag("amount_yeast").assertTextEquals("4")
        compose.onNodeWithTag("amount_salt").assertTextEquals("25")
        compose.onNodeWithTag("amount_count").performScrollTo().performTextReplacement("0")
        compose.onNodeWithTag("input_error").assertExists()
        compose.onNodeWithTag("copy").assertIsNotEnabled()
        compose.onNodeWithTag("amount_count").performTextReplacement("6")
        compose.onNodeWithTag("mode_diameter").performScrollTo().performClick()
        compose.onNodeWithTag("amount_diameter").performScrollTo().performTextReplacement("40,64")
        compose.onNodeWithTag("amount_flour").assertTextEquals("1705")
        compose.onNodeWithTag("amount_water").assertTextEquals("1057")
        compose.onNodeWithTag("amount_weight").assertTextEquals("483.63")
        compose.onNodeWithTag("recipe_gluten_free").performScrollTo().performClick()
        compose.onNodeWithTag("amount_flour").assertTextEquals("1998")
        compose.onNodeWithTag("amount_weight").assertTextEquals("626.67")
        compose.onNodeWithTag("recipe_regular").performScrollTo().performClick()
        compose.onNodeWithTag("reset").performClick()
        compose.onNodeWithTag("amount_weight").performScrollTo().performTextReplacement("250")
        compose.onNodeWithTag("amount_weight").performImeAction()
        compose.onNodeWithTag("reset").performScrollTo()
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir,"duckdough-screen.png").outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,output)
        }
    }
}
