package com.gfms.xchain

import com.gfms.xnet.crypto.XPrivateKey
import com.gfms.xnet.crypto.defaultCryptoProvider
import com.gfms.xchain.payloads.HalfBlockPayload
import com.gfms.xnet.utils.EncodingUtils
import com.gfms.xnet.utils.sha256
import com.gfms.xnet.utils.toHex
import java.lang.IllegalStateException
import java.math.BigInteger
import java.util.*


val GENESIS_HASH = ByteArray(32) { '0'.toByte() }
val GENESIS_SEQ = 1u
val UNKNOWN_SEQ = 0u
val EMPTY_SIG = ByteArray(64) { '0'.toByte() }
val EMPTY_PK = ByteArray(74) { '0'.toByte() }
val ANY_COUNTERPARTY_PK = EMPTY_PK

typealias XTransaction = Map<*,*>

class XBlock(
    val type: String,
    val rawTx: ByteArray,
    val publicKey: ByteArray,
    val index: UInt,
    val linkPublicKey: ByteArray,
    val linkIndex: UInt,
    val prevHash: ByteArray,
    var signature: ByteArray,
    val timestamp: Date,
    val insertTime: Date? = null
) {

    val blockId = publicKey.toHex() + "." + index
    val linkedBlockId = linkPublicKey.toHex() + "." + linkIndex
    // If the block is genesis or not
    val isGenesis = index == GENESIS_SEQ && prevHash.contentEquals(GENESIS_HASH)
    // If the block is self signed or not
    val isSelfSigned = publicKey.contentEquals(linkPublicKey)
    // If the block is a proposal block
    val isProposal = linkIndex == UNKNOWN_SEQ
    // If the block is an agreement block
    val isAgreement = linkIndex != UNKNOWN_SEQ

    val transaction: XTransaction by lazy {
        try {
            val (_,data) = EncodingUtils.decode(rawTx)
            data as XTransaction
        } catch (e: XTransactionSerializationException) {
            e.printStackTrace()
            mapOf<String, Any>()
        } catch (e: Exception) {
            e.printStackTrace()
            mapOf<String, Any>()
        }
    }

    // Returns the hash of this block as a number (used as crawl ID)
    val hashNumber: Int
        get() {
            val int = calculateHash().toHex().toBigInteger(16)
            return int.mod(BigInteger.valueOf(100000000)).toInt()
        }

    // Validates this block against what is known in the database
    fun validate(database: XChainStore): ValidationResult {
        val prevBlk = database.getBlockBefore(this)
        val nextBlk = database.getBlockAfter(this)
        // initialize the validation result to reflect the achievable validation level
        var result = getMaxValidationLevel(prevBlk, nextBlk)
        // Check the block invariant
        result = validateBlockInvariant(result)
        // Check of the chain of block is properly booked up
        result = validateChainConsistency(prevBlk, nextBlk, result)
        return result
    }

    // Determine the max validation level
    // Depending on the blocks we get from the database, we can decide to reduce the validation
    // level. We must do this prior to flagging any errors. This way we are only ever reducing
    // the validation level without having to resort to min()/max() every time we set it
    private fun getMaxValidationLevel(
        prevBlk: XBlock?,
        nextBlk: XBlock?
    ): ValidationResult {
        val isPrevGap = prevBlk == null || prevBlk.index != index - 1u
        val isNextGap = nextBlk == null || nextBlk.index != index + 1u

        return if (prevBlk == null && nextBlk == null && !isGenesis) {
            ValidationResult.NoInfo
        } else if (isPrevGap && isNextGap && !isGenesis) {
            ValidationResult.Partial
        } else if (isNextGap) {
            ValidationResult.PartialNext
        } else if (isPrevGap && !isGenesis) {
            ValidationResult.PartialPrevious
        } else {
            ValidationResult.Valid
        }
    }

    // Validate that the block is the sane, no insane parameters, and count the errors
    private fun validateBlockInvariant(prevResult: ValidationResult): ValidationResult {
        val errors = mutableListOf<String>()
        if (index < GENESIS_SEQ) {
            errors += ValidationErrors.INVALID_SEQ_NUMBER
        }
        if (!defaultCryptoProvider.isValidPublicBin(publicKey))
            errors += ValidationErrors.INVALID_PUBLIC_KEY
        if (!linkPublicKey.contentEquals(EMPTY_PK) && !linkPublicKey.contentEquals(
                ANY_COUNTERPARTY_PK
            ) && !defaultCryptoProvider.isValidPublicBin(linkPublicKey))
                    errors += ValidationErrors.INVALID_LINK_PUBLIC_KEY

        try {
            val pk = defaultCryptoProvider.keyFromPublicBin(publicKey)
            val serialized = HalfBlockPayload.fromHalfBlock(this, false).serialize()
            if (!pk.verify(signature, serialized))
                errors += ValidationErrors.INVALID_SIGNATURE
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (index == GENESIS_SEQ && !prevHash.contentEquals(GENESIS_HASH)) {
            errors += ValidationErrors.INVALID_GENESIS_HASH
        }
        if (index != GENESIS_SEQ && prevHash.contentEquals(GENESIS_HASH)) {
            errors += ValidationErrors.INVALID_GENESIS_SEQ_NUMBER
        }
        return updateValidationResult(prevResult, errors)
    }

    // Update the validation result
    private fun updateValidationResult(prevResult: ValidationREsult, nwErrors: List<String>): ValidationResult {
        return if (nwErrors.isNotEmpty()) {
            val prevErrors = if (prevResult is ValidationResult.Invalid) {
                prevResult.errors
            } else listOf<String>()
            val errors = prevErrors + nwErrors
            ValidationResult.Invalid(errors)
        } else {
            prevResult
        }
    }

    // Validate the chain consistency
    // The prev block should point to this block and this block to the next block
    private fun validateChainConsistency(
        prevBlk: XBlock?,
        nextBlk: XBlock?,
        prevResult: ValidationResult
    ): ValidationResult {
        val errors = mutableListOf<String>()

        if (prevBlk != null) {
            if (!prevBlk.publicKey.contentEquals(publicKey)) {
                errors += ValidationErrors.PREV_PUBLIC_KEY_MISMATCH
            }
            if (prevBlk.index >= index) {
                errors += ValidationErrors.PREV_SEQ_NUMBER_MISMATCH
            }
            val isPrevGap = prevBlk.index != index - 1u
            if (!isPrevGap && !prevBlk.calculateHash().contentEquals(prevHash)) {
                errors += ValidationErrors.PREV_HASH_MISMATCH
            }
        }

        if (nextBlk != null) {
            if (!nextBlk.publicKey.contentEquals(publicKey))
                errors += ValidationErrors.NEXT_PUBLIC_KEY_MISMATCH
            if (nextBlk.index <= index)
                errors += ValidationErrors.NEXT_SEQ_NUMBER_MISMATCH
            val isNextGap = nextBlk.index != index - 1u
            if (!isNextGap && !nextBlk.prevHash.contentEquals(calculateHash()))
                errors += ValidationErrors.NEXT_HASH_MISMATCH
        }

        return updateValidationResult(prevResult, errors)
    }

    // Sign this block with the given private key
    fun sign(key: XPrivateKey) {
        val payload = HalfBlockPayload.fromHalfBlock(this, sign=false).serialize()
        signature = key.sign(payload)
    }

    fun calculateHash(): ByteArray {
        val payload = HalfBlockPayload.fromHalfBlock(this).serialize()
        return sha256(payload)
    }

    override fun equals(other: Any?): Boolean {
        return other is XBlock && other.calculateHash().contentEquals(calculateHash())
    }

    override fun hashCode(): Int {
        return calculateHash().contentHashCode()
    }

    class Builder(
        var type: String? = null,
        var rawTx: ByteArray? = null,
        var publicKey: ByteArray? = null,
        var index: UInt? = null,
        var linkPublicKey: ByteArray? = null,
        var linkIndex: UInt? = null,
        var prevHash: ByteArray? = null,
        var signature: ByteArray? = null
    ) {
        fun build(): XBlock {
            val type = type ?: throw IllegalStateException("type is null")
            val rawTx = rawTx ?: throw IllegalStateException("transaction is null")
            val publicKey = publicKey ?: throw IllegalStateException("publicKey is null")
            val index = index ?: throw IllegalStateException("index is null")
            val linkPublicKey = linkPublicKey ?: throw IllegalStateException("linkPublicKey is null")
            val linkIndex = linkIndex ?: throw IllegalStateException("linkIndex is null")
            val prevHash = prevHash ?: throw IllegalStateException("prevHash is null")
            val signature = signature ?: throw IllegalStateException("signature is null")

            return XBlock(type = type, rawTx = rawTx, publicKey = publicKey, index = index, linkPublicKey = linkPublicKey, linkIndex = linkIndex, prevHash = prevHash, signature = signature, timestamp = Date())
        }
    }
}