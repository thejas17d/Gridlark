package com.gridlark.engine

/**
 * Deterministic SplitMix64 generator.
 *
 * We deliberately do not use kotlin.random / java.util.Random so that the same seed
 * produces the same puzzle on every device, JVM and app version. Daily puzzles depend on it.
 */
class Rng(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Uniform int in [0, bound). Uses rejection sampling to avoid modulo bias. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        val b = bound.toLong()
        val limit = Long.MAX_VALUE - (Long.MAX_VALUE % b)
        while (true) {
            val r = nextLong() ushr 1
            if (r < limit) return (r % b).toInt()
        }
    }

    fun nextInt(from: Int, untilExclusive: Int): Int = from + nextInt(untilExclusive - from)

    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))

    fun chance(p: Double): Boolean = nextDouble() < p

    fun shuffle(a: IntArray) {
        for (i in a.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val t = a[i]; a[i] = a[j]; a[j] = t
        }
    }

    fun <T> shuffle(list: MutableList<T>) {
        for (i in list.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val t = list[i]; list[i] = list[j]; list[j] = t
        }
    }

    fun permutation(n: Int): IntArray = IntArray(n) { it }.also { shuffle(it) }

    companion object {
        /** Mixes several values into a single well-distributed seed. */
        fun mix(vararg parts: Long): Long {
            var h = 0x2545F4914F6CDD1DL
            for (p in parts) {
                h = h xor p
                h = Rng(h).nextLong()
            }
            return h
        }
    }
}
