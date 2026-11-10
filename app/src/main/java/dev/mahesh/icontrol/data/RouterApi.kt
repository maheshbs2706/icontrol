package dev.mahesh.icontrol.data

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

/** A device the router currently sees on the network. */
data class NetDevice(
    val mac: String,
    val ip: String,
    val name: String,
    /** "Wi-Fi 2.4 GHz", "Wi-Fi 5 GHz", "Wi-Fi guest", "Cable", ... */
    val connection: String,
    val online: Boolean,
    val blocked: Boolean,
    /** The router's Access Control rule that blocks it; needed to lift the block. */
    val ruleStack: String? = null,
)

class RouterException(message: String) : Exception(message)

/**
 * Talks to a TP-Link Archer C5 (and routers with the same "cgi_gdpr" web UI) the way its own admin page does:
 * an RSA-signed, AES-CBC encrypted JSON API. Blocking a device adds a Drop rule for its MAC to the router's
 * Access Control blacklist; unblocking deletes that rule.
 *
 * Everything here blocks on the network, so call it from a background thread. The router allows one admin session
 * at a time, so use [RouterApi.session], which always signs out again.
 */
class RouterApi private constructor(
    private val host: String,
    private val username: String,
    private val password: String,
) {
    private val cookies = linkedMapOf<String, String>()
    private var token: String? = null
    private var modulus = BigInteger.ZERO
    private var exponent = BigInteger.ZERO
    private var seq = 0
    private var loggedIn = false

    // Session AES key and IV: 16 ASCII characters each, as the router's own page generates them.
    private val aesKey = randomDigits()
    private val aesIv = randomDigits()
    private val credentialHash = md5Hex(username + password)

    // ---- Public operations ---------------------------------------------------------------------------------------

    /** Devices on the network (online ones first), including blocked devices that have left it. */
    fun devices(): List<NetDevice> {
        val hosts = list("gl", "DEV2_HOST_ENTRY", listOf("active", "X_TP_LanConnType", "physAddress", "IPAddress", "hostName"))
        val rules = blockRules()
        val blockedByMac = rules.associateBy { it.mac }
        val seen = mutableSetOf<String>()
        val result = mutableListOf<NetDevice>()
        for (h in hosts) {
            val mac = h.optString("physAddress").uppercase()
            if (mac.isEmpty() || !seen.add(mac)) continue
            val online = h.optString("active") == "1"
            val rule = blockedByMac[mac]
            if (!online && rule == null) continue
            result += NetDevice(
                mac = mac,
                ip = h.optString("IPAddress"),
                name = h.optString("hostName").trim().takeIf { it.isNotEmpty() && it != "Unknown" } ?: "Unknown device",
                connection = connectionName(h.optString("X_TP_LanConnType").toIntOrNull()),
                online = online,
                blocked = rule != null,
                ruleStack = rule?.stack,
            )
        }
        // Blocked devices the router no longer lists at all still need a way to be unblocked.
        for (rule in rules) {
            if (seen.add(rule.mac)) {
                result += NetDevice(rule.mac, "", rule.name.ifEmpty { "Blocked device" }, "Not connected", false, true, rule.stack)
            }
        }
        return result.sortedWith(compareByDescending<NetDevice> { it.online }.thenBy { it.name.lowercase() })
    }

    /** Blocks [mac] from the internet. Switches the router's Access Control on, in blacklist mode, if needed. */
    fun block(mac: String, name: String) {
        val firewall = act("go", "DEV2_FIREWALL", mapOf("X_TP_EnableACL" to "", "X_TP_ACLMode" to "", "enable" to "")).obj()
        if (firewall.optString("X_TP_ACLMode") != "1") {
            throw RouterException("The router's Access Control is set to allow-list mode. Switch it to Block list on the router first.")
        }
        if (firewall.optString("X_TP_EnableACL") != "1") {
            ok(act("so", "DEV2_FIREWALL", mapOf("X_TP_EnableACL" to "1", "X_TP_ACLMode" to "1", "enable" to firewall.optString("enable", "0"))))
        }
        val chain = blackChain()
        val ruleName = "iControl-" + mac.replace(":", "").takeLast(6)
        ok(
            act(
                "ao", "DEV2_FW_CHAIN_RULE",
                mapOf(
                    "enable" to "1", "X_TP_RuleType" to "2", "X_TP_RuleName" to ruleName, "X_TP_SourceType" to "2",
                    "sourceIP" to "", "X_TP_SourceMACAddress" to mac, "target" to "Drop",
                ),
                pstack = chain,
            ),
        )
    }

    /** Lifts the block described by the rule at [ruleStack]. */
    fun unblock(ruleStack: String) {
        ok(act("do", "DEV2_FW_CHAIN_RULE", emptyMap(), stack = ruleStack))
    }

    /** Signs in. Throws [RouterException] with a plain-language reason when it can't. */
    private fun login() {
        val params = http("GET", "/cgi/getGDPRParm?_=${System.currentTimeMillis()}").body
        modulus = BigInteger(Regex("var nn=\"([0-9a-fA-F]+)\"").find(params)?.groupValues?.get(1) ?: fail("Not a supported router"), 16)
        exponent = BigInteger(Regex("var ee=\"([0-9a-fA-F]+)\"").find(params)?.groupValues?.get(1) ?: fail("Not a supported router"), 16)
        seq = Regex("var seq=\"(\\d+)\"").find(params)?.groupValues?.get(1)?.toInt() ?: fail("Not a supported router")

        val loginJson = "{\"data\":{\"UserName\":\"${b64(username)}\",\"Passwd\":\"${b64(password)}\",\"Action\": \"1\"," +
            "\"stack\":\"0,0,0,0,0,0\",\"pstack\":\"0,0,0,0,0,0\"},\"operation\":\"cgi\",\"oid\":\"/cgi/login\"}"
        val reply = decrypt(post(loginJson, isLogin = true))
        when (val ret = Regex("\\\$\\.ret=(\\d+);").find(reply)?.groupValues?.get(1)?.toInt()) {
            0 -> Unit
            71233 -> fail("Wrong router password")
            null -> fail("Unexpected reply from the router")
            else -> fail("The router refused the login (code $ret). It may be locked for a few minutes after wrong passwords.")
        }
        loggedIn = true
        // The page after login carries the token every later request must send back.
        val page = http("GET", "/?_=${System.currentTimeMillis()}").body
        token = Regex("var token=\"([^\"]*)\";").find(page)?.groupValues?.get(1) ?: fail("Could not finish signing in")
    }

    private fun logout() {
        if (!loggedIn) return
        runCatching {
            post("{\"data\":{\"stack\":\"0,0,0,0,0,0\",\"pstack\":\"0,0,0,0,0,0\"},\"operation\":\"cgi\",\"oid\":\"/cgi/logout\"}", isLogin = false)
        }
        loggedIn = false
    }

    // ---- Requests ------------------------------------------------------------------------------------------------

    private data class Rule(val mac: String, val name: String, val stack: String)

    private fun blackChain(): String {
        val chains = list("gl", "DEV2_FW_CHAIN", emptyList())
        for (c in chains) if (c.optString("name") == "ACCESSCTL_BLACK") return c.optString("stack")
        fail("The router has no Access Control block list")
    }

    private fun blockRules(): List<Rule> {
        val chain = blackChain()
        return list("gs", "DEV2_FW_CHAIN_RULE", emptyList(), pstack = chain)
            .filter { it.optString("target") == "Drop" && it.optString("X_TP_SourceMACAddress").isNotEmpty() }
            .map { Rule(it.optString("X_TP_SourceMACAddress").uppercase(), it.optString("X_TP_RuleName"), it.optString("stack")) }
    }

    private fun list(op: String, oid: String, attrs: List<String>, pstack: String = ZERO_STACK): List<JSONObject> {
        val data = act(op, oid, attrs.associateWith { "" }, pstack = pstack).opt("data")
        return when (data) {
            is JSONArray -> (0 until data.length()).mapNotNull { data.optJSONObject(it) }
            is JSONObject -> listOf(data)
            else -> emptyList()
        }
    }

    private fun act(
        op: String,
        oid: String,
        attrs: Map<String, String>,
        stack: String = ZERO_STACK,
        pstack: String = ZERO_STACK,
    ): JSONObject {
        val sb = StringBuilder("{\"data\":{\"stack\":\"$stack\",\"pstack\":\"$pstack\"")
        for ((k, v) in attrs) sb.append(",\"").append(esc(k)).append("\":\"").append(esc(v)).append('"')
        sb.append("},\"operation\":\"").append(op).append("\",\"oid\":\"").append(oid).append("\"}")
        val text = decrypt(post(sb.toString(), isLogin = false)).trim()
        return if (text.isEmpty()) JSONObject() else JSONObject(text)
    }

    private fun ok(reply: JSONObject) {
        if (!reply.optBoolean("success", false)) throw RouterException("The router rejected the change (code ${reply.optInt("errorcode")})")
    }

    private fun JSONObject.obj(): JSONObject {
        ok(this)
        return optJSONObject("data") ?: JSONObject()
    }

    /** Sends one encrypted request and returns the router's (still encrypted) reply text. */
    private fun post(json: String, isLogin: Boolean): String {
        val encrypted = aesEncrypt(json)
        val sign = signature(seq + encrypted.length, isLogin)
        val body = "sign=$sign\r\ndata=$encrypted\r\n"
        return http("POST", "/cgi_gdpr?9", body).body
    }

    // ---- Crypto --------------------------------------------------------------------------------------------------

    private fun aesEncrypt(text: String): String {
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey.toByteArray(), "AES"), IvParameterSpec(aesIv.toByteArray()))
        return Base64.encodeToString(c.doFinal(text.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(text: String): String {
        val clean = text.trim()
        if (clean.isEmpty()) return ""
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey.toByteArray(), "AES"), IvParameterSpec(aesIv.toByteArray()))
        return String(c.doFinal(Base64.decode(clean, Base64.DEFAULT)), Charsets.UTF_8)
    }

    /** The router's request signature: the AES key (login only), credential hash and length, RSA-encrypted in blocks. */
    private fun signature(length: Int, isLogin: Boolean): String {
        val text = if (isLogin) "key=$aesKey&iv=$aesIv&h=$credentialHash&s=$length" else "h=$credentialHash&s=$length"
        val block = modulus.bitLength().plus(7) / 8
        val out = StringBuilder()
        var pos = 0
        val bytes = text.toByteArray()
        while (pos < bytes.size) {
            val chunk = bytes.copyOfRange(pos, minOf(pos + block, bytes.size)).copyOf(block) // zero-padded, no RSA padding
            val encrypted = BigInteger(1, chunk).modPow(exponent, modulus).toString(16)
            out.append(encrypted.padStart(block * 2, '0'))
            pos += block
        }
        return out.toString()
    }

    // ---- Minimal HTTP --------------------------------------------------------------------------------------------

    private class Reply(val body: String)

    /**
     * The router sometimes answers a POST with a chunked body and no status line or headers at all, which
     * HttpURLConnection rejects, so this speaks HTTP itself.
     */
    private fun http(method: String, path: String, body: String? = null): Reply {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, 80), 5_000)
            socket.soTimeout = 10_000
            val payload = body?.toByteArray()
            val head = StringBuilder("$method $path HTTP/1.1\r\nHost: $host\r\nAccept: */*\r\n")
                .append("User-Agent: Mozilla/5.0 (Linux; Android 11) iControl\r\nReferer: http://$host/\r\nConnection: close\r\n")
            token?.let { head.append("TokenID: $it\r\n") }
            if (cookies.isNotEmpty()) head.append("Cookie: ").append(cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }).append("\r\n")
            if (payload != null) head.append("Content-Type: text/plain;charset=UTF-8\r\nContent-Length: ${payload.size}\r\n")
            head.append("\r\n")
            socket.getOutputStream().apply {
                write(head.toString().toByteArray())
                if (payload != null) write(payload)
                flush()
            }
            return readReply(socket.getInputStream().buffered())
        }
    }

    private fun readReply(input: InputStream): Reply {
        var line = readLine(input) ?: throw RouterException("The router did not answer")
        var chunked = false
        var length = -1
        if (line.startsWith("HTTP/")) {
            val status = line.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
            while (true) {
                line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val key = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim()
                when (key) {
                    "transfer-encoding" -> chunked = value.contains("chunked", ignoreCase = true)
                    "content-length" -> length = value.toIntOrNull() ?: -1
                    "set-cookie" -> value.substringBefore(';').split('=', limit = 2).let { if (it.size == 2) cookies[it[0]] = it[1] }
                }
            }
            if (status == 406 || status == 403) throw RouterException("The router refused the request (HTTP $status)")
            line = ""
        } else {
            chunked = true // no headers at all: the first line is already a chunk size
        }
        val out = ByteArrayOutputStream()
        when {
            chunked -> {
                var sizeLine: String? = if (line.isEmpty()) readLine(input) else line
                while (sizeLine != null) {
                    val size = sizeLine.trim().substringBefore(';').toIntOrNull(16) ?: break
                    if (size == 0) break
                    out.write(readFully(input, size))
                    readLine(input) // the CRLF after the chunk
                    sizeLine = readLine(input)
                }
            }
            length >= 0 -> out.write(readFully(input, length))
            else -> input.copyTo(out)
        }
        return Reply(String(out.toByteArray(), Charsets.UTF_8))
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
        }
    }

    private fun readFully(input: InputStream, count: Int): ByteArray {
        val buf = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = input.read(buf, read, count - read)
            if (n < 0) break
            read += n
        }
        return buf.copyOf(read)
    }

    // ---- Helpers -------------------------------------------------------------------------------------------------

    private fun fail(message: String): Nothing = throw RouterException(message)

    companion object {
        private const val ZERO_STACK = "0,0,0,0,0,0"

        /** Signs in, runs [block], and always signs out so the router's single admin slot is freed. */
        fun <T> session(host: String, password: String, block: RouterApi.() -> T): T {
            val api = RouterApi(host, "admin", password)
            try {
                api.login()
                return api.block()
            } catch (e: RouterException) {
                throw e
            } catch (e: java.io.IOException) {
                throw RouterException("Can't reach the router at $host")
            } finally {
                api.logout()
            }
        }

        private fun randomDigits(): String = (System.currentTimeMillis().toString() + Random.nextInt(100_000_000, 999_999_999)).take(16)

        private fun md5Hex(text: String) = MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        private fun b64(text: String) = Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP)

        private fun esc(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"")

        private fun connectionName(type: Int?) = when (type) {
            0 -> "Cable"
            1, 8 -> "Wi-Fi 2.4 GHz"
            3, 9 -> "Wi-Fi 5 GHz"
            2, 4 -> "Wi-Fi guest"
            5, 6, 10 -> "Wi-Fi"
            else -> "Network"
        }
    }
}
