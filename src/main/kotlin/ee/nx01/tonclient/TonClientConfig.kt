package ee.nx01.tonclient


data class TonClientConfig(val network: NetworkConfig = NetworkConfig(),
                           val crypto: CryptoConfig? = null,
                           val abi: AbiConfig? = null)


data class NetworkConfig(val serverAddress: String = "net.ton.dev",
                         val networkRetriesCount: Int? = null,
                         val messageRetriesCount: Int? = null,
                         val messageProcessingTimeout: Int? = null,
                         val waitForTimeout: Int? = null,
                         val outOfSyncThreshold: Int? = null,
                         val accessKey: String? = null)

data class CryptoConfig(
    val mnemonicDictionary: Int? = null,
    val mnemonicWordCount: Int? = null,
    val hdkeyDerivationPath: String? = null,
    val hdkeyCompliant: Boolean? = null
)

data class AbiConfig(
    val workchain: Int? = null,
    val messageExpirationTimeout: Int? = null,
    val messageExpirationTimeoutGrowFactor: Int? = null
)