package com.norman.duckdough

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteSizingTest {
    @Test fun everyReferenceCombinationPreservesTheWebsitesIngredientAmounts() {
        val stream = checkNotNull(javaClass.getResourceAsStream("/doughguy-reference.csv"))
        var cases = 0
        stream.bufferedReader().useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.forEach { line ->
                val fields = line.split(',')
                val inches = fields[0].toDouble()
                val count = fields[1].toInt()
                val thickness = Thickness.valueOf(fields[2])
                val recipe = if (fields[3].toBoolean()) AppRecipes.glutenFree else AppRecipes.primary
                val batch = WebsiteSizing.forDiameter(recipe, count, inches * 2.54, thickness)
                listOf("flour", "water", "yeast", "salt", "sugar", "oil").forEachIndexed { index, id ->
                    assertEquals("$line: $id", fields[4 + index].toDouble(), batch.grams(id), 1e-9)
                }
                val ingredientSum = recipe.ingredients.sumOf { batch.grams(it.id) }
                assertEquals(ingredientSum, batch.totalGrams, 1e-8)
                assertEquals(ingredientSum, batch.ballGrams * count, 1e-8)
                cases++
            }
        }
        assertEquals(660, cases)
    }

    @Test fun normalAndGlutenFreeWeightsComeFromTheirActualIngredients() {
        val normal = WebsiteSizing.forDiameter(AppRecipes.primary, 6, 40.64, Thickness.REGULAR)
        val gf = WebsiteSizing.forDiameter(AppRecipes.glutenFree, 6, 40.64, Thickness.REGULAR)
        assertEquals(2901.8, normal.totalGrams, 1e-8)
        assertEquals(3760.0, gf.totalGrams, 1e-8)
        assertTrue(normal.ballGrams > 480.0)
        assertTrue(gf.ballGrams > 562.0)
    }

    @Test fun directWeightModePreservesBothRecipeRatiosAndTargetWeight() {
        for (recipe in AppRecipes.all) {
            val batch = DoughMath.forPizzas(recipe, 4, 250.0)
            assertEquals(1000.0, batch.totalGrams, 1e-8)
            assertEquals(250.0, batch.ballGrams, 1e-8)
            recipe.ingredients.forEach { assertEquals(it.ratio, batch.grams(it.id) / batch.flourGrams, 1e-10) }
        }
    }
}
