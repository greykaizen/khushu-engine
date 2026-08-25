import com.github.msarhan.ummalqura.calendar.UmmalquraCalendar
import java.time.LocalDate
import java.util.Calendar

// Golden-master dumper for the calendar module: replicates Osprey's hijri
// conversion call (UmmalquraCalendar.setTimeInMillis + Y/M/D reads), including
// the donor's civil-day-shift offset application.

data class Row(val date: String, val offset: Int, val hy: Int, val hm: Int, val hd: Int)

fun main(args: Array<String>) {
    val rows = mutableListOf<Row>()
    val offsets = intArrayOf(-2, -1, 0, 1, 2)
    // Sampled years: two full years plus Ramadan/Eid windows of neighbours.
    val dates = mutableListOf<LocalDate>()
    for (year in intArrayOf(2024, 2025)) {
        var d = LocalDate.of(year, 1, 1)
        while (!d.isAfter(LocalDate.of(year, 12, 31))) {
            dates += d
            d = d.plusDays(1)
        }
    }

    for (date in dates) {
        for (offset in offsets) {
            val cal = UmmalquraCalendar()
            cal.timeInMillis = date.toEpochDay() * 86_400_000L + offset * 86_400_000L
            rows += Row(
                date = date.toString(),
                offset = offset,
                hy = cal.get(Calendar.YEAR),
                hm = cal.get(Calendar.MONTH) + 1,
                hd = cal.get(Calendar.DAY_OF_MONTH),
            )
        }
    }

    val json = buildString {
        append("{\n  \"meta\": {\"generator\": \"golden-dumper-calendar\", \"lib\": \"com.github.msarhan:ummalqura-calendar:2.0.2\",\n")
        append("    \"donorPath\": \"Osprey core/common/IslamicEventCalculator.kt\"},\n")
        append("  \"cases\": [\n")
        append(rows.joinToString(",\n") {
            "    {\"date\":\"${it.date}\",\"offset\":${it.offset},\"hy\":${it.hy},\"hm\":${it.hm},\"hd\":${it.hd}}"
        })
        append("\n  ]\n}\n")
    }
    val out = java.io.File(args.getOrElse(0) { "hijri_golden.json" })
    out.parentFile?.mkdirs()
    out.writeText(json)
    println("wrote ${rows.size} cases to ${out.absolutePath}")
}
