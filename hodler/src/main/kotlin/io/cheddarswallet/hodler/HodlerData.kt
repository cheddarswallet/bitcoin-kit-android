package io.cheddarswallet.hodler

import io.cheddarswallet.bitcoincore.core.IPluginData

data class HodlerData(val lockTimeInterval: LockTimeInterval) : IPluginData
