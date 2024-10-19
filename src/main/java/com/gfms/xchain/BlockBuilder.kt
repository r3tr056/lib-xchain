package com.gfms.xchain

import com.gfms.xnet.crypto.XPrivateKey
import com.gfms.xnet.utils.EncodingUtils
import com.gfms.xnet.xpeer.XPeer

abstract class BlockBuilder(
    protected val selfPeer: XPeer,
    private val database: XChainStore
) {
    protected abstract fun udpate(builder: XBlock.Builder)

    fun sign(): XBlock {
        val builder = XBlock.Builder()
        update(builder)

        val prevBlock = database.getLatest(selfPeer.publicKey.keyToBin())
        if (prevBlock != null) {
            builder.index = prevBlock.sequenceNumber + 1u
            builder.prevHash = prevBlock.calculateHash()
        } else {
            // Genesis Block
            builder.index = GENESIS_SEQ
            builder.prevHash = GENESIS_HASH
        }

        builder.publicKey = selfPeer.publicKey.keyToBin()
        builder.signature = EMPTY_SIG

        val block = builder.buildBlock()
        block.sign(selfPeer.key as XPrivateKey)

        return block
    }
}

class ProposalBlockBuilder(
    selfPeer: XPeer,
    database: XChainStore,
    private val blockType: String,
    private val transaction: XTransaction,
    private val publicKey: ByteArray
): BlockBuilder(selfPeer, database) {
    override fun udpate(builder: XBlock.Builder) {
        builder.type = blockType
        builder.rawTx = EncodingUtils.encode(transaction)
        builder.linkPublicKey = publicKey
        builder.linkIndex = UNKNOWN_SEQ
    }
}

class AgreementBlockBuilder(
    selfPeer: XPeer,
    database: XChainStore,
    private val link: XBlock,
    private val transaction: XTransaction
): BlockBuilder(selfPeer, database) {
    override fun udpate(builder: XBlock.Builder) {
        builder.type = link.type
        builder.rawTx = EncodingUtils.encode(transaction)
        builder.linkPublicKey = link.publicKey
        builder.linkIndex = link.index
    }
}