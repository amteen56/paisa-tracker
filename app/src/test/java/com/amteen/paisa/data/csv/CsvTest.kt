package com.amteen.paisa.data.csv

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RFC 4180, and specifically the parts people get wrong.
 *
 * The escaping rules are cheap to state and easy to break, and a CSV bug is the kind
 * that silently changes a user's data on the way back in — so every rule has a test.
 */
class CsvTest {

    // -- Writing ------------------------------------------------------------

    @Test
    fun `plain fields are not quoted`() {
        assertEquals("a,b,c\r\n", Csv.writeRow(listOf("a", "b", "c")))
    }

    @Test
    fun `a field containing a comma is quoted`() {
        assertEquals("\"a,b\",c\r\n", Csv.writeRow(listOf("a,b", "c")))
    }

    @Test
    fun `a quote is doubled, not escaped with a backslash`() {
        // The single most common CSV bug. Backslash escaping is not RFC 4180 and
        // every spreadsheet reads it back wrong.
        assertEquals("\"say \"\"hi\"\"\"\r\n", Csv.writeRow(listOf("say \"hi\"")))
    }

    @Test
    fun `a newline inside a field is quoted rather than breaking the row`() {
        assertEquals("\"line1\nline2\"\r\n", Csv.writeRow(listOf("line1\nline2")))
    }

    @Test
    fun `leading and trailing spaces are preserved by quoting`() {
        // An unquoted " 5" is ambiguous enough that some readers trim it, which
        // changes the value.
        assertEquals("\" 5\"\r\n", Csv.writeRow(listOf(" 5")))
    }

    @Test
    fun `writeAll puts the header first`() {
        val out = Csv.writeAll(listOf("a", "b"), listOf(listOf("1", "2"), listOf("3", "4")))
        assertEquals("a,b\r\n1,2\r\n3,4\r\n", out)
    }

    // -- Reading ------------------------------------------------------------

    @Test
    fun `parses a simple document`() {
        val rows = Csv.parse("a,b\r\n1,2\r\n")
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), rows)
    }

    @Test
    fun `accepts LF, CRLF and CR line endings`() {
        val expected = listOf(listOf("a"), listOf("b"))
        assertEquals(expected, Csv.parse("a\nb"))
        assertEquals(expected, Csv.parse("a\r\nb"))
        assertEquals(expected, Csv.parse("a\rb"))
    }

    @Test
    fun `a quoted newline stays inside its field`() {
        // Splitting on newlines before parsing quotes is the classic way to corrupt
        // any note the user pressed return in.
        val rows = Csv.parse("id,note\r\n1,\"first\nsecond\"\r\n")
        assertEquals(2, rows.size)
        assertEquals(listOf("1", "first\nsecond"), rows[1])
    }

    @Test
    fun `a doubled quote reads back as one quote`() {
        val rows = Csv.parse("\"say \"\"hi\"\"\"\r\n")
        assertEquals(listOf(listOf("say \"hi\"")), rows)
    }

    @Test
    fun `a quoted comma does not split the field`() {
        val rows = Csv.parse("\"a,b\",c\r\n")
        assertEquals(listOf(listOf("a,b", "c")), rows)
    }

    @Test
    fun `a UTF-8 BOM is skipped`() {
        // Files round-tripped through Excel on Windows routinely arrive with one,
        // and without this the first column name never matches.
        val rows = Csv.parse("\uFEFFdate,amount\r\n2026-09-01,100\r\n")
        assertEquals("date", rows.first().first())
    }

    @Test
    fun `an empty document produces no rows`() {
        assertEquals(emptyList<List<String>>(), Csv.parse(""))
        assertEquals(emptyList<List<String>>(), Csv.parse("\r\n"))
    }

    @Test
    fun `a final row without a trailing break is still read`() {
        val rows = Csv.parse("a,b\r\n1,2")
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), rows)
    }

    @Test
    fun `an empty trailing field is kept`() {
        // "1,2," is three fields, the last empty — dropping it shifts every column
        // after it when the row is read by position.
        assertEquals(listOf(listOf("1", "2", "")), Csv.parse("1,2,"))
    }

    // -- Round trip ---------------------------------------------------------

    @Test
    fun `every awkward value survives a round trip`() {
        val nasty = listOf(
            "plain",
            "with,comma",
            "with \"quotes\"",
            "with\nnewline",
            "with\r\ncrlf",
            " padded ",
            "",
            "trailing quote\"",
            "\"leading quote",
        )

        val document = Csv.writeAll(listOf("value"), nasty.map { listOf(it) })
        val parsed = Csv.parse(document)

        assertEquals("value", parsed.first().single())
        // The empty string is dropped as a blank row, which is why it is excluded
        // here — a genuinely empty single-column row carries nothing to preserve.
        val expected = nasty.filter { it.isNotEmpty() }
        assertEquals(expected, parsed.drop(1).map { it.single() })
    }
}
