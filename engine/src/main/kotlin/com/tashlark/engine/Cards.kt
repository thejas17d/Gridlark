package com.tashlark.engine

/** The four French suits. [ordinal] is stable and persisted. */
enum class Suit(val symbol: String, val label: String) {
    SPADES("♠", "spades"),
    HEARTS("♥", "hearts"),
    DIAMONDS("♦", "diamonds"),
    CLUBS("♣", "clubs");

    val isRed: Boolean get() = this == HEARTS || this == DIAMONDS
}

/**
 * A playing card packed in one Int: suit * 13 + (rank - 2), rank 2..14 (11 J, 12 Q, 13 K, 14 A).
 * Value class: no allocation in hot AI loops, stable for persistence.
 */
@JvmInline
value class Card(val code: Int) {
    init {
        require(code in 0..51) { "card code out of range: $code" }
    }

    val suit: Suit get() = Suit.entries[code / 13]
    val rank: Int get() = code % 13 + 2

    val rankLabel: String
        get() = when (rank) {
            11 -> "J"; 12 -> "Q"; 13 -> "K"; 14 -> "A"
            else -> rank.toString()
        }

    val rankName: String
        get() = when (rank) {
            11 -> "jack"; 12 -> "queen"; 13 -> "king"; 14 -> "ace"
            else -> rank.toString()
        }

    /** e.g. "Q♥" */
    override fun toString(): String = rankLabel + suit.symbol

    /** e.g. "queen of hearts" (for TalkBack). */
    val spoken: String get() = "$rankName of ${suit.label}"

    companion object {
        fun of(suit: Suit, rank: Int): Card {
            require(rank in 2..14) { "rank must be 2..14" }
            return Card(suit.ordinal * 13 + rank - 2)
        }

        /** Parses "Q♥", "10S", "as" etc. Used by tests. */
        fun parse(text: String): Card {
            val t = text.trim().uppercase()
            val suitChar = t.last()
            val suit = when (suitChar) {
                '♠', 'S' -> Suit.SPADES
                '♥', 'H' -> Suit.HEARTS
                '♦', 'D' -> Suit.DIAMONDS
                '♣', 'C' -> Suit.CLUBS
                else -> throw IllegalArgumentException("bad suit in $text")
            }
            val r = when (val rs = t.dropLast(1)) {
                "J" -> 11; "Q" -> 12; "K" -> 13; "A" -> 14
                else -> rs.toInt()
            }
            return of(suit, r)
        }
    }
}

/** A set of cards as a 52-bit mask. Cheap to copy, compare and iterate. */
@JvmInline
value class CardSet(val bits: Long) : Iterable<Card> {
    val size: Int get() = java.lang.Long.bitCount(bits)
    fun isEmpty(): Boolean = bits == 0L
    operator fun contains(c: Card): Boolean = bits and (1L shl c.code) != 0L
    operator fun plus(c: Card): CardSet = CardSet(bits or (1L shl c.code))
    operator fun minus(c: Card): CardSet = CardSet(bits and (1L shl c.code).inv())
    operator fun plus(o: CardSet): CardSet = CardSet(bits or o.bits)
    operator fun minus(o: CardSet): CardSet = CardSet(bits and o.bits.inv())
    infix fun intersect(o: CardSet): CardSet = CardSet(bits and o.bits)

    fun ofSuit(s: Suit): CardSet = CardSet(bits and suitMask(s))
    fun has(s: Suit): Boolean = bits and suitMask(s) != 0L

    override fun iterator(): Iterator<Card> = object : Iterator<Card> {
        var rest = bits
        override fun hasNext() = rest != 0L
        override fun next(): Card {
            val i = java.lang.Long.numberOfTrailingZeros(rest)
            rest = rest and (rest - 1)
            return Card(i)
        }
    }

    fun toList(): List<Card> = iterator().asSequence().toList()

    override fun toString(): String = toList().joinToString(" ")

    companion object {
        val EMPTY = CardSet(0L)
        fun suitMask(s: Suit): Long = ((1L shl 13) - 1) shl (s.ordinal * 13)
        fun of(cards: Iterable<Card>): CardSet = CardSet(cards.fold(0L) { acc, c -> acc or (1L shl c.code) })

        /** All 52 cards. */
        val FULL = CardSet((1L shl 52) - 1)

        /** 32-card deck used by 29: 7, 8, 9, 10, J, Q, K, A of each suit. */
        val PIQUET: CardSet = of(Suit.entries.flatMap { s -> (7..14).map { Card.of(s, it) } })
    }
}

/** Deterministic SplitMix64, identical on every device (used for deals and AI sampling). */
class Rng(seed: Long) {
    private var state = seed

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        val b = bound.toLong()
        val limit = Long.MAX_VALUE - (Long.MAX_VALUE % b)
        while (true) {
            val r = nextLong() ushr 1
            if (r < limit) return (r % b).toInt()
        }
    }

    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))

    fun <T> shuffle(list: MutableList<T>) {
        for (i in list.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val t = list[i]; list[i] = list[j]; list[j] = t
        }
    }

    fun fork(): Rng = Rng(nextLong())
}

/** Mixes values into one well-distributed seed (match seed + hand number, etc.). */
fun mixSeed(vararg parts: Long): Long {
    var h = 0x2545F4914F6CDD1DL
    for (p in parts) {
        h = h xor p
        h = Rng(h).nextLong()
    }
    return h
}
