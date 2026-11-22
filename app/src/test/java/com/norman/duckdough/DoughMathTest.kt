package com.norman.duckdough

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DoughMathTest {
    private val recipe = DoughRecipe("fixture", "Test recipe", listOf(
        Ingredient("flour", "Flour", 100.0), Ingredient("water", "Water", 60.0),
        Ingredient("salt", "Salt", 3.0), Ingredient("yeast", "Yeast", 1.0)
    ))

    @Test fun recipeRatiosAndTotalAgree() {
        val batch = DoughBatch(recipe, 500.0, 4)
        assertEquals(300.0, batch.grams("water"), 1e-10)
        assertEquals(15.0, batch.grams("salt"), 1e-10)
        assertEquals(5.0, batch.grams("yeast"), 1e-10)
        assertEquals(820.0, batch.totalGrams, 1e-10)
        assertEquals(205.0, batch.ballGrams, 1e-10)
    }

    @Test fun ballTargetsConserveTheSumOfEveryIngredient() {
        for (count in listOf(1, 4, 10, 100)) for (ball in listOf(100.0, 250.0, 480.0)) {
            val batch = DoughMath.forPizzas(recipe, count, ball)
            val sum = recipe.ingredients.sumOf { batch.grams(it.id) }
            assertEquals(count * ball, sum, 1e-7)
            assertEquals(ball, batch.ballGrams, 1e-9)
        }
    }

    @Test fun anyIngredientCanScaleTheWholeRecipe() {
        for (ingredient in recipe.ingredients) {
            val batch = DoughMath.fromIngredient(recipe, 4, ingredient.id, 1000.0 * ingredient.ratio)
            assertEquals(1000.0, batch.flourGrams, 1e-8)
            assertEquals(1640.0, batch.totalGrams, 1e-8)
        }
    }

    @Test fun invalidCountsAndNonFiniteAmountsAreRejected() {
        for (count in listOf(0, -1, 101)) assertThrows(IllegalArgumentException::class.java) { DoughMath.forPizzas(recipe, count, 250.0) }
        for (value in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY))
            assertThrows(IllegalArgumentException::class.java) { DoughMath.forPizzas(recipe, 4, value) }
    }

    @Test fun duplicateIngredientIdentifiersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DoughRecipe("bad", "Bad", listOf(Ingredient("flour", "Flour", 100.0), Ingredient("flour", "Duplicate", 50.0)))
        }
    }
}
