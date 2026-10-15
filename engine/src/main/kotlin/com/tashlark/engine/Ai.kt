package com.tashlark.engine

enum class Skill(val samples: Int, val noise: Double) {
    /** Plays the heuristic policy with some randomness: beatable, never silly. */
    EASY(0, 0.25),
    /** Monte-Carlo with a modest sample count. */
    MEDIUM(24, 0.0),
    /** Monte-Carlo with more samples. */
    HARD(72, 0.0),
}

/**
 * Game-specific fast policy used for (a) Easy opponents and (b) playouts inside the
 * Monte-Carlo search. Must be quick and must only return legal actions.
 */
fun interface Policy<S : HandState> {
    fun choose(s: S, legal: List<Action>, rng: Rng): Action
}

/**
 * Perfect-information Monte Carlo ("determinization"):
 *  1. sample worlds consistent with what [seat] can see,
 *  2. for each legal action, play the hand out with the fast [policy] in every world,
 *  3. pick the action with the best average outcome for [seat].
 *
 * The AI never reads hidden information from the real state: every simulation runs on a
 * resampled copy, so it cannot "cheat".
 */
class MonteCarloAi<S : HandState>(
    private val rules: Rules<S>,
    private val policy: Policy<S>,
) {
    fun decide(state: S, seat: Int, skill: Skill, rng: Rng, maxMillis: Long = 900): Action {
        val legal = rules.legalActions(state)
        require(legal.isNotEmpty()) { "no legal action for seat $seat" }
        if (legal.size == 1) return legal[0]
        if (skill.samples == 0 || (skill.noise > 0 && rng.nextDouble() < skill.noise)) {
            return policy.choose(state, legal, rng)
        }
        val totals = DoubleArray(legal.size)
        var runs = 0
        val deadline = System.nanoTime() + maxMillis * 1_000_000
        for (k in 0 until skill.samples) {
            val world = rules.determinize(state, seat, rng)
            for (i in legal.indices) {
                totals[i] += playout(rules.apply(world, legal[i]), seat, rng)
            }
            runs++
            if (runs >= 2 && System.nanoTime() > deadline) break
        }
        var best = 0
        for (i in legal.indices) if (totals[i] > totals[best]) best = i
        return legal[best]
    }

    private fun playout(start: S, seat: Int, rng: Rng): Double {
        var s = start
        var guard = 0
        while (!rules.isOver(s)) {
            val legal = rules.legalActions(s)
            s = rules.apply(s, policy.choose(s, legal, rng))
            check(++guard < 400) { "playout did not terminate" }
        }
        return rules.utility(s)[seat]
    }
}
