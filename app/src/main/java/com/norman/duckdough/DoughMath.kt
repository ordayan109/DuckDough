package com.norman.duckdough

data class Ingredient(val id: String, val name: String, val percent: Double, val note: String = "") {
    init { require(percent.isFinite() && percent > 0) }
    val ratio: Double get() = percent / 100.0
}

data class DoughRecipe(val id: String, val name: String, val ingredients: List<Ingredient>) {
    init {
        require(ingredients.isNotEmpty())
        require(ingredients.map { it.id }.distinct().size == ingredients.size)
        require(ingredients.first().id == "flour" && ingredients.first().percent == 100.0)
    }
    val massFactor: Double get() = ingredients.sumOf { it.ratio }
}

data class DoughBatch(val recipe: DoughRecipe, val flourGrams: Double, val pizzas: Int,
                      val weighedAmounts: Map<String, Double>? = null) {
    init {
        require(flourGrams.isFinite() && flourGrams > 0 && flourGrams <= 1e9)
        require(pizzas in 1..100)
        if (weighedAmounts != null) {
            require(weighedAmounts.keys == recipe.ingredients.map { it.id }.toSet())
            require(weighedAmounts.values.all { it.isFinite() && it >= 0 })
        }
    }
    val totalGrams: Double get() = weighedAmounts?.values?.sum() ?: (flourGrams * recipe.massFactor)
    val ballGrams: Double get() = totalGrams / pizzas
    fun grams(id: String): Double = weighedAmounts?.get(id) ?: (flourGrams * recipe.ingredients.first { it.id == id }.ratio)
}

object DoughMath {
    fun forPizzas(recipe: DoughRecipe, pizzas: Int, ballGrams: Double): DoughBatch {
        require(ballGrams.isFinite() && ballGrams > 0)
        return DoughBatch(recipe, pizzas * ballGrams / recipe.massFactor, pizzas)
    }

    fun fromIngredient(recipe: DoughRecipe, pizzas: Int, id: String, grams: Double): DoughBatch {
        require(grams.isFinite() && grams > 0)
        val ingredient = recipe.ingredients.first { it.id == id }
        return DoughBatch(recipe, grams / ingredient.ratio, pizzas)
    }
}
