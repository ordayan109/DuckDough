package com.norman.duckdough

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow
import kotlin.math.roundToLong

object AppRecipes {
    private fun ingredients(glutenFree: Boolean) = listOf(
        Ingredient("flour", if (glutenFree) "קמח ללא גלוטן" else "קמח לחם", 100.0,
            if (glutenFree) "תערובת ייעודית לפיצה ללא גלוטן" else "קמח חזק, 12–14% חלבון"),
        Ingredient("water", "מים", if (glutenFree) 80.0 else 62.0, "קרים, מתחת לכ־16°C"),
        Ingredient("yeast", "שמרים יבשים", 0.4, "שמרים יבשים פעילים · 0.4%"),
        Ingredient("salt", "מלח", 2.5, "2.5% ממשקל הקמח"),
        Ingredient("sugar", "סוכר", 2.0, "2% ממשקל הקמח"),
        Ingredient("oil", "שמן זית", 3.3, "3.3% ממשקל הקמח")
    )
    val primary = DoughRecipe("regular", "רגיל", ingredients(false))
    val glutenFree = DoughRecipe("gluten_free", "ללא גלוטן", ingredients(true))
    val all = listOf(primary, glutenFree)
}

enum class Thickness(val label: String, val factor: Double) {
    THIN("דק", 1.8), REGULAR("רגיל", 2.11), THICK("עבה", 2.75)
}

/**
 * Independently implemented numerical model of the public Dough Guy calculator,
 * inspected 2026-11-22. Preserve its ingredient amounts and display rounding.
 * Derive actual total/ball weights from those ingredients instead of its
 * inconsistent, separate dough-ball conversion constant.
 */
object WebsiteSizing {
    fun forDiameter(recipe: DoughRecipe, pizzas: Int, diameterCm: Double, thickness: Thickness): DoughBatch {
        require(diameterCm.isFinite() && diameterCm in 20.0..60.0)
        require(pizzas in 1..100)
        val baseFlour = if (recipe.id == AppRecipes.glutenFree.id) 333.0 * 6.0 else 1509.797685 * (480.0 / 425.0)
        val inches = diameterCm / 2.54
        val flour = baseFlour * (inches / 16.0).pow(2) * (pizzas / 6.0) * (thickness.factor / 2.11)
        val weights = recipe.ingredients.associate { ingredient ->
            val value = flour * ingredient.ratio
            ingredient.id to if (ingredient.id == "yeast")
                BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toDouble()
            else value.roundToLong().toDouble()
        }
        return DoughBatch(recipe, flour, pizzas, weights)
    }
}
