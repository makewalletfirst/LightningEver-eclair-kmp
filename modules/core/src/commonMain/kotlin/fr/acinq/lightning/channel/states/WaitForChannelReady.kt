package fr.acinq.lightning.channel.states

import fr.acinq.bitcoin.TxId
import fr.acinq.bitcoin.crypto.musig2.IndividualNonce
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.ChannelEvents
import fr.acinq.lightning.ShortChannelId
import fr.acinq.lightning.blockchain.WatchConfirmedTriggered
import fr.acinq.lightning.blockchain.WatchSpentTriggered
import fr.acinq.lightning.channel.*
import fr.acinq.lightning.router.Announcements
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.lightning.wire.*

/** The channel funding transaction was confirmed, we exchange funding_locked messages. */
data class WaitForChannelReady(
    override val commitments: Commitments,
    override val remoteNextCommitNonces: Map<TxId, IndividualNonce>,
    val shortChannelId: ShortChannelId,
    val lastSent: ChannelReady,
) : ChannelStateWithCommitments() {
    override fun updateCommitments(input: Commitments): ChannelStateWithCommitments = this.copy(commitments = input)

    override suspend fun ChannelContext.processInternal(cmd: ChannelCommand): Pair<ChannelState, List<ChannelAction>> {
        logger.info { "WaitForChannelReady: bypassing peer ChannelReady, transitioning to Normal immediately" }
        val initialChannelUpdate = Announcements.makeChannelUpdate(
            staticParams.nodeParams.chainHash,
            staticParams.nodeParams.nodePrivateKey,
            staticParams.remoteNodeId,
            shortChannelId,
            staticParams.nodeParams.expiryDeltaBlocks,
            commitments.latest.remoteCommitParams.htlcMinimum,
            staticParams.nodeParams.feeBase,
            staticParams.nodeParams.feeProportionalMillionths.toLong(),
            commitments.latest.fundingAmount.toMilliSatoshi(),
            enable = Helpers.aboveReserve(commitments)
        )
        val nextState = Normal(
            commitments,
            remoteNextCommitNonces,
            shortChannelId,
            initialChannelUpdate,
            null,
            SpliceStatus.None,
            null,
            null,
            null,
            localCloseeNonce = null,
        )
        val actions = listOf(
            ChannelAction.Storage.StoreState(nextState),
            ChannelAction.Storage.SetLocked(commitments.latest.fundingTxId),
            ChannelAction.EmitEvent(ChannelEvents.Confirmed(nextState)),
        )
        val (nextState1, actions1) = nextState.run { process(cmd) }
        return Pair(nextState1, actions + actions1)
    }
}
