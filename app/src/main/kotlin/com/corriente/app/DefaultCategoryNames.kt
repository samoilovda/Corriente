package com.corriente.app

/**
 * A2.13: названия типовых категорий «из коробки» — по локали устройства в момент создания БД (имя категории после
 * этого — данные пользователя, их можно переименовать). Ключ — `SeedCategory.id`. Миграция v5→v6 (уже
 * установленные копии) по-прежнему сеет русский набор из `:core:data`: на них она давно отработала.
 */
val defaultCategoryNameRes: Map<String, Int> = mapOf(
    "seed-exp-groceries" to R.string.seed_cat_exp_groceries,
    "seed-exp-dining" to R.string.seed_cat_exp_dining,
    "seed-exp-transport" to R.string.seed_cat_exp_transport,
    "seed-exp-car" to R.string.seed_cat_exp_car,
    "seed-exp-housing" to R.string.seed_cat_exp_housing,
    "seed-exp-utilities" to R.string.seed_cat_exp_utilities,
    "seed-exp-connectivity" to R.string.seed_cat_exp_connectivity,
    "seed-exp-health" to R.string.seed_cat_exp_health,
    "seed-exp-clothing" to R.string.seed_cat_exp_clothing,
    "seed-exp-entertainment" to R.string.seed_cat_exp_entertainment,
    "seed-exp-sport" to R.string.seed_cat_exp_sport,
    "seed-exp-education" to R.string.seed_cat_exp_education,
    "seed-exp-gifts" to R.string.seed_cat_exp_gifts,
    "seed-exp-travel" to R.string.seed_cat_exp_travel,
    "seed-exp-kids" to R.string.seed_cat_exp_kids,
    "seed-exp-pets" to R.string.seed_cat_exp_pets,
    "seed-exp-beauty" to R.string.seed_cat_exp_beauty,
    "seed-exp-subscriptions" to R.string.seed_cat_exp_subscriptions,
    "seed-exp-fees" to R.string.seed_cat_exp_fees,
    "seed-exp-other-exp" to R.string.seed_cat_exp_other_exp,
    "seed-inc-salary" to R.string.seed_cat_inc_salary,
    "seed-inc-bonus" to R.string.seed_cat_inc_bonus,
    "seed-inc-sidework" to R.string.seed_cat_inc_sidework,
    "seed-inc-interest" to R.string.seed_cat_inc_interest,
    "seed-inc-dividends" to R.string.seed_cat_inc_dividends,
    "seed-inc-gift" to R.string.seed_cat_inc_gift,
    "seed-inc-refund" to R.string.seed_cat_inc_refund,
    "seed-inc-sale" to R.string.seed_cat_inc_sale,
    "seed-inc-other-inc" to R.string.seed_cat_inc_other_inc,
)
