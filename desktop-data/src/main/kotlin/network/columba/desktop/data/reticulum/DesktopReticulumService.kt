package network.columba.desktop.data.reticulum

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import network.reticulum.Reticulum
import network.reticulum.common.DestinationDirection
import network.reticulum.common.DestinationType
import network.reticulum.crypto.BouncyCastleProvider
import network.reticulum.crypto.CryptoProvider
import network.reticulum.destination.Destination
import network.reticulum.identity.Identity
import network.reticulum.interfaces.InterfaceAdapter
import network.reticulum.interfaces.auto.AutoInterface
import network.reticulum.interfaces.tcp.TCPClientInterface
import network.reticulum.lxmf.LXMRouter
import network.reticulum.lxmf.LXMessage
import network.reticulum.transport.Transport
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Desktop Reticulum networking service.
 *
 * Mirrors the Android NativeReticulumProtocol's responsibilities for the
 * Compose Multiplatform desktop app:
 *  - Boots Reticulum + LXMRouter against an on-disk config dir
 *  - Loads/creates the user delivery identity (compatible with Android
 *    on-the-wire formats — both stacks use the same reticulum-kt Identity)
 *  - Brings up TCP client and Auto interfaces so we can reach announced
 *    peers and discover others on the LAN
 *  - Exposes a coroutine-friendly send / inbound surface that the
 *    repository layer can consume
 *
 * Single-instance per process. Held by Koin as a singleton.
 */
class DesktopReticulumService(
    private val configDir: File,
) {
    private val logger = LoggerFactory.getLogger(DesktopReticulumService::class.java)

    private val crypto: CryptoProvider = BouncyCastleProvider()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var reticulum: Reticulum? = null
    private var router: LXMRouter? = null
    private var deliveryIdentity: Identity? = null
    private var deliveryDestination: Destination? = null
    private val ownedInterfaces = mutableListOf<Any>()

    private val _state = MutableStateFlow(State.STOPPED)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Inbound LXMF messages, observed by the conversation repository. */
    private val _inbound = MutableStateFlow<LXMessage?>(null)
    val inbound: StateFlow<LXMessage?> = _inbound.asStateFlow()

    /**
     * Announces from peers (lxmf.delivery + propagation + nomadnet). The
     * conversation repository subscribes so peer display names land in the UI
     * as soon as we hear from a contact, matching the Android behavior.
     */
    private val _announces = MutableSharedFlow<AnnounceEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val announces: SharedFlow<AnnounceEvent> = _announces.asSharedFlow()

    /**
     * Outbound delivery state changes (sent/delivered/failed). Mirrors the
     * Android NativeReticulumProtocol#deliveryStatus stream so the repository
     * can update message rows once the LXMF router learns the result.
     */
    private val _deliveryStatus = MutableSharedFlow<DeliveryStatusUpdate>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val deliveryStatus: SharedFlow<DeliveryStatusUpdate> = _deliveryStatus.asSharedFlow()

    enum class State { STOPPED, STARTING, READY, ERROR }

    /** Snapshot of an inbound announce, mirroring Android's AnnounceEvent. */
    data class AnnounceEvent(
        val destinationHash: ByteArray,
        val identityHash: String,
        val appData: ByteArray?,
        val hops: Int,
        val timestamp: Long,
        val aspect: String,
        val displayName: String?,
        val receivingInterface: String?,
    )

    /** Outbound message lifecycle update. Status is one of sent/delivered/failed. */
    data class DeliveryStatusUpdate(
        val lxmfHashHex: String,
        val status: String,
        val timestamp: Long,
    )

    /** Resolves the path Reticulum should use for its on-disk state. */
    private val rnsConfigDir: File by lazy {
        File(configDir, "reticulum").also { it.mkdirs() }
    }

    /** Resolves the path used to persist the user's long-lived delivery identity. */
    private val identityFile: File
        get() = File(configDir, "delivery_identity")

    /** Returns the active delivery identity hash (hex), if any. */
    val deliveryIdentityHash: String?
        get() = deliveryIdentity?.hexHash

    val deliveryDestinationHash: ByteArray?
        get() = deliveryDestination?.hash

    val lxmRouter: LXMRouter?
        get() = router

    val cryptoProvider: CryptoProvider
        get() = crypto

    /**
     * Boot the stack. Idempotent — calling start() twice is a no-op once READY.
     */
    @Synchronized
    fun start(displayName: String) {
        if (_state.value == State.READY) {
            logger.info("DesktopReticulumService already started")
            return
        }
        _state.value = State.STARTING
        try {
            // 1. Load or create the delivery identity. Persist as raw bytes
            //    (rns-kt Identity#toFile / Identity.fromFile both use raw key
            //    layout, which is compatible with the Android side and with
            //    Python Reticulum-generated identities).
            val identity = loadOrCreateIdentity()
            deliveryIdentity = identity

            // 2. Boot Reticulum core. We pass our identity as the transport
            //    identity so the node hash stays stable across restarts. The
            //    Android client uses an ephemeral transport identity for
            //    privacy; on a desktop the user's setup is more pinned, but
            //    we still keep transport disabled by default — this is a
            //    leaf node, not a relay.
            reticulum = Reticulum.Companion.start(
                /* configDir = */ rnsConfigDir.absolutePath,
                /* enableTransport = */ false,
                /* shareInstance = */ false,
                /* sharedInstancePort = */ Reticulum.DEFAULT_SHARED_INSTANCE_PORT,
                /* connectToSharedInstance = */ false,
                /* transportIdentity = */ identity,
            )

            // 3. Build the LXMF router for messaging. storagePath holds queues
            //    and transient state; using a subdir keeps it segregated from
            //    Reticulum's own caches.
            val lxmfStorage = File(rnsConfigDir, "lxmf").apply { mkdirs() }
            val r = LXMRouter(
                /* identity = */ identity,
                /* storagePath = */ lxmfStorage.absolutePath,
                /* enablePropagation = */ false,
            )
            router = r

            // 4. Register a delivery destination so peers can address us.
            //    Display name is broadcast in our announces.
            deliveryDestination = r.registerDeliveryIdentity(identity, displayName, null)

            // 5. Wire delivery callbacks. Inbound messages get pushed to the
            //    repository via the inbound StateFlow.
            r.registerDeliveryCallback { message ->
                logger.info("LXMF delivery received from {}", message.sourceHash?.toHex())
                _inbound.value = message
            }
            r.registerFailedDeliveryCallback { message ->
                logger.warn("LXMF delivery failed for hash={}", message.hash?.toHex())
                message.hash?.toHex()?.let { hex ->
                    _deliveryStatus.tryEmit(DeliveryStatusUpdate(hex, "failed", System.currentTimeMillis()))
                }
            }

            // 6. Subscribe to peer announces so the repo can refresh peer
            //    names. Done before interfaces come up to make sure we never
            //    drop the very first announce.
            registerAnnounceHandlers()

            // 7. Bring up default interfaces. Auto-discovers same-LAN peers
            //    via UDP multicast and connects to the well-known public TCP
            //    test net so single-host setups still see traffic.
            startDefaultInterfaces()

            // 7. Start router processing loop. Must come after interfaces so
            //    the first announce has somewhere to leave from.
            r.start()

            _state.value = State.READY
            logger.info(
                "DesktopReticulumService READY identity={} dest={}",
                identity.hexHash,
                deliveryDestination?.hash?.toHex(),
            )
            return
        } catch (e: Exception) {
            logger.error("Failed to start DesktopReticulumService", e)
            _state.value = State.ERROR
            // Best-effort cleanup so a retry has a clean slate.
            runCatching { router?.close() }
            runCatching { Reticulum.Companion.stop() }
            router = null
            reticulum = null
            deliveryIdentity = null
            deliveryDestination = null
            throw e
        }
    }

    @Synchronized
    fun stop() {
        if (_state.value == State.STOPPED) return
        logger.info("Stopping DesktopReticulumService")
        runCatching { router?.stop() }
        runCatching { router?.close() }
        ownedInterfaces.forEach { iface ->
            runCatching {
                when (iface) {
                    is TCPClientInterface -> iface.stop()
                    is AutoInterface -> iface.detach()
                }
            }
        }
        ownedInterfaces.clear()
        runCatching { Reticulum.Companion.stop() }
        router = null
        reticulum = null
        deliveryIdentity = null
        deliveryDestination = null
        _state.value = State.STOPPED
    }

    /**
     * Send an LXMF message to a peer. Returns the message hash on enqueue.
     * Throws if the recipient identity isn't known and a path request times out.
     */
    suspend fun sendMessage(
        recipientHash: ByteArray,
        content: String,
        title: String = "",
    ): ByteArray {
        val r = router ?: error("DesktopReticulumService not started")
        val sourceDest = deliveryDestination ?: error("Delivery destination missing")

        val recipientIdentity = resolveRecipientIdentity(recipientHash)
        val recipientDest = Destination.Companion.create(
            recipientIdentity,
            DestinationDirection.OUT,
            DestinationType.SINGLE,
            LXMRouter.APP_NAME,
            LXMRouter.DELIVERY_ASPECT,
        )

        val message = LXMessage.Companion.create(
            destination = recipientDest,
            source = sourceDest,
            content = content,
            title = title,
            fields = mutableMapOf<Int, Any>(),
            desiredMethod = network.reticulum.lxmf.DeliveryMethod.OPPORTUNISTIC,
        )

        // Per-message delivery callback. LXMRouter invokes this when the
        // recipient confirms reception (proof packet for opportunistic /
        // direct ack for link delivery).
        message.deliveryCallback = { delivered: LXMessage ->
            delivered.hash?.toHex()?.let { hex ->
                _deliveryStatus.tryEmit(DeliveryStatusUpdate(hex, "delivered", System.currentTimeMillis()))
            }
        }

        r.handleOutbound(message)
        val hash = message.hash ?: ByteArray(0)
        // Optimistic "sent" emission so the repo can immediately flip status
        // from queued/sending to sent the moment the router accepts the
        // outbound packet. Final delivered/failed comes via the callbacks above.
        if (hash.isNotEmpty()) {
            _deliveryStatus.tryEmit(DeliveryStatusUpdate(hash.toHex(), "sent", System.currentTimeMillis()))
        }
        return hash
    }

    /** Sends an announce so peers can discover us. */
    fun announce(appData: ByteArray? = null) {
        val r = router ?: return
        val dest = deliveryDestination ?: return
        r.announce(dest, appData ?: ByteArray(0))
    }

    private suspend fun resolveRecipientIdentity(destinationHash: ByteArray): Identity {
        Identity.Companion.recall(destinationHash)?.let { return it }
        Identity.Companion.recallByIdentityHash(destinationHash)?.let { return it }

        // Path-not-known. Ask the network and wait briefly. 10s matches the
        // Android NativeMessageSender deadline so user-visible latency is
        // consistent across the two clients.
        Transport.requestPath(destinationHash, null, null)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(250)
            Identity.Companion.recall(destinationHash)?.let { return it }
            Identity.Companion.recallByIdentityHash(destinationHash)?.let { return it }
        }
        error("Recipient identity not found after path request: ${destinationHash.toHex().take(16)}")
    }

    private fun loadOrCreateIdentity(): Identity {
        if (identityFile.exists()) {
            val loaded = runCatching {
                Identity.Companion.fromFile(identityFile.absolutePath, crypto)
            }.getOrNull()
            if (loaded != null) {
                logger.info("Loaded delivery identity {}", loaded.hexHash)
                return loaded
            }
            logger.warn("Failed to load existing identity at {}, creating new one", identityFile.absolutePath)
        }
        val fresh = Identity.Companion.create(crypto)
        if (!fresh.toFile(identityFile.absolutePath)) {
            logger.warn("Failed to persist delivery identity to {}", identityFile.absolutePath)
        }
        logger.info("Created new delivery identity {}", fresh.hexHash)
        return fresh
    }

    /**
     * Mirrors NativeReticulumProtocol.registerAnnounceHandlers — register the
     * known LXMF/NomadNet aspects so Transport will resolve them, then attach
     * a single RichAnnounceHandler that fans every announce into our SharedFlow.
     */
    private fun registerAnnounceHandlers() {
        Transport.registerKnownAspect("lxmf.delivery")
        Transport.registerKnownAspect("lxmf.propagation")
        Transport.registerKnownAspect("nomadnetwork.node")
        Transport.registerKnownAspect("lxst.telephony")

        val handler = object : network.reticulum.transport.RichAnnounceHandler {
            override fun handleAnnounceWithContext(
                destinationHash: ByteArray,
                announcedIdentity: Identity,
                appData: ByteArray?,
                hops: Int,
                receivingInterfaceName: String?,
                matchedAspect: String?,
            ): Boolean {
                val aspect = matchedAspect ?: return false
                val effectiveHops = if (hops > 0) hops else (Transport.hopsTo(destinationHash) ?: 0)
                val displayName = parseDisplayName(appData, aspect)
                _announces.tryEmit(
                    AnnounceEvent(
                        destinationHash = destinationHash,
                        identityHash = announcedIdentity.hexHash,
                        appData = appData,
                        hops = effectiveHops,
                        timestamp = System.currentTimeMillis(),
                        aspect = aspect,
                        displayName = displayName,
                        receivingInterface = receivingInterfaceName,
                    ),
                )
                if (aspect == "lxmf.propagation" && appData != null) {
                    runCatching { router?.handlePropagationAnnounce(destinationHash, announcedIdentity, appData) }
                }
                return true
            }
        }
        Transport.registerAnnounceHandler(handler, null)
    }

    /**
     * Best-effort display name extraction. Mirrors AppDataParser on Android
     * for the simple lxmf.delivery case (msgpack array starting with the
     * peer name as the first element); falls back to UTF-8 for the rest.
     */
    private fun parseDisplayName(appData: ByteArray?, aspect: String): String? {
        if (appData == null || appData.isEmpty()) return null
        return runCatching {
            when (aspect) {
                "nomadnetwork.node" -> String(appData, Charsets.UTF_8).split(":").firstOrNull()?.takeIf { it.isNotBlank() }
                else -> {
                    val first = appData[0].toInt() and 0xFF
                    if (first in 0x90..0x9f || first == 0xdc) {
                        val unpacker = org.msgpack.core.MessagePack.newDefaultUnpacker(appData)
                        val arrayLen = unpacker.unpackArrayHeader()
                        if (arrayLen < 1) return@runCatching null
                        val format = unpacker.nextFormat
                        when (format.valueType) {
                            org.msgpack.value.ValueType.NIL -> { unpacker.unpackNil(); null }
                            org.msgpack.value.ValueType.BINARY -> {
                                val len = unpacker.unpackBinaryHeader()
                                String(unpacker.readPayload(len), Charsets.UTF_8)
                            }
                            org.msgpack.value.ValueType.STRING -> unpacker.unpackString()
                            else -> null
                        }
                    } else {
                        String(appData, Charsets.UTF_8)
                    }
                }
            }
        }.getOrNull()
    }

    private fun startDefaultInterfaces() {
        // AutoInterface: UDP multicast peer discovery on the local segment.
        // Same default the Python RNS reference and the Android client use.
        runCatching {
            val auto = AutoInterface(
                name = "Default-Auto",
                discoveryScope = "5", // site-local
            )
            auto.start()
            Transport.registerInterface(InterfaceAdapter.Companion.getOrCreate(auto))
            ownedInterfaces.add(auto)
            logger.info("Started AutoInterface")
        }.onFailure { e ->
            logger.warn("AutoInterface failed to start", e)
        }
    }

    /**
     * Dynamically add a TCPClient interface (e.g. user-configured testnet entry).
     * Safe to call after start(). The interface is owned by this service and
     * stopped on stop().
     */
    fun addTcpClientInterface(host: String, port: Int, name: String = "TCP-$host:$port") {
        check(_state.value == State.READY) { "Service must be READY" }
        val iface = TCPClientInterface(
            /* name = */ name,
            /* targetHost = */ host,
            /* targetPort = */ port,
            /* useKissFraming = */ false,
            /* keepAlive = */ 600,
            /* hwMtu = */ null,
            /* ifacEnabled = */ false,
            /* ifacNetname = */ "",
            /* ifacNetkey = */ "",
            /* coroutineScope = */ scope,
        )
        iface.start()
        Transport.registerInterface(InterfaceAdapter.Companion.getOrCreate(iface))
        ownedInterfaces.add(iface)
        logger.info("Started TCPClientInterface to {}:{}", host, port)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
