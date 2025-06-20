package io.cheddarswallet.bitcoincore

import io.cheddarswallet.bitcoincore.core.IPluginData
import io.cheddarswallet.bitcoincore.models.BitcoinPaymentData
import io.cheddarswallet.bitcoincore.models.BitcoinSendInfo
import io.cheddarswallet.bitcoincore.models.PublicKey
import io.cheddarswallet.bitcoincore.models.TransactionDataSortType
import io.cheddarswallet.bitcoincore.models.TransactionFilterType
import io.cheddarswallet.bitcoincore.models.TransactionInfo
import io.cheddarswallet.bitcoincore.models.UsedAddress
import io.cheddarswallet.bitcoincore.network.Network
import io.cheddarswallet.bitcoincore.rbf.ReplacementTransaction
import io.cheddarswallet.bitcoincore.rbf.ReplacementTransactionInfo
import io.cheddarswallet.bitcoincore.rbf.ReplacementType
import io.cheddarswallet.bitcoincore.storage.FullTransaction
import io.cheddarswallet.bitcoincore.storage.UnspentOutput
import io.cheddarswallet.bitcoincore.storage.UnspentOutputInfo
import io.cheddarswallet.bitcoincore.storage.UtxoFilters
import io.cheddarswallet.bitcoincore.transactions.scripts.ScriptType
import io.reactivex.Single

abstract class AbstractKit {

    protected abstract var bitcoinCore: BitcoinCore
    protected abstract var network: Network

    fun getUnspentOutputs(filters: UtxoFilters): List<UnspentOutputInfo> {
        return bitcoinCore.getUnspentOutputs(filters)
    }

    val balance
        get() = bitcoinCore.balance

    val lastBlockInfo
        get() = bitcoinCore.lastBlockInfo

    val networkName: String
        get() = network.javaClass.simpleName

    val syncState get() = bitcoinCore.syncState

    val watchAccount: Boolean
        get() = bitcoinCore.watchAccount

    fun start() {
        bitcoinCore.start()
    }

    fun stop() {
        bitcoinCore.stop()
    }

    fun refresh() {
        bitcoinCore.refresh()
    }

    fun onEnterForeground() {
        bitcoinCore.onEnterForeground()
    }

    fun onEnterBackground() {
        bitcoinCore.onEnterBackground()
    }

    fun transactions(fromUid: String? = null, type: TransactionFilterType? = null, limit: Int? = null): Single<List<TransactionInfo>> {
        return bitcoinCore.transactions(fromUid, type, limit)
    }

    fun getTransaction(hash: String): TransactionInfo? {
        return bitcoinCore.getTransaction(hash)
    }

    fun sendInfo(
        value: Long,
        address: String? = null,
        memo: String?,
        senderPay: Boolean = true,
        feeRate: Int,
        unspentOutputs: List<UnspentOutputInfo>?,
        pluginData: Map<Byte, IPluginData> = mapOf(),
        dustThreshold: Int?,
        changeToFirstInput: Boolean,
        filters: UtxoFilters
    ): BitcoinSendInfo {
        return bitcoinCore.sendInfo(
            value = value,
            address = address,
            memo = memo,
            senderPay = senderPay,
            feeRate = feeRate,
            unspentOutputs = unspentOutputs,
            pluginData = pluginData,
            dustThreshold = dustThreshold,
            changeToFirstInput = changeToFirstInput,
            filters = filters,
        )
    }

    fun send(
        address: String,
        memo: String?,
        value: Long,
        senderPay: Boolean = true,
        feeRate: Int,
        sortType: TransactionDataSortType,
        unspentOutputs: List<UnspentOutputInfo>? = null,
        pluginData: Map<Byte, IPluginData> = mapOf(),
        rbfEnabled: Boolean,
        dustThreshold: Int?,
        changeToFirstInput: Boolean,
        filters: UtxoFilters,
    ): FullTransaction {
        return bitcoinCore.send(
            address,
            memo,
            value,
            senderPay,
            feeRate,
            sortType,
            unspentOutputs,
            pluginData,
            rbfEnabled,
            dustThreshold,
            changeToFirstInput,
            filters,
        )
    }

    fun send(
        address: String,
        memo: String?,
        value: Long,
        senderPay: Boolean = true,
        feeRate: Int,
        sortType: TransactionDataSortType,
        pluginData: Map<Byte, IPluginData> = mapOf(),
        rbfEnabled: Boolean,
        dustThreshold: Int?,
        changeToFirstInput: Boolean,
        filters: UtxoFilters,
    ): FullTransaction {
        return bitcoinCore.send(
            address,
            memo,
            value,
            senderPay,
            feeRate,
            sortType,
            null,
            pluginData,
            rbfEnabled,
            dustThreshold,
            changeToFirstInput,
            filters,
        )
    }

    fun send(
        hash: ByteArray,
        memo: String?,
        scriptType: ScriptType,
        value: Long,
        senderPay: Boolean = true,
        feeRate: Int,
        sortType: TransactionDataSortType,
        unspentOutputs: List<UnspentOutputInfo>? = null,
        rbfEnabled: Boolean,
        dustThreshold: Int?,
        changeToFirstInput: Boolean,
        filters: UtxoFilters,
    ): FullTransaction {
        return bitcoinCore.send(
            hash,
            memo,
            scriptType,
            value,
            senderPay,
            feeRate,
            sortType,
            unspentOutputs,
            rbfEnabled,
            dustThreshold,
            changeToFirstInput,
            filters,
        )
    }

    fun send(
        hash: ByteArray,
        memo: String?,
        scriptType: ScriptType,
        value: Long,
        senderPay: Boolean = true,
        feeRate: Int,
        sortType: TransactionDataSortType,
        rbfEnabled: Boolean,
        dustThreshold: Int?,
        changeToFirstInput: Boolean,
        filters: UtxoFilters,
    ): FullTransaction {
        return bitcoinCore.send(
            hash,
            memo,
            scriptType,
            value,
            senderPay,
            feeRate,
            sortType,
            null,
            rbfEnabled,
            dustThreshold,
            changeToFirstInput,
            filters,
        )
    }

    fun redeem(unspentOutput: UnspentOutput, address: String, memo: String?, feeRate: Int, sortType: TransactionDataSortType, rbfEnabled: Boolean): FullTransaction {
        return bitcoinCore.redeem(unspentOutput, address, memo, feeRate, sortType, rbfEnabled)
    }

    fun receiveAddress(): String {
        return bitcoinCore.receiveAddress()
    }

    fun usedAddresses(change: Boolean): List<UsedAddress> {
        return bitcoinCore.usedAddresses(change)
    }

    fun receivePublicKey(): PublicKey {
        return bitcoinCore.receivePublicKey()
    }

    fun changePublicKey(): PublicKey {
        return bitcoinCore.changePublicKey()
    }

    fun validateAddress(address: String, pluginData: Map<Byte, IPluginData>) {
        bitcoinCore.validateAddress(address, pluginData)
    }

    fun parsePaymentAddress(paymentAddress: String): BitcoinPaymentData {
        return bitcoinCore.parsePaymentAddress(paymentAddress)
    }

    fun showDebugInfo() {
        bitcoinCore.showDebugInfo()
    }

    fun statusInfo(): Map<String, Any> {
        return bitcoinCore.statusInfo()
    }

    fun getPublicKeyByPath(path: String): PublicKey {
        return bitcoinCore.getPublicKeyByPath(path)
    }

    fun watchTransaction(filter: TransactionFilter, listener: WatchedTransactionManager.Listener) {
        bitcoinCore.watchTransaction(filter, listener)
    }

    fun maximumSpendableValue(
        address: String?,
        memo: String?,
        feeRate: Int,
        unspentOutputInfos: List<UnspentOutputInfo>?,
        pluginData: Map<Byte, IPluginData>,
        dustThreshold: Int?,
        changeToFirstInput: Boolean,
        filters: UtxoFilters
    ): Long {
        return bitcoinCore.maximumSpendableValue(
            address,
            memo,
            feeRate,
            unspentOutputInfos,
            pluginData,
            dustThreshold,
            changeToFirstInput,
            filters,
        )
    }

    fun minimumSpendableValue(address: String?, dustThreshold: Int?): Int {
        return bitcoinCore.minimumSpendableValue(address, dustThreshold)
    }

    fun getRawTransaction(transactionHash: String): String? {
        return bitcoinCore.getRawTransaction(transactionHash)
    }

    fun speedUpTransaction(
        transactionHash: String,
        minFee: Long
    ): ReplacementTransaction {
        return bitcoinCore.replacementTransaction(
            transactionHash,
            minFee,
            ReplacementType.SpeedUp
        )
    }

    fun cancelTransaction(
        transactionHash: String,
        minFee: Long
    ): ReplacementTransaction {
        val publicKey = bitcoinCore.receivePublicKey()
        val address = bitcoinCore.address(publicKey)
        return bitcoinCore.replacementTransaction(
            transactionHash,
            minFee,
            ReplacementType.Cancel(address, publicKey)
        )
    }

    fun send(replacementTransaction: ReplacementTransaction): FullTransaction {
        return bitcoinCore.send(replacementTransaction)
    }

    fun speedUpTransactionInfo(transactionHash: String): ReplacementTransactionInfo? {
        return bitcoinCore.replacementTransactionInfo(
            transactionHash,
            ReplacementType.SpeedUp
        )
    }

    fun cancelTransactionInfo(transactionHash: String): ReplacementTransactionInfo? {
        val receivePublicKey = bitcoinCore.receivePublicKey()
        val address = bitcoinCore.address(receivePublicKey)
        val type = ReplacementType.Cancel(address, receivePublicKey)
        return bitcoinCore.replacementTransactionInfo(transactionHash, type)
    }

}
