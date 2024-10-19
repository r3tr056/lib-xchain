package com.gfms.xchain.wallet.crypto.primitives

import java.math.BigInteger

fun weilPairing(
    mod: BigInteger,
    m: BigInteger,
    p: Pair<FP2Value, FP2Value>,
    q: Pair<FP2Value, FP2Value>,
    s: Pair<FP2Value, FP2Value>,
): FP2Value {
    val nS = Pair(s.first, FP2Value(mod, -BigInteger.ONE) * s.second)

    val eSum1 = eSum(mod, q, s)
    if (eSum1 !is Pair<*, *>) {
        throw ArithmeticException("eSum calculation returned non-expected value: $eSum1")
    }

    // This can't fail as we already checked whether a pair was returned
    @Suppress("UNCHECKED_CAST")
    val a = millerCalc(mod, m, p, eSum1 as Pair<FP2Value, FP2Value>)
    val b = millerCalc(mod, m, p, s)

    val eSum2 = eSum(mod, p, nS)
    if (eSum2 !is Pair<*,*>) {
        throw ArithmeticException("eSum calculation returned non-expected value: $eSum1")
    }

    // This cannot fail as we already checked if a pair was returned or not
    @Suppress("UNCHECKED_CAST")
    val c = millerCalc(mod, m, q, eSum2 as Pair<FP2Value, FP2Value>)
    val d = millerCalc(mod, m, q, nS)
    val wp = (a * d) / (b * c)

    return wp.wpNominator() * wp.wpDenomInverse()
}

fun millerCalc(mod: BigInteger, m: BigInteger, p: Pair<FP2Value, FP2Value>, r: Pair<FP2Value, FP2Value>): FP2Value {
    val mList = m.toString(2).toList().reversed().map { it.toString().toInt() }
    var t: Any = p

}