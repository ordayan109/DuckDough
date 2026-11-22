package com.norman.duckdough

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val Cream = Color(0xFFFBF6EB)
private val Paper = Color(0xFFFFFEFB)
private val Tomato = Color(0xFFAD3C2E)
private val Basil = Color(0xFF285442)
private val Ink = Color(0xFF273A31)
private val Muted = Color(0xFF69716A)
private val Line = Color(0xFFE4DFD3)
private val PaleGreen = Color(0xFFE8EFE5)

internal fun displayNumber(value: Double): String = DecimalFormat(
    if (value < 1.0) "0.####" else "0.##", DecimalFormatSymbols(Locale.US)
).format(value)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Cream.toArgb(), Cream.toArgb()),
            navigationBarStyle = SystemBarStyle.light(Cream.toArgb(), Cream.toArgb())
        )
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Tomato, onPrimary = Paper, secondary = Basil,
                background = Cream, surface = Cream, onSurface = Ink,
                onBackground = Ink, outline = Line
            )) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    DuckDoughApp()
                }
            }
        }
    }
}

@Composable
private fun DuckDoughApp() {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val preferences = remember { context.getSharedPreferences("batch", Context.MODE_PRIVATE) }
    var diameterMode by rememberSaveable { mutableStateOf(preferences.getBoolean("diameterMode", false)) }
    var diameterCm by rememberSaveable { mutableStateOf(preferences.getString("diameter", "30")?.toDoubleOrNull()?.takeIf { it in 20.0..60.0 } ?: 30.0) }
    var thickness by rememberSaveable { mutableStateOf(runCatching { Thickness.valueOf(preferences.getString("thickness", "REGULAR")!!) }.getOrDefault(Thickness.REGULAR)) }
    var batch by remember {
        val recipe = AppRecipes.all.firstOrNull { it.id == preferences.getString("recipe", null) } ?: AppRecipes.primary
        val flour = preferences.getString("flour", null)?.toDoubleOrNull()
        val count = preferences.getInt("pizzas", 4)
        mutableStateOf(runCatching { if (diameterMode) WebsiteSizing.forDiameter(recipe, count, diameterCm, thickness)
            else DoughBatch(recipe, flour ?: (1000.0 / recipe.massFactor), count) }
            .getOrElse { DoughMath.forPizzas(recipe, 4, 250.0) })
    }
    var activeField by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun save(next: DoughBatch) {
        batch = next
        preferences.edit().putString("recipe", next.recipe.id).putString("flour", next.flourGrams.toString())
            .putInt("pizzas", next.pizzas).putBoolean("diameterMode", diameterMode)
            .putString("diameter", diameterCm.toString()).putString("thickness", thickness.name).apply()
    }
    fun value(id: String): String = if (activeField == id) draft else displayNumber(when (id) {
        "count" -> batch.pizzas.toDouble()
        "weight" -> batch.ballGrams
        "diameter" -> diameterCm
        else -> batch.grams(id)
    })
    fun change(id: String, text: String) {
        activeField = id
        draft = text
        val amount = text.trim().replace(',', '.').toDoubleOrNull()
        if (amount == null || !amount.isFinite() || amount <= 0 || amount > 1e7 ||
            (id == "count" && (amount % 1.0 != 0.0 || amount > 100)) || (id == "diameter" && amount !in 20.0..60.0)) {
            error = when(id) { "count" -> "יש להזין מספר פיצות שלם בין 1 ל־100."; "diameter" -> "יש להזין קוטר בין 20 ל־60 ס״מ."; else -> "יש להזין כמות תקינה, גדולה מאפס." }
            return
        }
        val next = runCatching { when (id) {
            "count" -> if (diameterMode) WebsiteSizing.forDiameter(batch.recipe, amount.toInt(), diameterCm, thickness)
                else DoughMath.forPizzas(batch.recipe, amount.toInt(), batch.ballGrams)
            "diameter" -> WebsiteSizing.forDiameter(batch.recipe, batch.pizzas, amount, thickness)
            "weight" -> DoughMath.forPizzas(batch.recipe, batch.pizzas, amount)
            else -> DoughMath.fromIngredient(batch.recipe, batch.pizzas, id, amount)
        } }.getOrNull()
        if (next == null) { error = "הכמות גדולה מדי לחישוב. יש להזין כמות קטנה יותר."; return }
        if (id == "diameter") diameterCm = amount
        else if (id != "count") diameterMode = false
        error = null
        save(next)
    }
    fun focused(id: String, hasFocus: Boolean) {
        if (hasFocus && activeField != id) {
            activeField = null
            draft = value(id)
            activeField = id
            error = null
        } else if (!hasFocus && activeField == id && error == null) activeField = null
    }
    fun applyBatch(next: DoughBatch) {
        focus.clearFocus()
        activeField = null
        error = null
        save(next)
    }
    fun recipeText(): String = buildString {
        appendLine("DuckDough — המתכון שלך")
        appendLine(batch.recipe.name)
        if (diameterMode) appendLine("קוטר ${displayNumber(diameterCm)} ס״מ · בצק ${thickness.label}")
        appendLine("${batch.pizzas} כדורים × ${displayNumber(batch.ballGrams)} גרם")
        appendLine()
        batch.recipe.ingredients.forEach { appendLine("${it.name}: ${displayNumber(batch.grams(it.id))} גרם") }
        appendLine()
        appendLine("סה״כ בצק: ${displayNumber(batch.totalGrams)} גרם")
        append("פותח באהבה על ידי נורמן")
    }

    Surface(color = Cream, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                      Row {
                        Text("DuckDough", fontFamily = FontFamily.Serif, fontSize = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                        Text(".", color = Tomato, fontFamily = FontFamily.Serif, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                      }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("קמח. מים. קוואק.", color = Tomato, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("הערב אוכלים\nפיצה טובה.", fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
                        Text("אתם בוחרים כמה.\nהשף דואג לכמויות.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                    Image(painterResource(R.drawable.duck_chef), contentDescription = "ברווז שף לבן עם מקור צהוב מחזיק פיצה",
                        modifier = Modifier.width(115.dp).height(145.dp), contentScale = ContentScale.Fit)
                }
                HorizontalDivider(color = Line)
                if (AppRecipes.all.size > 1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppRecipes.all.forEach { recipe ->
                            FilterChip(selected = batch.recipe.id == recipe.id,
                                onClick = { applyBatch(if (diameterMode) WebsiteSizing.forDiameter(recipe, batch.pizzas, diameterCm, thickness)
                                    else DoughMath.forPizzas(recipe, batch.pizzas, batch.ballGrams)) },
                                label = { Text(recipe.name, fontSize = 12.sp) }, modifier = Modifier.testTag("recipe_${recipe.id}"))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("מתכננים את הבצק", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { diameterMode=false; applyBatch(DoughMath.forPizzas(batch.recipe, 4, 250.0)) }, modifier = Modifier.testTag("reset")) { Text("איפוס", fontSize = 13.sp) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !diameterMode, onClick = { diameterMode=false; applyBatch(DoughMath.forPizzas(batch.recipe, batch.pizzas, batch.ballGrams)) },
                        label = { Text("לפי משקל כדור") }, modifier = Modifier.testTag("mode_weight"))
                    FilterChip(selected = diameterMode, onClick = { diameterMode=true; applyBatch(WebsiteSizing.forDiameter(batch.recipe, batch.pizzas, diameterCm, thickness)) },
                        label = { Text("לפי קוטר ועובי") }, modifier = Modifier.testTag("mode_diameter"))
                }
                if (diameterMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("קוטר הפיצה", Modifier.weight(1f), color = Muted, fontSize = 13.sp)
                            Box(Modifier.width(90.dp).background(Paper,RoundedCornerShape(10.dp)).border(1.dp,Line,RoundedCornerShape(10.dp))) {
                                QuantityInput("diameter",value("diameter"),{change("diameter",it)},{focused("diameter",it)},Modifier.fillMaxWidth())
                            }
                            Text(" ס״מ",color=Muted,fontSize=12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Thickness.entries.forEach { choice ->
                                FilterChip(selected=thickness==choice,onClick={thickness=choice;applyBatch(WebsiteSizing.forDiameter(batch.recipe,batch.pizzas,diameterCm,choice))},
                                    label={Text(choice.label)},modifier=Modifier.testTag("thickness_${choice.name.lowercase()}"))
                            }
                        }
                        Text("קוטר ועובי נותנים הערכת משקל. הכמויות מחושבות לפי מתכון Dough Guy.",color=Muted,fontSize=11.sp,lineHeight=16.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1.08f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("מספר פיצות", color = Muted, fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).background(Paper, RoundedCornerShape(12.dp))
                            .border(1.dp, if (activeField == "count" && error != null) Tomato else Line, RoundedCornerShape(12.dp)),
                            verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { applyBatch(if(diameterMode) WebsiteSizing.forDiameter(batch.recipe,batch.pizzas+1,diameterCm,thickness) else DoughMath.forPizzas(batch.recipe, batch.pizzas + 1, batch.ballGrams)) },
                                enabled = batch.pizzas < 100, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(38.dp).height(48.dp)) { Text("+", color = Basil, fontSize = 22.sp) }
                            QuantityInput("count", value("count"), { change("count", it) }, { focused("count", it) }, Modifier.weight(1f), true)
                            TextButton(onClick = { applyBatch(if(diameterMode) WebsiteSizing.forDiameter(batch.recipe,batch.pizzas-1,diameterCm,thickness) else DoughMath.forPizzas(batch.recipe, batch.pizzas - 1, batch.ballGrams)) },
                                enabled = batch.pizzas > 1, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(38.dp).height(48.dp)) { Text("−", color = Basil, fontSize = 22.sp) }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if(diameterMode) "משקל כדור (מחושב)" else "משקל כדור", color = Muted, fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).background(Paper, RoundedCornerShape(12.dp))
                            .border(1.dp, if (activeField == "weight" && error != null) Tomato else Line, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            QuantityInput("weight", value("weight"), { change("weight", it) }, { focused("weight", it) }, Modifier.weight(1f),readOnly=diameterMode)
                            Text("גרם", fontSize = 12.sp, color = Muted)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().background(PaleGreen, RoundedCornerShape(12.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("סה״כ בצק", Modifier.weight(1f), color = Basil, fontSize = 13.sp)
                    Text(displayNumber(batch.totalGrams), color = Basil, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("total"))
                    Text(" גרם", color = Basil, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("מה נכנס לקערה?", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("אפשר לערוך כל כמות", color = Muted, fontSize = 11.sp)
                }
                Column(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(16.dp)).border(1.dp, Line, RoundedCornerShape(16.dp)).padding(horizontal = 13.dp, vertical = 3.dp)) {
                    batch.recipe.ingredients.forEachIndexed { index, ingredient ->
                        if (index > 0) HorizontalDivider(color = Line.copy(alpha = 0.7f))
                        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(ingredient.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                if (ingredient.note.isNotEmpty()) Text(ingredient.note, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.width(87.dp).heightIn(min = 44.dp).background(Cream, RoundedCornerShape(9.dp))
                                    .border(1.dp, if (activeField == ingredient.id && error != null) Tomato else Line, RoundedCornerShape(9.dp))) {
                                    QuantityInput(ingredient.id, value(ingredient.id), { change(ingredient.id, it) }, { focused(ingredient.id, it) }, Modifier.fillMaxWidth())
                                }
                                Text("גרם", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                if (error != null) Text(error!!, color = Tomato, fontSize = 12.sp, modifier = Modifier.testTag("input_error"))
                Text("${batch.pizzas} כדורי בצק · ${displayNumber(batch.ballGrams)} גרם לכדור", Modifier.fillMaxWidth(), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                Text("עריכת מצרך מעבירה לחישוב לפי משקל. ייתכנו הבדלי עיגול קטנים בתצוגה.",color=Muted,fontSize=11.sp,lineHeight=16.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val hydration = batch.recipe.ingredients.first { it.id == "water" }.percent
                    Box(Modifier.size(46.dp).background(Basil, RoundedCornerShape(14.dp, 14.dp, 14.dp, 3.dp)), contentAlignment = Alignment.Center) {
                        Text("${displayNumber(hydration)}%", color = Paper, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Column { Text("מים ביחס לקמח", fontSize = 12.sp); Text("אותו מתכון, בכל כמות.", color = Muted, fontSize = 11.sp) }
                }
                Button(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("DuckDough", recipeText()))
                    Toast.makeText(context, "המתכון הועתק. עכשיו נשאר להכין פיצה.", Toast.LENGTH_SHORT).show()
                }, enabled = error == null, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("copy"), shape = RoundedCornerShape(12.dp)) {
                    Text("העתקת המתכון", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, recipeText())
                    context.startActivity(Intent.createChooser(intent, "שיתוף המתכון"))
                }, enabled = error == null, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp), shape = RoundedCornerShape(12.dp)) { Text("שיתוף המתכון") }
                Text("פותח באהבה על ידי נורמן", Modifier.fillMaxWidth().padding(top = 8.dp), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                Row(Modifier.width(54.dp).height(4.dp).align(Alignment.CenterHorizontally)) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(Basil)); Box(Modifier.weight(1f).fillMaxHeight().background(Paper)); Box(Modifier.weight(1f).fillMaxHeight().background(Tomato))
                }
            }
        }
    }
}

@Composable
private fun QuantityInput(id: String, value: String, change: (String) -> Unit, focused: (Boolean) -> Unit, modifier: Modifier = Modifier, integer: Boolean = false, readOnly: Boolean = false) {
    val focus = LocalFocusManager.current
    val accessibleLabel = when (id) {
        "count" -> "מספר פיצות"
        "weight" -> "משקל כדור בגרמים"
        "diameter" -> "קוטר הפיצה בסנטימטרים"
        else -> "כמות " + when (id) {
            "flour" -> "קמח"
            "water" -> "מים"
            "salt" -> "מלח"
            "yeast" -> "שמרים יבשים"
            "sugar" -> "סוכר"
            "oil" -> "שמן זית"
            else -> id
        } + " בגרמים"
    }
    BasicTextField(value, change, modifier = modifier.heightIn(min = 44.dp).wrapContentHeight(Alignment.CenterVertically)
        .onFocusChanged { focused(it.isFocused) }.testTag("amount_$id").semantics { contentDescription = accessibleLabel },
        singleLine = true, readOnly=readOnly, textStyle = TextStyle(color = Ink, fontSize = if (id == "count" || id == "weight") 21.sp else 17.sp,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, textDirection = TextDirection.Ltr),
        cursorBrush = SolidColor(Tomato),
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
}
