package com.tjshea.vigilant.data.novig.trading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a reply looked like, with none of what it said (v0.70.4: the unreadable batch reply of the v0.70.1 diagnostics). */
class ReplyShapeTest {

    @Test
    fun `objects give sorted keys with the kind of each value, arrays the first item's shape and the count`() {
        assertEquals("{accepted:[{clientId:string,orderId:string}x2],ok:bool,n:number,gone:null}".let { "{accepted:[{clientId:string,orderId:string}x2],gone:null,n:number,ok:bool}" },
            ReplyShape.of("""{"ok":true,"n":1.5,"gone":null,"accepted":[{"orderId":"a","clientId":"b"},{"orderId":"c"}]}"""))
        assertEquals("[]", ReplyShape.of("[]"))
        assertEquals("{}", ReplyShape.of("{}"))
        assertEquals("number", ReplyShape.of("42"))
        assertEquals("[string x3]".replace(" ", ""), ReplyShape.of("""["a","b","c"]"""))
    }

    @Test
    fun `no value ever appears, and a key that is an id is written as one`() {
        val shape = ReplyShape.of("""{"4f6c1c1a-1b2c-4d3e-8f9a-0123456789ab":{"price":"0.485","secretToken":"SECRET"},"short":1}""")
        assertEquals("{<id>:{price:string,secretToken:string},short:number}", shape)
        assertFalse(shape.contains("0.485"))
        assertFalse(shape.contains("SECRET"))
        assertEquals("a key over 24 characters is an id too", "{<id>:string}", ReplyShape.of("""{"abcdefghijklmnopqrstuvwxyz0123":"v"}"""))
    }

    @Test
    fun `an empty body, a page and broken JSON are described by their size and first character, never quoted`() {
        assertEquals("an empty body (0 characters)", ReplyShape.of(""))
        assertEquals("an empty body (3 characters)", ReplyShape.of("   "))
        assertEquals("not JSON (13 characters, starting with '<' (HTML))", ReplyShape.of("<html>x</html"))
        assertEquals("not JSON (5 characters, starting with a letter)", ReplyShape.of("oops!"))
        assertEquals("not JSON (4 characters, starting with a digit)", ReplyShape.of("12 ]"))
    }

    @Test
    fun `depth and length are capped so a huge reply can't fill a diagnostics line`() {
        assertEquals("{a:{b:{c:{d:{…}}}}}", ReplyShape.of("""{"a":{"b":{"c":{"d":{"e":{"f":1}}}}}}"""))
        val wide = ReplyShape.of((1..200).joinToString(",", "{", "}") { "\"k$it\":1" })
        assertTrue(wide.length <= 301)
        assertTrue(wide.endsWith("…"))
    }
}
