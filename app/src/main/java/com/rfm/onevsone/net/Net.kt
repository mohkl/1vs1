package com.rfm.onevsone.net

import com.rfm.onevsone.domain.Phase
import com.rfm.onevsone.domain.RoomEngine
import com.rfm.onevsone.domain.RoomSnapshot
import com.rfm.onevsone.domain.Wire
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

enum class Link { CONNECTING, ONLINE, CLOSED }

interface RoomSession {
    val snapshot: StateFlow<RoomSnapshot?>
    val link: StateFlow<Link>
    val error: StateFlow<String?>
    val myId: Int
    val isHost: Boolean
    fun move(cell: Int)
    fun next()
    fun start()
    fun close()
}

object Net {
    const val TCP_PORT = 45455
    const val UDP_PORT = 45454

    fun localIps(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filter { it is Inet4Address && !it.isLoopbackAddress }
            .mapNotNull { it.hostAddress }
    } catch (e: Exception) {
        emptyList()
    }

    fun broadcasts(): List<InetAddress> {
        val l = ArrayList<InetAddress>()
        try {
            for (ni in NetworkInterface.getNetworkInterfaces().toList()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) ia.broadcast?.let { l.add(it) }
            }
        } catch (e: Exception) {
        }
        try {
            l.add(InetAddress.getByName("255.255.255.255"))
        } catch (e: Exception) {
        }
        return l.distinct()
    }
}

/** المضيف: يفتح خادم TCP + خدمة اكتشاف UDP تستجيب لكود الغرفة. لا يحتاج إنترنت. */
class HostServer : RoomSession {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val engine = RoomEngine()
    private val lock = Any()

    private class Peer(val socket: Socket, val out: PrintWriter)

    private val peers = HashMap<Int, Peer>()
    val code: String = (100000 + java.util.Random().nextInt(900000)).toString()

    private val _snapshot = MutableStateFlow<RoomSnapshot?>(null)
    override val snapshot: StateFlow<RoomSnapshot?> = _snapshot
    private val _link = MutableStateFlow(Link.CONNECTING)
    override val link: StateFlow<Link> = _link
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error
    private var _myId = -1
    override val myId: Int get() = _myId
    override val isHost: Boolean = true

    private var server: ServerSocket? = null
    private var udp: DatagramSocket? = null

    fun open(name: String, avatar: String): Boolean {
        return try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(Net.TCP_PORT))
            server = ss
            val ds = DatagramSocket(null)
            ds.reuseAddress = true
            ds.broadcast = true
            ds.bind(InetSocketAddress(Net.UDP_PORT))
            udp = ds
            synchronized(lock) {
                _myId = engine.add(Wire.clean(name), Wire.clean(avatar, 8)) ?: -1
                publish()
            }
            _link.value = Link.ONLINE
            scope.launch { acceptLoop(ss) }
            scope.launch { discoveryLoop(ds, code) }
            true
        } catch (e: Exception) {
            _error.value = "تعذر فتح الغرفة: ${e.message}"
            closeSockets()
            false
        }
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (!ss.isClosed) {
            val s = try {
                ss.accept()
            } catch (e: Exception) {
                break
            }
            scope.launch { serve(s) }
        }
    }

    private fun discoveryLoop(ds: DatagramSocket, code: String) {
        val buf = ByteArray(64)
        while (!ds.isClosed) {
            val pkt = DatagramPacket(buf, buf.size)
            try {
                ds.receive(pkt)
            } catch (e: Exception) {
                break
            }
            val msg = String(pkt.data, 0, pkt.length)
            if (msg == "1VS1?$code") {
                val reply = "1VS1!$code".toByteArray()
                try {
                    ds.send(DatagramPacket(reply, reply.size, pkt.address, pkt.port))
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun serve(s: Socket) {
        var id = -1
        try {
            s.tcpNoDelay = true
            s.soTimeout = 8000
            val rd = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val out = PrintWriter(BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)), true)
            val hello = rd.readLine()?.split("|") ?: return
            if (hello.size < 4 || hello[0] != "HELLO" || hello[1] != code) {
                out.println("REJECT|code"); s.close(); return
            }
            synchronized(lock) {
                val nid = engine.add(Wire.clean(hello[2]), Wire.clean(hello[3], 8))
                if (nid == null) {
                    out.println("REJECT|full"); s.close(); return
                }
                id = nid
                peers[id] = Peer(s, out)
                out.println("YOU|$id")
                publish()
            }
            s.soTimeout = 0
            while (true) {
                val line = rd.readLine() ?: break
                val p = line.split("|")
                if (p[0] == "BYE") break
                synchronized(lock) {
                    when (p[0]) {
                        "MOVE" -> p.getOrNull(1)?.toIntOrNull()?.let { engine.move(id, it) }
                        "NEXT" -> engine.nextRound()
                    }
                    publish()
                }
            }
        } catch (e: Exception) {
        } finally {
            if (id >= 0) {
                synchronized(lock) {
                    peers.remove(id)
                    engine.remove(id)
                    publish()
                }
            }
            try {
                s.close()
            } catch (e: Exception) {
            }
        }
    }

    /** يجب استدعاؤها داخل synchronized(lock) */
    private fun publish() {
        val snap = engine.snapshot()
        _snapshot.value = snap
        val line = Wire.encode(snap)
        for (p in peers.values.toList()) p.out.println(line)
    }

    override fun move(cell: Int) {
        scope.launch { synchronized(lock) { engine.move(_myId, cell); publish() } }
    }

    override fun next() {
        scope.launch { synchronized(lock) { engine.nextRound(); publish() } }
    }

    override fun start() {
        scope.launch { synchronized(lock) { engine.start(); publish() } }
    }

    private fun closeSockets() {
        try { server?.close() } catch (e: Exception) {}
        try { udp?.close() } catch (e: Exception) {}
    }

    override fun close() {
        _link.value = Link.CLOSED
        closeSockets()
        synchronized(lock) {
            peers.values.forEach { try { it.socket.close() } catch (e: Exception) {} }
            peers.clear()
        }
        job.cancel()
    }
}

/** العميل: يبحث عن الغرفة عبر UDP Broadcast بالكود ثم يتصل بالمضيف عبر TCP. */
class ClientConnection : RoomSession {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)

    private val _snapshot = MutableStateFlow<RoomSnapshot?>(null)
    override val snapshot: StateFlow<RoomSnapshot?> = _snapshot
    private val _link = MutableStateFlow(Link.CONNECTING)
    override val link: StateFlow<Link> = _link
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error
    private var _myId = -1
    override val myId: Int get() = _myId
    override val isHost: Boolean = false

    @Volatile private var socket: Socket? = null
    @Volatile private var out: PrintWriter? = null

    private fun fail(msg: String) {
        _error.value = msg
        _link.value = Link.CLOSED
    }

    fun connect(code: String, name: String, avatar: String, manualIp: String) {
        _link.value = Link.CONNECTING
        _error.value = null
        scope.launch {
            try {
                val ip = if (manualIp.isNotBlank()) manualIp.trim() else discover(code)
                if (ip == null) {
                    fail("لم يتم العثور على الغرفة. تأكد من الاتصال بنفس الشبكة/الهوتسبوت ومن صحة الكود")
                    return@launch
                }
                val s = Socket()
                s.connect(InetSocketAddress(ip, Net.TCP_PORT), 4000)
                s.tcpNoDelay = true
                socket = s
                val rd = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val o = PrintWriter(BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)), true)
                out = o
                o.println("HELLO|$code|${Wire.clean(name)}|${Wire.clean(avatar, 8)}")
                s.soTimeout = 6000
                val first = rd.readLine() ?: throw java.io.IOException("closed")
                val fp = first.split("|")
                if (fp[0] == "REJECT") {
                    fail(if (fp.getOrNull(1) == "full") "الغرفة ممتلئة (6 لاعبين)" else "كود الغرفة غير صحيح")
                    s.close()
                    return@launch
                }
                if (fp[0] != "YOU") throw java.io.IOException("bad reply")
                _myId = fp[1].toInt()
                s.soTimeout = 0
                _link.value = Link.ONLINE
                while (true) {
                    val line = rd.readLine() ?: break
                    Wire.decode(line)?.let { _snapshot.value = it }
                }
                if (_link.value != Link.CLOSED) {
                    _error.value = "انقطع الاتصال بالمضيف"
                    _link.value = Link.CLOSED
                }
            } catch (e: Exception) {
                if (_link.value != Link.CLOSED) {
                    _error.value = "تعذر الاتصال: ${e.message}"
                    _link.value = Link.CLOSED
                }
            }
        }
    }

    private fun discover(code: String): String? {
        val sock = DatagramSocket()
        sock.broadcast = true
        sock.soTimeout = 1000
        try {
            val msg = "1VS1?$code".toByteArray()
            for (attempt in 1..5) {
                for (a in Net.broadcasts()) {
                    try {
                        sock.send(DatagramPacket(msg, msg.size, a, Net.UDP_PORT))
                    } catch (e: Exception) {
                    }
                }
                try {
                    val buf = ByteArray(64)
                    val pkt = DatagramPacket(buf, buf.size)
                    sock.receive(pkt)
                    if (String(pkt.data, 0, pkt.length) == "1VS1!$code") return pkt.address.hostAddress
                } catch (e: SocketTimeoutException) {
                }
            }
        } finally {
            sock.close()
        }
        return null
    }

    private fun send(line: String) {
        scope.launch { try { out?.println(line) } catch (e: Exception) {} }
    }

    override fun move(cell: Int) = send("MOVE|$cell")
    override fun next() = send("NEXT")
    override fun start() {}

    override fun close() {
        _link.value = Link.CLOSED
        val o = out
        val s = socket
        Thread {
            try { o?.println("BYE") } catch (e: Exception) {}
            try { s?.close() } catch (e: Exception) {}
        }.start()
        job.cancel()
    }
}
