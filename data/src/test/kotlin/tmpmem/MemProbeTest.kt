package tmpmem

import com.tjshea.vigilant.data.reference.SgoParser
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File

class MemProbeTest {
    private fun used(): Long { repeat(4) { System.gc(); Thread.sleep(30) }; val r = Runtime.getRuntime(); return r.totalMemory() - r.freeMemory() }

    @Test
    fun probe() {
        val raw = File(System.getenv("SGO_RAW") ?: return).readText()
        val base = used()
        val tree = Json { isLenient = true }.parseToJsonElement(raw)
        val afterTree = used()
        val page = SgoParser.page(raw)
        val afterPage = used()
        println("MEMPROBE raw=${raw.length / 1024} KB  tree=${(afterTree - base) / 1024} KB  (tree+model)=${(afterPage - afterTree) / 1024} KB  events=${page.events.size} odds=${page.events.sumOf { it.odds.size }} lines=${page.events.sumOf { e -> e.odds.sumOf { o -> o.byBook.values.sumOf { it.size } } }}")
        check(tree.hashCode() != 0 || page.events.size >= 0)
    }
}
