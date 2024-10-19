package com.gmfs.xchain.wallet.crypto.bonehexact

import java.math.BigDecimal
import java.math.BigInteger

const val ALGO_NAME = "bonehexact"

class BonehExactAlgo(val idFormat: String, val formats: HashMap<String, HashMap<String, Any>>) : IdentityAlgo {

	private val keySize = formats[idFormat]?.get("key_size") as Int
	private var attestationFun: (BonehPublicKey, ByteArray) -> BohenAttestation
	private var aggregateReference: (ByteArray) -> HashMap<Int, Int>

	init {
		this.honestCheck = true

		if (!formats.containsKey(idFormat)) {
			throw RuntimeException("Identity Format $idFormat not found!")
		}
		val format = formats[idFormat]!!

		if (format.get("algo") !== ALGO_NAME) {
			throw RuntimeException("Identity format linked to wrong algorithm!")
		}

		if (this.keySize < 32 || this.keySize > 512) {
			throw RuntimeException("Illegale key size specified!")
		}

		when (val hashMode = format.get("hash")) {
			"sha256" -> {
				this.attestationFun = ::attestSHA256
				this.aggregateReference = ::binaryRelativitySHA256
			}
			"sha256_4" -> {
				this.attestationFun = ::attestSHA256_4
				this.aggregateReference = ::binaryRelativitySHA256_4
			}
			"sha512" -> {
				this.attestationFun = ::attestSHA512
				this.aggregateReference = ::binaryRelativitySHA512
			}
			else -> throw RuntimeException("Unknown hashing mode $hashNode")
		}
	}
}