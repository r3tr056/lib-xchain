package com.gfms.xchain.wallet.payload

import com.gfms.xnet.serialization.*

class ChallengeRequestPayload(
    val attestationHash: ByteArray,
    val challenge: ByteArray,
): Serializable {

    val messageId = 3

    override fun serialize(): ByteArray {
        return attestationHash + serializeVarLen(challenge)
    }

    companion object Deserializer: Deserializable<ChallengeRequestPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<ChallengeRequestPayload, Int> {
            var localOffset = 0
            val challengeHash = buffer.copyOfRange(offset + localOffset, offset + localOffset + SERIALIZED_SHA1_HASH_SIZE)
            localOffset += SERIALIZED_SHA1_HASH_SIZE

            val (data, dataSize) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += dataSize

            val payload = ChallengeRequestPayload(challengeHash, data)
            return Pair(payload, localOffset)
        }
    }
}