package com.gfms.xchain.wallet.payload

import com.gfms.xnet.serialization.Deserializable
import com.gfms.xnet.serialization.SERIALIZED_SHA1_HASH_SIZE
import com.gfms.xnet.serialization.Serializable

class VerifyAttestationRequestPayload(
    val hash: ByteArray
): Serializable {
    val messageId = 1

    override fun serialize(): ByteArray {
        return hash
    }

    companion object Deserializer: Deserializable<VerifyAttestationRequestPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<VerifyAttestationRequestPayload, Int> {
            var localOffset = 0
            val hash = buffer.copyOfRange(offset + localOffset, offset + localOffset + SERIALIZED_SHA1_HASH_SIZE)
            localOffset += SERIALIZED_SHA1_HASH_SIZE
            return Pair(VerifyAttestationRequestPayload(hash), localOffset)
        }
    }
}