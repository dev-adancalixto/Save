package com.save.finanzas.update

/** Compara versiones tipo "1.2.3" / "v1.2.3" / "1.2.3-dev.4". Positivo si a > b. */
fun compareVersions(a: String, b: String): Int {
    fun parse(v: String): Pair<List<Int>, String?> {
        val s = v.trim().removePrefix("v").removePrefix("V")
        val core = s.substringBefore('-')
        val pre = s.substringAfter('-', "").ifEmpty { null }
        val nums = core.split('.').map { it.toIntOrNull() ?: 0 }
        return (nums + List(maxOf(0, 3 - nums.size)) { 0 }) to pre
    }
    val (na, pa) = parse(a)
    val (nb, pb) = parse(b)
    for (i in 0 until maxOf(na.size, nb.size)) {
        val d = na.getOrElse(i) { 0 } - nb.getOrElse(i) { 0 }
        if (d != 0) return d
    }
    return when {
        pa == null && pb == null -> 0
        pa == null -> 1      // versión final > pre-release
        pb == null -> -1
        else -> pa.compareTo(pb)
    }
}
