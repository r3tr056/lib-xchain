package com.gfms.xchain.wallet.payload

import com.gfms.xnet.serialization.Deserializable
import com.gfms.xnet.serialization.Serializable
import com.gfms.xnet.serialization.deserializeVarLen
import com.gfms.xnet.serialization.serializeVarLen

class RequestAttestationPayload(
    private val metadata: String
): Serializable {
    private val msgId = 5

    override fun serialize(): ByteArray {
        return serializeVarLen(metadata.toByteArray(Charsets.UTF_8))
    }

    companion object Deserializer: Deserializable<RequestAttestationPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<RequestAttestationPayload, Int> {
            val (metadata, metadataSize) = deserializeVarLen(buffer, offset)
            return Pair(RequestAttestationPayload(metadata.toString(Charsets.UTF_8)), metadataSize)
        }
    }
}