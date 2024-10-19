package com.gfms.xchain.wallet.payload

import com.gfms.xnet.serialization.*

@OptIn(kotlin.ExperimentalUnsignedTypes::class)
class XChainChunkPayload(
    private val hash: ByteArray,
    private val index: Int,
    private val data: ByteArray,
    private val metadata: ByteArray? = null,
    private val signature: ByteArray? = null
): Serializable {
    private val msgId = 2

    override fun serialize(): ByteArray {
        return (hash + serializeUInt(index.toUInt()) + serializeVarLen(data) +
                (
                        if (metadata != null && signature != null)
                            serializeVarLen(metadata) + serializeVarLen(signature)
                        else
                            byteArrayOf()
                        )
                )
    }

    companion object Deserializer: Deserializable<XChainChunkPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<XChainChunkPayload, Int> {

            var localOffset = 0
            val hash = buffer.copyOfRange(offset + localOffset,
            offset + localOffset + SERIALIZED_SHA1_HASH_SIZE)
            localOffset += SERIALIZED_SHA1_HASH_SIZE
            val index = deserializeUInt(buffer, offset + localOffset)
            localOffset =+ SERIALIZED_UINT_SIZE
            val (data, dataSize) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += dataSize

            return if (buffer.lastIndex > offset + localOffset) {
                val (metadata, metadataSize) = deserializeVarLen(buffer, offset + localOffset)
                localOffset += metadataSize

                val (signature, signatureSize) = deserializeVarLen(buffer, offset + localOffset)
                localOffset += signatureSize

                val payload = XChainChunkPayload(hash, index.toInt(), data, metadata, signature)
                Pair(payload, localOffset)
            } else {
                val payload = XChainChunkPayload(hash, index.toInt(), data)
                Pair(payload, localOffset)
            }
        }
    }
}