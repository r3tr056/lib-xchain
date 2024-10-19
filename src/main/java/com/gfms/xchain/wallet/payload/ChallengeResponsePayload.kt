package com.gfms.xchain.wallet.payload

import com.gfms.xnet.serialization.*

class ChallengeResponsePayload(
    private val challengeHash: ByteArray,
    private val response: ByteArray,
): Serializable {
    val messageId = 4

    override fun serialize(): ByteArray {
        return challengeHash + serializeVarLen(response)
    }

    companion object Deserializer: Deserializable<ChallengeResponsePayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<ChallengeResponsePayload, Int> {
            var localOffset = 0
            val challengeHash = buffer.copyOfRange(offset + localOffset, offset + localOffset + SERIALIZED_SHA1_HASH_SIZE)
            localOffset += SERIALIZED_SHA1_HASH_SIZE
            val (data, dataSize) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += dataSize
            val payload = ChallengeResponsePayload(challengeHash, data)
            return Pair(payload, localOffset)
        }
    }
}