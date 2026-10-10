package com.tjshea.vigilant.app

import java.lang.reflect.Modifier

/**
 * Who holds the app's heap (Tj, 2026-10-10: "stopped reading early because the app's memory was nearly full (heap 467 of 512 MB)"). Android gives the app a fixed heap (largeHeap is already on
 * and 512 MB is this phone's ceiling), and a profiler cannot be attached to Tj's phone, so the app measures itself: a reflective walk over the object graph from named roots (every field of the
 * app container, every field of the UI state), adding up an approximate size per owner. Approximate on purpose (sizes are the ART layout's rule of thumb; an object two owners share is counted
 * under the first one walked; a visited-set built from identity hashes may miss a few percent), and bounded: [maxObjects] objects and [budgetMs] milliseconds, whichever comes first, using about
 * 8 MB of its own. It reads only this app's own classes, Kotlin's and the collections, never the framework's (Android restricts reflection into it and its objects hold the whole screen).
 */
object HeapCensus {

    /** One owner's share: its [bytes] (approximate) in [objects] objects. */
    data class Row(val owner: String, val bytes: Long, val objects: Int)

    class Result(val rows: List<Row>, val totalBytes: Long, val objects: Int, val truncated: Boolean, val ms: Long) {
        /** "heap census 142 MB in 1.2 M objects (4.1 s): ui.result 38 MB, runner 21 MB, …" */
        fun line(top: Int = 12): String =
            "Heap census (approximate: ${totalBytes / MB} MB in ${objects / 1000} k objects walked in $ms ms${if (truncated) ", STOPPED at its limit, so the true total is larger" else ""}): " +
                rows.take(top).joinToString(" · ") { "${it.owner} ${if (it.bytes >= MB) "${it.bytes / MB} MB" else "${it.bytes / 1024} KB"} (${it.objects / 1000} k)" }
    }

    const val MB = 1024L * 1024L
    const val MAX_OBJECTS = 1_500_000
    const val BUDGET_MS = 6_000L
    private const val SEEN_LONGS = 1 shl 20          // 2^26 bits: 8 MB

    /** Walks [roots] in order; returns each owner's share, largest first. */
    fun run(roots: List<Pair<String, Any?>>, maxObjects: Int = MAX_OBJECTS, budgetMs: Long = BUDGET_MS, clock: () -> Long = System::nanoTime): Result {
        val started = clock()
        val seen = LongArray(SEEN_LONGS)
        val mask = SEEN_LONGS * 64 - 1
        fun firstVisit(o: Any): Boolean {
            val h = System.identityHashCode(o) and mask
            val w = h ushr 6
            val bit = 1L shl (h and 63)
            if (seen[w] and bit != 0L) return false
            seen[w] = seen[w] or bit
            return true
        }
        val rows = ArrayList<Row>()
        var total = 0L
        var objects = 0
        var truncated = false
        val stack = ArrayList<Any>()
        outer@ for ((owner, root) in roots) {
            if (root == null) continue
            var bytes = 0L
            var n = 0
            stack.clear()
            stack += root
            while (stack.isNotEmpty()) {
                if (objects >= maxObjects || (n and 0xFFF == 0 && (clock() - started) / 1_000_000L > budgetMs)) { truncated = true; if (n > 0) rows += Row(owner, bytes, n); total += bytes; break@outer }
                val o = stack.removeAt(stack.size - 1)
                if (!firstVisit(o)) continue
                n++; objects++
                bytes += Sizer.visit(o, stack)
            }
            rows += Row(owner, bytes, n)
            total += bytes
        }
        return Result(rows.filter { it.bytes > 0 }.sortedByDescending { it.bytes }, total, objects, truncated, (clock() - started) / 1_000_000L)
    }

    /** Every field of [target] as a root named after it (Kotlin's `x$delegate` for a `by lazy` is named `x`). */
    fun rootsOf(prefix: String, target: Any): List<Pair<String, Any?>> =
        generateSequence<Class<*>>(target.javaClass) { it.superclass?.takeIf { s -> s != Any::class.java } }.flatMap { c -> c.declaredFields.asSequence() }
            .filter { !Modifier.isStatic(it.modifiers) && !it.type.isPrimitive }
            .mapNotNull { f -> runCatching { f.isAccessible = true; "$prefix${f.name.removeSuffix("\$delegate")}" to f.get(target) }.getOrNull() }
            .toList()

    /** Approximate shallow sizes (ART: 8-byte header, 4-byte references) and the children to walk. */
    private object Sizer {
        private fun align(n: Long) = (n + 7) and 7L.inv()

        /** Classes whose fields are walked: this app's, Kotlin's, and the few kotlinx ones that hold app state. */
        private fun walkable(c: Class<*>): Boolean {
            val n = c.name
            return n.startsWith("com.tjshea.") || (n.startsWith("kotlin.") && !n.startsWith("kotlin.coroutines.")) ||
                n.startsWith("kotlinx.coroutines.flow.StateFlowImpl") || n.startsWith("kotlinx.coroutines.flow.SharedFlowImpl") ||
                n.startsWith("kotlinx.serialization.json.Json") || n.startsWith("kotlinx.atomicfu")
        }

        fun visit(o: Any, out: MutableList<Any>): Long {
            val c = o.javaClass
            when {
                o is String -> return align(24L + o.length)
                o is Number || o is Boolean || o is Char -> return 16L
                o is Enum<*> || o is Class<*> || o is Thread || o is ClassLoader -> return 0L
                c.isArray -> return array(o, out)
                o is Map<*, *> -> return map(o, out)
                o is Collection<*> -> return collection(o, out)
                o is java.util.concurrent.atomic.AtomicReference<*> -> { o.get()?.let { out += it }; return 16L }
                o is java.util.concurrent.atomic.AtomicLong || o is java.util.concurrent.atomic.AtomicInteger || o is java.util.concurrent.atomic.AtomicBoolean -> return 16L
                walkable(c) -> return fields(o, c, out)
                else -> return 16L   // a framework or library object: counted shallow, its fields not walked
            }
        }

        private fun fields(o: Any, c: Class<*>, out: MutableList<Any>): Long {
            var size = 8L
            var k: Class<*>? = c
            while (k != null && k != Any::class.java) {
                for (f in k.declaredFields) {
                    if (Modifier.isStatic(f.modifiers)) continue
                    val t = f.type
                    size += when (t) {
                        java.lang.Long.TYPE, java.lang.Double.TYPE -> 8
                        java.lang.Integer.TYPE, java.lang.Float.TYPE -> 4
                        java.lang.Short.TYPE, java.lang.Character.TYPE -> 2
                        java.lang.Boolean.TYPE, java.lang.Byte.TYPE -> 1
                        else -> 4
                    }
                    if (!t.isPrimitive) runCatching { f.isAccessible = true; f.get(o) }.getOrNull()?.let { out += it }
                }
                k = k.superclass
            }
            return align(size)
        }

        private fun array(o: Any, out: MutableList<Any>): Long = when (o) {
            is ByteArray -> align(16L + o.size)
            is CharArray -> align(16L + 2L * o.size)
            is ShortArray -> align(16L + 2L * o.size)
            is IntArray -> align(16L + 4L * o.size)
            is FloatArray -> align(16L + 4L * o.size)
            is LongArray -> align(16L + 8L * o.size)
            is DoubleArray -> align(16L + 8L * o.size)
            is BooleanArray -> align(16L + o.size)
            else -> { val a = o as Array<*>; a.forEach { e -> if (e != null) out += e }; align(16L + 4L * a.size) }
        }

        private fun map(m: Map<*, *>, out: MutableList<Any>): Long = try {
            val entries = m.entries.toList()
            for (e in entries) { e.key?.let { out += it }; e.value?.let { out += it } }
            align(48L + 4L * entries.size * 2 + 40L * entries.size)
        } catch (e: RuntimeException) { 48L }   // a map changing under the walk: counted shallow

        private fun collection(c: Collection<*>, out: MutableList<Any>): Long = try {
            val items = c.toList()
            for (e in items) if (e != null) out += e
            align(24L + 4L * items.size + (if (c is Set<*>) 36L * items.size else 0L))
        } catch (e: RuntimeException) { 24L }
    }
}
