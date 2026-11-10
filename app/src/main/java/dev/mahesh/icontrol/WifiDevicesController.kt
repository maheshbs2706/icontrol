package dev.mahesh.icontrol

import android.content.Context
import android.net.ConnectivityManager
import dev.mahesh.icontrol.data.NetDevice
import dev.mahesh.icontrol.data.PrefsStore
import dev.mahesh.icontrol.data.RouterApi
import dev.mahesh.icontrol.data.RouterException
import java.net.Inet4Address
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WifiDevicesState(
    /** True until a router password is available; the screen then asks for it. */
    val needsPassword: Boolean = false,
    val busy: Boolean = false,
    val devices: List<NetDevice> = emptyList(),
    val loaded: Boolean = false,
    val message: String? = null,
    /** This TV's address on the network: it can't be blocked from here. */
    val selfIp: String? = null,
    val passwordSaved: Boolean = false,
)

/** Logic behind the "Wi-Fi devices" screen: sign in to the router, list who is connected, block or unblock them. */
class WifiDevicesController(
    private val context: Context,
    private val prefs: PrefsStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(WifiDevicesState())
    val state: StateFlow<WifiDevicesState> = _state

    /** The password in use this time: the saved one, or one typed for this visit only. */
    private var password: String? = null
    private var busy = false

    /** Called whenever the screen opens. */
    fun open() {
        scope.launch {
            val saved = prefs.routerPassword.first()
            password = saved ?: password
            _state.value = WifiDevicesState(passwordSaved = saved != null, needsPassword = password == null)
            if (password != null) refresh()
        }
    }

    fun submitPassword(typed: String, remember: Boolean) {
        if (typed.isEmpty()) return
        password = typed
        scope.launch {
            if (remember) prefs.setRouterPassword(typed)
            _state.value = _state.value.copy(needsPassword = false, passwordSaved = remember, message = null)
            refresh()
        }
    }

    /** Forgets the saved password and asks for it again. */
    fun forgetPassword() {
        password = null
        scope.launch {
            prefs.setRouterPassword(null)
            _state.value = WifiDevicesState(needsPassword = true, message = "Password forgotten")
        }
    }

    fun refresh() = perform { devices() }

    fun toggle(device: NetDevice) = perform {
        if (device.blocked) {
            val stack = device.ruleStack ?: throw RouterException("That block can't be found on the router any more")
            unblock(stack)
        } else {
            block(device.mac, device.name)
        }
        devices()
    }

    private fun perform(work: RouterApi.() -> List<NetDevice>) {
        val pw = password ?: return
        if (busy) return
        busy = true
        _state.value = _state.value.copy(busy = true, message = null)
        scope.launch {
            val (gateway, self) = withContext(Dispatchers.IO) { gatewayAndSelf() }
            try {
                val list = withContext(Dispatchers.IO) {
                    RouterApi.session(gateway ?: FALLBACK_ROUTER, pw, work)
                }
                _state.value = _state.value.copy(devices = list, loaded = true, busy = false, selfIp = self, message = null)
            } catch (e: RouterException) {
                if (e.message?.startsWith("Wrong router password") == true) {
                    password = null
                    prefs.setRouterPassword(null)
                    _state.value = WifiDevicesState(needsPassword = true, message = e.message)
                } else {
                    _state.value = _state.value.copy(busy = false, selfIp = self, message = e.message)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, selfIp = self, message = "Something went wrong talking to the router")
            } finally {
                busy = false
            }
        }
    }

    private fun gatewayAndSelf(): Pair<String?, String?> {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val props = cm.activeNetwork?.let(cm::getLinkProperties) ?: return null to null
        val gateway = props.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
        val self = props.linkAddresses.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
        return gateway to self
    }

    private companion object {
        const val FALLBACK_ROUTER = "192.168.0.1"
    }
}
