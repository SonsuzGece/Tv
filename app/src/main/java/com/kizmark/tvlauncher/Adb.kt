package com.kizmark.tvlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Base64
import java.io.DataInputStream
import java.io.File
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey

/**
 * TV'nin kendi ağ hata ayıklamasına (127.0.0.1:5555) bağlanan küçük ADB istemcisi.
 * Normal bir uygulamanın yapamadığı işleri (uygulama kapat/aç, uyku, hızlandır) bununla yapar.
 * İlk kullanımda TV "Hata ayıklamaya izin ver?" diye sorar: "Her zaman izin ver" seçilmeli.
 */
object Adb {
    private const val CNXN = 0x4E584E43
    private const val AUTH = 0x48545541
    private const val OPEN = 0x4E45504F
    private const val OKAY = 0x59414B4F
    private const val CLSE = 0x45534C43
    private const val WRTE = 0x45545257
    private val SHA1_PREFIX = byteArrayOf(
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    )
    private val lock = Any()

    private class Key(val n: BigInteger, val d: BigInteger, val e: BigInteger)

    private fun key(ctx: Context): Key {
        val f = File(ctx.filesDir, "adbkey.txt")
        if (f.exists()) {
            val parts = f.readText().trim().split("\n")
            if (parts.size == 3) {
                return Key(BigInteger(parts[0], 16), BigInteger(parts[1], 16), BigInteger(parts[2], 16))
            }
        }
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        val priv = gen.generateKeyPair().private as RSAPrivateCrtKey
        val k = Key(priv.modulus, priv.privateExponent, priv.publicExponent)
        f.writeText(k.n.toString(16) + "\n" + k.d.toString(16) + "\n" + k.e.toString(16))
        return k
    }

    private fun le(v: BigInteger, len: Int): ByteArray {
        val be = v.toByteArray()
        val out = ByteArray(len)
        for (i in 0 until len) {
            val src = be.size - 1 - i
            out[i] = if (src >= 0) be[src] else 0
        }
        return out
    }

    private fun publicKey(k: Key): ByteArray {
        val two32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = two32.subtract(k.n.mod(two32).modInverse(two32)).mod(two32)
        val rr = BigInteger.ONE.shiftLeft(4096).mod(k.n)
        val bb = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(64)
        bb.putInt(n0inv.toInt())
        bb.put(le(k.n, 256))
        bb.put(le(rr, 256))
        bb.putInt(k.e.toInt())
        val b64 = Base64.encodeToString(bb.array(), Base64.NO_WRAP)
        return (b64 + " tvlauncher@tv\u0000").toByteArray()
    }

    private fun sign(k: Key, token: ByteArray): ByteArray {
        val t = SHA1_PREFIX + token
        val em = ByteArray(256)
        em[0] = 0
        em[1] = 1
        val padEnd = 256 - t.size - 1
        for (i in 2 until padEnd) em[i] = 0xFF.toByte()
        em[padEnd] = 0
        System.arraycopy(t, 0, em, padEnd + 1, t.size)
        val s = BigInteger(1, em).modPow(k.d, k.n)
        val raw = s.toByteArray()
        val out = ByteArray(256)
        val n = minOf(raw.size, 256)
        System.arraycopy(raw, raw.size - n, out, 256 - n, n)
        return out
    }

    private class Msg(val cmd: Int, val a0: Int, val a1: Int, val data: ByteArray)

    private class Conn(port: Int) {
        val sock = Socket()
        val inp: DataInputStream
        val out: OutputStream

        init {
            sock.connect(InetSocketAddress("127.0.0.1", port), 2000)
            sock.tcpNoDelay = true
            inp = DataInputStream(sock.getInputStream())
            out = sock.getOutputStream()
        }

        fun send(cmd: Int, a0: Int, a1: Int, data: ByteArray = ByteArray(0)) {
            var sum = 0
            for (b in data) sum += (b.toInt() and 0xFF)
            val h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            h.putInt(cmd).putInt(a0).putInt(a1).putInt(data.size).putInt(sum).putInt(cmd xor -1)
            out.write(h.array())
            if (data.isNotEmpty()) out.write(data)
            out.flush()
        }

        fun recv(timeoutMs: Int): Msg {
            sock.soTimeout = timeoutMs
            val h = ByteArray(24)
            inp.readFully(h)
            val bb = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
            val cmd = bb.int
            val a0 = bb.int
            val a1 = bb.int
            val len = bb.int
            val data = ByteArray(len)
            if (len > 0) inp.readFully(data)
            return Msg(cmd, a0, a1, data)
        }

        fun close() = runCatching { sock.close() }
    }

    /**
     * Komutu TV'de çalıştırır, çıktısını döndürür. Bağlanamazsa null.
     * allowPrompt = false: izin daha önce verilmediyse TV'de soru çıkarmadan vazgeçer.
     */
    fun exec(ctx: Context, command: String, allowPrompt: Boolean = true, timeoutMs: Int = 15000): String? {
        synchronized(lock) {
            val prefs = Prefs(ctx)
            var c: Conn? = null
            try {
                c = Conn(5555)
                val k = key(ctx)
                c.send(CNXN, 0x01000000, 4096, "host::\u0000".toByteArray())
                var signed = false
                while (true) {
                    val m = c.recv(if (signed) 60000 else 5000)
                    if (m.cmd == CNXN) break
                    if (m.cmd != AUTH || m.a0 != 1) continue
                    if (!signed) {
                        c.send(AUTH, 2, 0, sign(k, m.data))
                        signed = true
                    } else {
                        if (!allowPrompt) return null
                        c.send(AUTH, 3, 0, publicKey(k))
                    }
                }
                prefs.saveAdbReady(true)
                val lid = 1
                c.send(OPEN, lid, 0, ("shell:$command\u0000").toByteArray())
                val sb = StringBuilder()
                val start = System.currentTimeMillis()
                var rid = 0
                while (true) {
                    val left = timeoutMs - (System.currentTimeMillis() - start)
                    if (left <= 0) break
                    val m = c.recv(left.toInt())
                    if (m.a1 != lid) continue
                    when (m.cmd) {
                        OKAY -> rid = m.a0
                        WRTE -> {
                            rid = m.a0
                            sb.append(String(m.data))
                            c.send(OKAY, lid, rid)
                        }
                        CLSE -> {
                            if (rid != 0) c.send(CLSE, lid, rid)
                            return sb.toString()
                        }
                    }
                }
                return sb.toString()
            } catch (e: Throwable) {
                return null
            } finally {
                c?.close()
            }
        }
    }
}

/** ADB ile açılıp kapatılan sistem parçaları. */
object SysToggles {
    const val CAST = "cast"
    const val ASSIST = "assist"
    const val GTV = "gtv"

    val GROUPS = mapOf(
        CAST to listOf(
            "com.google.android.apps.mediashell",
            "com.android.tv.mdnsoffloadmanager",
            "com.mediatek.android.tv.mdns.offload",
            "com.mediatek.dialservice",
            "com.mediatek.miracast"
        ),
        ASSIST to listOf("com.google.android.katniss"),
        GTV to listOf("com.google.android.apps.tv.launcherx")
    )

    private const val KATNISS = "com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService"

    class HomeResult(val ok: Boolean, val msg: String)

    /** Her grubun açık olup olmadığı. Bağlanamazsa null. */
    fun query(ctx: Context): Map<String, Boolean>? {
        val out = Adb.exec(ctx, "pm list packages -d; echo ===; pm list packages", allowPrompt = false) ?: return null
        val parts = out.split("===")
        if (parts.size < 2) return null
        val disabled = Regex("package:(\\S+)").findAll(parts[0]).map { it.groupValues[1] }.toSet()
        val installed = Regex("package:(\\S+)").findAll(parts[1]).map { it.groupValues[1] }.toSet()
        return GROUPS.mapValues { (_, pkgs) ->
            val present = pkgs.filter { it in installed }
            present.isNotEmpty() && present.any { it !in disabled }
        }
    }

    /** Sistemin Home tuşu şu an bu launcher'a mı yönleniyor? (ADB gerektirmez) */
    fun isDefaultHome(ctx: Context): Boolean {
        return try {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val ri = ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
            ri?.activityInfo?.packageName == ctx.packageName
        } catch (e: Throwable) {
            false
        }
    }

    private fun parseResolvedHome(out: String): String {
        val after = out.substringAfter("===HOME===", "")
        val lines = after.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("priority=") }
        return lines.lastOrNull() ?: ""
    }

    /**
     * Home tuşunu bu launcher'a bağlar.
     * Google TV (launcherx) açıkken Home'u bırakmıyorsa allowDisableGtv=true olduğunda
     * güvenli sırayla kapatıp doğrular; doğrulama başarısızsa anında geri açar (boot-loop önlemi).
     */
    fun repairHome(ctx: Context, allowPrompt: Boolean = true, allowDisableGtv: Boolean = true): HomeResult {
        val pkg = ctx.packageName
        val act = "$pkg/.MainActivity"

        val cmd1 = buildString {
            appendLine("cmd role add-role-holder --user 0 android.app.role.HOME $pkg 2>&1")
            appendLine("cmd package set-home-activity --user 0 $act 2>&1")
            appendLine("echo ===HOME===")
            append("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>&1")
        }
        val out1 = Adb.exec(ctx, cmd1, allowPrompt)
            ?: return HomeResult(false, "ADB bağlanamadı: Ağ hata ayıklama açık mı?")
        val res1 = parseResolvedHome(out1)
        if (res1.contains(pkg) || isDefaultHome(ctx)) {
            return HomeResult(true, "Home tuşu bu launcher'a bağlandı")
        }

        if (!allowDisableGtv) {
            return HomeResult(false, "Home şu an: ${res1.substringBefore("/")}")
        }
        return disableGtvSafely(ctx, allowPrompt)
    }

    /** Google TV ekranını güvenli biçimde kapatır; Home bize geçmezse anında geri açar. */
    fun disableGtvSafely(ctx: Context, allowPrompt: Boolean = true): HomeResult {
        val pkg = ctx.packageName
        val act = "$pkg/.MainActivity"
        val gtvPkg = GROUPS[GTV]!!.first()
        val cmd = buildString {
            appendLine("cmd role add-role-holder --user 0 android.app.role.HOME $pkg 2>&1")
            appendLine("pm disable-user --user 0 $gtvPkg 2>&1")
            appendLine("cmd role add-role-holder --user 0 android.app.role.HOME $pkg 2>&1")
            appendLine("cmd package set-home-activity --user 0 $act 2>&1")
            appendLine("echo ===HOME===")
            append("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>&1")
        }
        val out = Adb.exec(ctx, cmd, allowPrompt)
            ?: return HomeResult(false, "ADB bağlanamadı: Ağ hata ayıklama açık mı?")
        val res = parseResolvedHome(out)
        if (res.contains(pkg) || isDefaultHome(ctx)) {
            return HomeResult(true, "Home tuşu aktif (Google TV kapatıldı)")
        }
        val rollback = buildString {
            appendLine("pm enable --user 0 $gtvPkg 2>&1")
            append("pm enable $gtvPkg 2>&1")
        }
        Adb.exec(ctx, rollback, allowPrompt = false)
        val blocker = res.substringBefore("/").ifBlank { "bilinmiyor" }
        return HomeResult(false, "Güvenlik: Home doğrulanamadı ($blocker), Google TV geri açıldı")
    }

    fun set(ctx: Context, group: String, on: Boolean): HomeResult {
        val pkgs = GROUPS[group] ?: return HomeResult(false, "Bilinmeyen ayar")
        if (group == GTV) {
            val gtvPkg = pkgs.first()
            return if (on) {
                val pkg = ctx.packageName
                val act = "$pkg/.MainActivity"
                val cmd = buildString {
                    appendLine("pm enable --user 0 $gtvPkg 2>&1")
                    appendLine("pm enable $gtvPkg 2>&1")
                    appendLine("cmd role add-role-holder --user 0 android.app.role.HOME $pkg 2>&1")
                    append("cmd package set-home-activity --user 0 $act 2>&1")
                }
                Adb.exec(ctx, cmd) ?: return HomeResult(false, "ADB bağlanamadı: Ağ hata ayıklama açık mı?")
                HomeResult(true, "Google TV ekranı açıldı")
            } else {
                disableGtvSafely(ctx, allowPrompt = true)
            }
        }
        val sb = StringBuilder()
        for (p in pkgs) {
            sb.appendLine(if (on) "pm enable --user 0 $p 2>&1" else "pm disable-user --user 0 $p 2>&1")
        }
        if (group == ASSIST && on) {
            sb.appendLine("settings put secure assistant $KATNISS 2>&1")
            sb.appendLine("settings put secure voice_interaction_service $KATNISS 2>&1")
        }
        val res = Adb.exec(ctx, sb.toString())
            ?: return HomeResult(false, "ADB bağlanamadı: Ağ hata ayıklama açık mı?")
        return if (res.contains("Exception")) HomeResult(false, "İşlem reddedildi")
        else HomeResult(true, "")
    }

    /** Arka planda bekleyen uygulamaları kapatır, boşalan RAM'i (MB) döndürür. */
    fun boost(ctx: Context): Long? {
        val before = freeRamMb(ctx)
        Adb.exec(ctx, "am kill-all") ?: return null
        Thread.sleep(900)
        return (freeRamMb(ctx) - before).coerceAtLeast(0)
    }

    fun sleepNow(ctx: Context) = Adb.exec(ctx, "input keyevent 223") != null
    fun reboot(ctx: Context) = Adb.exec(ctx, "reboot") != null
}
