package com.doanh.chroni.core.physics

/** Lecture des saisies utilisateur (virgule ou point décimal). */
object Inputs {
    fun parseDecimal(text: String): Double? {
        val t = text.trim().replace(" ", "").replace(' '.toString(), "").replace(',', '.')
        if (t.isEmpty()) return null
        if (t.count { it == '.' } > 1) return null
        return t.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** Construit les paramètres ; la température vide signifie « non renseignée ». */
    fun toParams(mass: String, distance: String, temperature: String): Pair<ShotParams?, List<InputError>> {
        val m = parseDecimal(mass)
        val d = parseDecimal(distance)
        val tBlank = temperature.isBlank()
        val t = if (tBlank) null else parseDecimal(temperature)
        // Chaque champ est contrôlé indépendamment pour signaler toutes les erreurs à la fois.
        val rangeErrors = Physics.validate(ShotParams(m ?: Double.NaN, d ?: Double.NaN, t))
        val errors = mutableListOf<InputError>()
        if (m == null || InputError.MASS in rangeErrors) errors += InputError.MASS
        if (d == null || InputError.DISTANCE in rangeErrors) errors += InputError.DISTANCE
        if ((!tBlank && t == null) || InputError.TEMPERATURE in rangeErrors) errors += InputError.TEMPERATURE
        if (errors.isNotEmpty()) return null to errors
        return ShotParams(m!!, d!!, t) to emptyList()
    }
}
