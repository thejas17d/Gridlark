package com.gridlark.engine

import com.gridlark.engine.crowns.CrownsGenerator
import com.gridlark.engine.crowns.CrownsSolver
import org.junit.Test

class CrownsStats {
    @Test
    fun tierHistogram() {
        if (System.getProperty("gridlark.stats") == null) return
        for (n in 6..10) {
            val rng = Rng(n.toLong())
            val hist = IntArray(7)
            val t0 = System.nanoTime()
            val tries = 100
            repeat(tries) {
                val sol = CrownsGenerator.randomSolution(n, rng)
                val reg = CrownsGenerator.growRegions(n, sol, rng)
                if (!CrownsGenerator.makeUnique(n, reg, sol, rng)) { hist[0]++; return@repeat }
                val o = CrownsSolver(n, reg).solve()
                hist[if (o.solved) o.maxTier else 6]++
            }
            val ms = (System.nanoTime() - t0) / 1_000_000
            println("n=$n repairFail=${hist[0]} tiers(1..5, unsolved)=${hist.drop(1).joinToString()} totalMs=$ms perTry=${ms / tries.toDouble()}")
        }
    }
}
