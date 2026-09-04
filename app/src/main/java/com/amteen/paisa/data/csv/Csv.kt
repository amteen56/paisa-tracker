package com.amteen.paisa.data.csv

/**
 * RFC 4180 CSV, written by hand.
 *
 * A CSV library would be a dependency for two functions, and the dependency list here
 * is deliberately tiny — see CLAUDE.md. The two rules that actually matter are the
 * ones people get wrong: a field containing a comma, a quote or a newline must be
 * quoted, and a literal quote inside a quoted field is **doubled**, not backslashed.
 */
object Csv {

    /** Excel and Sheets both need CRLF to treat a quoted newline as in-cell. */
    const val LINE_BREAK = "\r\n"

    fun writeRow(values: List<String>): String =
        values.joinToString(",") { escape(it) } + LINE_BREAK

    fun writeAll(header: List<String>, rows: List<List<String>>): String = buildString {
        append(writeRow(header))
        for (row in rows) append(writeRow(row))
    }

    /**
     * Quotes only when a field needs it, so an ordinary export stays readable in a
     * text editor.
     *
     * A leading or trailing space is preserved by quoting too: an unquoted `" 5"` is
     * ambiguous enough that some readers trim it and change the value.
     */
    fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            value.startsWith(" ") || value.endsWith(" ")
        if (!needsQuotes) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    /**
     * Parses a whole document into rows.
     *
     * Written as a single character scan rather than `split` per line, because a
     * quoted field may itself contain the line separator — splitting on newlines
     * first is the classic way to corrupt any note the user typed a return into.
     *
     * Tolerates LF, CRLF and CR line endings, and skips a UTF-8 BOM, because the file
     * may well have been round-tripped through a spreadsheet on another platform.
     */
    fun parse(text: String): List<List<String>> {
        val input = text.removePrefix("﻿")
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0
        // Distinguishes a genuinely empty document from a trailing line break.
        var sawAnyChar = false

        while (index < input.length) {
            val ch = input[index]

            if (inQuotes) {
                when {
                    ch != '"' -> field.append(ch)
                    // A doubled quote is one literal quote, and stays in the field.
                    index + 1 < input.length && input[index + 1] == '"' -> {
                        field.append('"')
                        index++
                    }
                    else -> inQuotes = false
                }
                index++
                continue
            }

            when (ch) {
                '"' -> {
                    inQuotes = true
                    sawAnyChar = true
                }
                ',' -> {
                    row.add(field.toString())
                    field.setLength(0)
                    sawAnyChar = true
                }
                '\r', '\n' -> {
                    // Consume CRLF as one break rather than emitting a blank row.
                    if (ch == '\r' && index + 1 < input.length && input[index + 1] == '\n') index++
                    row.add(field.toString())
                    field.setLength(0)
                    if (row.any { it.isNotEmpty() }) rows.add(row)
                    row = ArrayList()
                    sawAnyChar = false
                }
                else -> {
                    field.append(ch)
                    sawAnyChar = true
                }
            }
            index++
        }

        // The last row usually has no trailing break.
        if (field.isNotEmpty() || row.isNotEmpty() || sawAnyChar) {
            row.add(field.toString())
            if (row.any { it.isNotEmpty() }) rows.add(row)
        }

        return rows
    }
}
