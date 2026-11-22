# Calculation model

Reference inspected on 2026-11-22:
[Dough Guy calculator](https://doughguy.co/pages/dough) and its public
[`dg-calculator.js`](https://doughguy.co/cdn/shop/t/18/assets/dg-calculator.js?v=150604521820962005191794940365).

This is an independent implementation of the numerical formulas. The reference
site's source, interface, branding, instructions and illustrations are not bundled.

## Recipes

All percentages are relative to flour weight.

| Ingredient | Regular | Gluten free |
|---|---:|---:|
| Flour | 100% | 100% |
| Water | 62% | 80% |
| Active dry yeast | 0.4% | 0.4% |
| Salt | 2.5% | 2.5% |
| Sugar | 2% | 2% |
| Olive oil | 3.3% | 3.3% |

## Diameter and thickness mode

Convert the diameter from centimetres to inches by dividing by 2.54.
The reference flour amounts for six 16-inch, regular-thickness pizzas are:

- Regular: `1509.797685 * (480 / 425)` grams.
- Gluten free: `333 * 6` grams.

Scale flour by `(diameterInches / 16)^2 * (pizzas / 6) * (thickness / 2.11)`.
Thickness factors are 1.8 (thin), 2.11 (regular) and 2.75 (thick).
Compute ingredients using the percentages above. Match the reference's display
rounding: whole grams except yeast, which is rounded to one decimal place.

Diameter-based weight is a practical estimate, not a guarantee of baked thickness.

## Corrected weight display

The reference calculates its displayed ball weight using a separate constant
`2550 / 1509.797685`, which disagrees with the sum of the recipe percentages.
DuckDough preserves all reference ingredient amounts in diameter mode, then
calculates **total = sum(ingredient amounts)** and **ball = total / pizzas**.

Example: six regular 16-inch pizzas require 1705g flour, 1057g water, 6.8g yeast,
43g salt, 34g sugar and 56g olive oil. Total: 2901.8g, or 483.633...g per ball.
The website displays 480g per ball. The gluten-free example totals 3760g,
or 626.666...g per ball, instead of the website's displayed 562g.

## Direct weight / ingredient scaling mode

For a requested ball weight and count, divide total requested dough weight by
the sum of ingredient ratios: 1.702 for regular, or 1.882 for gluten free.
Changing any ingredient scales the recipe while preserving its percentages.
This switches the interface to direct weight mode. Keep full precision in this
mode and round only the displayed quantities (normally to two decimals).

## Verification

`doughguy-reference.csv` contains 660 output cases captured from the reference
calculator itself: every integer diameter from 10 to 20 inches, counts 1 to 10,
all three thicknesses and both flour modes. Tests compare every ingredient
against those outputs and independently check total/ball mass conservation.
