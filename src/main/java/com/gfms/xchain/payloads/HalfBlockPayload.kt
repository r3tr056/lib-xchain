package com.gfms.xchain.payloads

import com.gfms.xchain.XBlock
import com.gfms.xnet.serialization.*
import java.util.*

@OptIn(ExperimentalUnsignedTypes::class)
open class HalfBlockPayload(
    val publicKey: ByteArray,
    val index: UInt,
    val linkPublicKey: ByteArray,
    val linkIndex: UInt,
    val prevHash: ByteArray,
    val signature: ByteArray,
    val blockType: String,
    val transaction: ByteArray,
    val timestamp: ULong
): Serializable {

    override fun serialize(): ByteArray {
        val serializedTimestamp = serializeULong(timestamp)
        return publicKey +
                serializeUInt(index) +
                linkPublicKey +
                serializeUInt(linkIndex) +
                prevHash +
                signature +
                serializeVarLen(blockType.toByteArray(Charsets.UTF_8)) +
                serializeVarLen(transaction) +
                serializedTimestamp
    }

    fun toXBlock(): XBlock {
        return XBlock(
            blockType,
            transaction,
            publicKey,
            index,
            linkPublicKey,
            linkIndex,
            prevHash,
            signature,
            Date(timestamp.toLong())
        )
    }

    companion object Deserializer : Deserializable<HalfBlockPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<HalfBlockPayload, Int> {
            var localOffset = 0
            val publicKey = buffer.copyOfRange(offset + localOffset, offset + localOffset + SERIALIZED_PUBLIC_KEY_SIZE)
            localOffset += SERIALIZED_PUBLIC_KEY_SIZE
            val index = deserializeUInt(buffer, offset + localOffset)
            localOffset += SERIALIZED_UINT_SIZE
            val linkPublicKey = buffer.copyOfRange(offset + localOffset, offset + localOffset + SERIALIZED_PUBLIC_KEY_SIZE)
            localOffset += SERIALIZED_PUBLIC_KEY_SIZE
            val linkIndex = deserializeUInt(buffer, offset + localOffset)
            localOffset += SERIALIZED_UINT_SIZE
            val prevHash = buffer.copyOfRange(offset + localOffset, offset + localOffset + HASH_SIZE)
            localOffset += HASH_SIZE
            val signature = buffer.copyOfRange(offset + localOffset, offset + localOffset + SIGNATURE_SIZE)
            localOffset += SIGNATURE_SIZE
            val (blockType, blockTypeSize) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += blockTypeSize
            val (transaction, transactionSize) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += transactionSize
            val timestamp = deserializeULong(buffer, offset + localOffset)
            localOffset += SERIALIZED_ULONG_SIZE

            val payload = HalfBlockPayload(
                publicKey,
                index,
                linkPublicKey,
                linkIndex,
                prevHash,
                signature,
                blockType.toString(Charsets.UTF_8),
                transaction,
                timestamp
            )
            return Pair(payload, localOffset)
        }

        fun fromHalfBlock(block: XBlock, sign: Boolean = true): HalfBlockPayload {
            return HalfBlockPayload(
                block.publicKey,
                block.index,
                block.linkPublicKey,
                block.linkIndex,
                block.prevHash,
                if (sign) block.signature else com.gfms.xchain.EMPTY_SIG,
                block.type,
                block.rawTx,
                block.timestamp.time.toULong()
            )
        }
    }
}

open class HalfBlockPairPayload(
    val halfBlock1: HalfBlockPayload,
    val halfBlock2: HalfBlockPayload
): Serializable {
    override fun serialize(): ByteArray {
        return halfBlock1.serialize() + halfBlock2.serialize()
    }

    companion object Deserializer : Deserializable<HalfBlockPairPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<HalfBlockPairPayload, Int> {
            val (block1, block1Size) = HalfBlockPayload.deserialize(buffer, offset)
            val (block2, block2Size) = HalfBlockPayload.deserialize(buffer, offset + block1Size)
            val payload = HalfBlockPairPayload(block1, block2)
            return Pair(payload, block1Size + block2Size)
        }

        fun fromHalfBlocks(block1: XBlock, block2: XBlock): HalfBlockPairPayload {
            val payload1 = HalfBlockPayload.fromHalfBlock(block1)
            val payload2 = HalfBlockPayload.fromHalfBlock(block2)
            return HalfBlockPairPayload(payload1, payload2)
        }
    }
}

open class HalfBlockBroadcastPayload(
    val block: HalfBlockPayload,
    val ttl: UInt
): Serializable {
    override fun serialize(): ByteArray {
        return block.serialize() + serializeUInt(ttl)
    }


    companion object Deserializer : Deserializable<HalfBlockBroadcastPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<HalfBlockBroadcastPayload, Int> {
            var (block, localOffset) = HalfBlockPayload.deserialize(buffer, offset)
            val ttl = deserializeUInt(buffer, offset + localOffset)
            localOffset += SERIALIZED_UINT_SIZE
            val payload = HalfBlockBroadcastPayload(
                block=block, ttl=ttl
            )
            return Pair(payload, localOffset)
        }

        fun fromHalfBlock(block: XBlock, ttl: UInt): HalfBlockBroadcastPayload {
            return HalfBlockBroadcastPayload(
                block=HalfBlockPayload.fromHalfBlock(block),
                ttl=ttl
            )
        }
    }
}

open class HalfBlockPairBroadcastPayload(
    val block1: HalfBlockPayload,
    val block2: HalfBlockPayload,
    val ttl: UInt
): Serializable {
    override fun serialize(): ByteArray {
        return block1.serialize() + block1.serialize() + serializeUInt(ttl)
    }

    companion object Deserializer : Deserializable<HalfBlockPairBroadcastPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<HalfBlockPairBroadcastPayload, Int> {
            val (block1, block1Size) = HalfBlockPayload.deserialize(buffer, offset)
            val (block2, block2Size) = HalfBlockPayload.deserialize(buffer, offset + block1Size)
            val ttl = deserializeUInt(buffer, offset + block1Size + block2Size)
            val payload = HalfBlockPairBroadcastPayload(block1, block2, ttl)
            return Pair(payload, block1Size + block2Size + SERIALIZED_UINT_SIZE)
        }

        fun fromHalfBlocks(block1: XBlock, block2: XBlock, ttl: UInt): HalfBlockPairBroadcastPayload {
            val payload1 = HalfBlockPayload.fromHalfBlock(block1)
            val payload2 = HalfBlockPayload.fromHalfBlock(block2)
            return HalfBlockPairBroadcastPayload(payload1, payload2, ttl)
        }
    }
}