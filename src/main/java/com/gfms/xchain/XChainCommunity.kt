package com.gfms.xchain

import com.gfms.xchain.payloads.HalfBlockBroadcastPayload
import com.gfms.xchain.payloads.HalfBlockPairBroadcastPayload
import com.gfms.xchain.payloads.HalfBlockPairPayload
import com.gfms.xchain.payloads.HalfBlockPayload
import com.gfms.xnet.XCommunity
import com.gfms.xnet.xpeer.XPeer

open class XChainCommunity(
    private val settings: XChainSettings,
    val database: XChainStore,
    private val crawlar: XChainCrawler = XChainCrawler()
) : XCommunity() {

    override val serviceId: String
        get() = "5ad767b05ae592a02488272ca2a86b847d4562e1"
    private val relayedBroadcasts = mutableSetOf<String>()
    private val listenersMap: MutableMap<String?, MutableList<BlockListener>> = mutableMapOf()
    private val txValidators: MutableMap<String, TransactionValidator> = mutableMapOf()
    private val blockSigners: MutableMap<String, BlockSigner> = mutableMapOf()
    private val crawlRequestCache: MutableMap<UInt, CrawlRequest> = mutableMapOf()

    init {
        messageHandlers[MessageId.HALF_BLOCK] = ::onBlockHeaderPacket
        messageHandlers[MessageId.CRAWL_REQUEST] = ::onCrawlRequestPacket
        messageHandlers[MessageId.CRAWL_RESPONSE] = ::onCrawlResponsePacket
        messageHandlers[MessageId.HALF_BLOCK_BROADCAST] = ::onHalfBlockBroadcastPacket
        messageHandlers[MessageId.HALF_BLOCK_PAIR] = ::onHalfBlockPairPacket
        messageHandlers[MessageId.HALF_BLOCK_PAIR_BROADCAST] = ::onHalfBlockPairBroadcastPacket
        messageHandlers[MessageId.EMPTY_CRAWL_RESPONSE] = ::onEmptyCrawlResponsePacket
    }

    override fun load() {
        super.load()
        crawlar.xChainCommunity = this
    }

    // Block listener
    // Registers listeners that will be notified of new blocks
    fun addListener(blockType: String?, listener: BlockListener) {
        val listeners = listenersMap[blockType] ?: mutableListOf()
        listeners.add(listener)
        listenersMap[blockType] = listeners
    }

    // Removes a previously registered block listener
    fun removeListener(listener: BlockListener, blockType: String? = null) {
        listenersMap[blockType]?.remove(listener)
    }

    /**
     * Register a validator for specific block type. The validator is called for every
     * incoming block. It should check the integrity of the transaction and return the
     * validation result. Invalid blocks will be dropped immediately, valid blocks will
     * be stored in the database
     */
    fun registerTransactionValidator(blockType: String, validator: TransactionValidator) {
        txValidators[blockType] = validator
    }

    private fun getTransactionValidator(blockType: String): TransactionValidator? {
        return txValidators[blockType]
    }

    fun registerBlockSigner(blockType: String, signer: BlockSigner) {
        blockSigners[blockType] = signer
    }

    // Notifies the listeners on the reception of a specific new block
    internal fun notfiyListeners(block: XBlock) {
        val universalListeners = listenersMap[null] ?: listOf<BlockListener>()
        for (listener in universalListeners) {
            listener.onBlockReceived(block)
        }
        // Type based filtering
        val listeners = listenersMap[block.type] ?: listOf<BlockListener>()
        for (listener in listeners) {
            listener.onBlockReceived(block)
        }
    }

    // Sends a signature request to sign a block signer
    private fun onSignatureRequest(block: XBlock) {
        blockSigners[block.type]?.onSignatureRequest(block)
    }

    // Send a block to a specific peer, or do a broadcast to known peers if no peer is specified
    fun sendBlock(block: XBlock, peer: XPeer? = null, ttl: Int = 1) {
        if (peer != null) {
            // logger call -> peer info
            val payload = HalfBlockPayload.fromHalfBlock(block)
            /// logger call -> payload
            // Serialize a packet based on the payload
            val packet = serializePacket(MessageId.HALF_BLOCK, payload, false)
            send(peer, packet)
        } else {
            // Broadcast to all known peers
            val payload = HalfBlockBroadcastPayload.fromHalfBlock(block, ttl.toUInt())
            // logger call -> payload
            val packet = serializePacket(MessageId.HALF_BLOCK, payload, false)
            // Specify the random seed `broadcastFanout`
            val randomPeers = network.getRandomPeers(settings.broadcastFanout)
            for (randomPeer in randomPeers) {
                send(randomPeer, packet)
            }
            relayedBroadcasts.add(block.blockId)
        }
    }

    // Send a half block pair to a specific peer, or do a broadcast to known peers
    fun sendBlockPair(
        block1: XBlock,
        block2: XBlock,
        peer: XPeer? = null,
        ttl: UInt = 1u
    ) {
        if (peer != null) {
            val payload = HalfBlockPairPayload.fromHalfBlocks(block1, block2)
            // logger call -> payload
            val packet = serializePacket(MessageId.HALF_BLOCK_PAIR, payload, false)
            send(peer, packet)
        } else {
            val payload = HalfBlockPairBroadcastPayload.fromHalfBlocks(block1, block2, ttl)
            // logger -> payload
            val packet = serializePacket(MessageId.HALF_BLOCK_PAIR_BROADCAST, payload, false)
            for (randomPeer in network.getRandomPeers(settings.broadcastFanout)) {
                send(randomPeer, packet)
            }
        }
    }

}