package no.nav.emottak.payload.helseid.util.lang

import java.sql.Timestamp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Objects
import javax.xml.datatype.DatatypeConfigurationException
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar

/**
 * Date utilities.
 */
@Suppress("TooManyFunctions")
object DateUtil {
    /**
     * Gets current date with timefields cleared (eg. midnight, when the date begins).
     * @return midnight of current date.
     */
    fun midnight(): Date = clearTimeFields(Date())

    /**
     * Gets a date with timefields cleared (eg. midnight, when the date begins).
     * @param date The date, if null then current date is used.
     * @return midnight of the given date.
     */
    fun midnight(date: Date?): Date =
        clearTimeFields(Objects.requireNonNullElseGet(date)  { Date() })

    /**
     * Checks if date is before today.
     * @param date The date to check.
     * @return True if date is before today, false otherwise or if date is null.
     */
    fun isBeforeToday(date: Date?): Boolean =
        date != null && isBeforeToday(LocalDateDeserializer.deserialize(date.time))

    /**
     * Checks if date is before today.
     * @param localDateTime The date to check.
     * @return True if date is before today, false otherwise or if date is null.
     */
    fun isBeforeToday(localDateTime: LocalDateTime?): Boolean =
        localDateTime != null && isBeforeToday(localDateTime.toLocalDate())

    /**
     * Checks if date is before today.
     * @param zonedDateTime The date to check.
     * @return True if date is before today, false otherwise or if date is null.
     */
    fun isBeforeToday(zonedDateTime: ZonedDateTime?): Boolean =
        zonedDateTime != null && isBeforeToday(zonedDateTime.toLocalDate())

    /**
     * Checks if date is before today.
     * @param localDate The date to check.
     * @return True if date is before today, false otherwise or if date is null.
     */
    fun isBeforeToday(localDate: LocalDate?): Boolean =
        localDate != null && localDate.isBefore(LocalDate.now())

    /**
     * Converts a [LocalDate] to a [Date]
     * @param localDate The date to convert. If null the the current [Date] is returned.
     * @return The [Date]
     */
    fun toDate(localDate: LocalDate?): Date =
        if (localDate == null) Date()
        else Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant())

    /**
     * Converts a [LocalDateTime] to a [Date]
     * @param localDateTime The date to convert. If null the the current [Date] is returned.
     * @return The [Date]
     */
    fun toDate(localDateTime: LocalDateTime?): Date =
        if (localDateTime == null) Date() else Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant())

    /**
     * Converts a [ZonedDateTime] to a [Date]
     * @param zonedDateTime The date to convert. If null the the current [Date] is returned.
     * @return The [Date]
     */
    fun toDate(zonedDateTime: ZonedDateTime?): Date =
        if (zonedDateTime == null) Date() else Date.from(zonedDateTime.toInstant())

    /**
     * Converts a [LocalDate] to a [Timestamp]
     * @param localDate The date to convert. If null the the current [Timestamp] is returned.
     * @return The [Timestamp] or null
     */
    fun toTimestamp(localDate: LocalDate?): Timestamp =
        if (localDate == null) Timestamp(midnight().time)
        else Timestamp.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant())

    /**
     * Converts a [LocalDateTime] to a [Timestamp]
     * @param localDateTime The date to convert. If null the the current [Timestamp] is returned.
     * @return The [Timestamp] or null
     */
    fun toTimestamp(localDateTime: LocalDateTime?): Timestamp =
        if (localDateTime == null) Timestamp.valueOf(LocalDateTime.now())
        else Timestamp.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant())

    /**
     * Converts a [ZonedDateTime] to a [Timestamp]
     * @param zonedDateTime The date to convert. If null the the current [Timestamp] is returned.
     * @return The [Timestamp] or null
     */
    fun toTimestamp(zonedDateTime: ZonedDateTime?): Timestamp? =
        if (zonedDateTime == null) Timestamp(System.currentTimeMillis()) else Timestamp.from(zonedDateTime.toInstant())

    /**
     * Converts a [LocalDate] to a [Date]
     * @param localDate The date to convert.
     * @return The [Date]
     */
    fun toNullableDate(localDate: LocalDate?): Date? =
        if (localDate == null) null
        else Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant())

    /**
     * Converts a [LocalDateTime] to a [Date]
     * @param localDateTime The date to convert.
     * @return The [Date]
     */
    fun toNullableDate(localDateTime: LocalDateTime?): Date? =
        if (localDateTime == null) null else Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant())

    /**
     * Converts a [ZonedDateTime] to a [Date]
     * @param zonedDateTime The date to convert.
     * @return The [Date]
     */
    fun toNullableDate(zonedDateTime: ZonedDateTime?): Date? =
        if (zonedDateTime == null) null else Date.from(zonedDateTime.toInstant())

    /**
     * Converts a [LocalDate] to a [Timestamp]
     * @param localDate The date to convert.
     * @return The [Timestamp] or null
     */
    fun toNullableTimestamp(localDate: LocalDate?): Timestamp? =
        if (localDate == null) null
        else Timestamp.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant())

    /**
     * Converts a [LocalDateTime] to a [Timestamp]
     * @param localDateTime The date to convert.
     * @return The [Timestamp] or null
     */
    fun toNullableTimestamp(localDateTime: LocalDateTime?): Timestamp? =
        if (localDateTime == null) null else Timestamp.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant())

    /**
     * Converts a [ZonedDateTime] to a [Timestamp]
     * @param zonedDateTime The date to convert.
     * @return The [Timestamp] or null
     */
    fun toNullableTimestamp(zonedDateTime: ZonedDateTime?): Timestamp? =
        if (zonedDateTime == null) null else Timestamp.from(zonedDateTime.toInstant())

    /**
     * Converts a date to an XMLGregorianCalendar.
     * @param date The date.
     * @return The XMLGregorianCalendar date.
     */
    fun toXMLGregorianCalendar(date: Date?): XMLGregorianCalendar {
        val c = GregorianCalendar()
        c.time = date
        return try {
            DatatypeFactory.newInstance().newXMLGregorianCalendar(c)
        } catch (e: DatatypeConfigurationException) {
            throw IllegalArgumentException(e)
        }
    }

    /**
     * Converts a date to an XMLGregorianCalendar.
     * @param date The date.
     * @return The XMLGregorianCalendar date.
     */
    fun toXMLGregorianCalendar(date: LocalDateTime?): XMLGregorianCalendar =
        toXMLGregorianCalendar(ZonedDateTime.of(date, ZoneId.systemDefault()))

    /**
     * Converts a date to an XMLGregorianCalendar.
     * @param date The date.
     * @return The XMLGregorianCalendar date.
     */
    fun toXMLGregorianCalendar(date: LocalDate?): XMLGregorianCalendar =
        toXMLGregorianCalendar(ZonedDateTime.of(LocalDateTime.of(date, LocalTime.MIN), ZoneId.systemDefault()))

    /**
     * Converts a date to an XMLGregorianCalendar.
     * @param date The date.
     * @return The XMLGregorianCalendar date.
     */
    fun toXMLGregorianCalendar(date: ZonedDateTime?): XMLGregorianCalendar {
        val c = GregorianCalendar.from(date)
        return try {
            DatatypeFactory.newInstance().newXMLGregorianCalendar(c)
        } catch (e: DatatypeConfigurationException) {
            throw IllegalArgumentException(e)
        }
    }

    /**
     * Gets the last day of the month of the date
     * @param date the date
     * @return the last day of the month
     */
    @Suppress("MagicNumber", "WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
    fun lastDayOfMonth(date: LocalDate): Int = when (date.month) {
        Month.JANUARY, Month.MARCH, Month.MAY, Month.JULY, Month.AUGUST, Month.OCTOBER, Month.DECEMBER -> 31
        Month.APRIL, Month.JUNE, Month.SEPTEMBER, Month.NOVEMBER -> 30
        Month.FEBRUARY -> if (date.isLeapYear) 29 else 28
    }

    /**
     * Adds at a number of working days to the given date. The result is always a working day.
     * @param date The date.
     * @param days Number of days to add.
     * @return The working date.
     */
    fun addWorkingDays(date: LocalDate?, days: Int): LocalDate =
        addWorkingDays(ZonedDateTime.of(date, LocalTime.MIN, ZoneId.systemDefault()), days).toLocalDate()



    /**
     * Adds at a number of working days to the given date. The result is always a working day.
     * @param date The date.
     * @param days Number of days to add.
     * @return The working date.
     */
    fun addWorkingDays(date: ZonedDateTime, days: Int): ZonedDateTime {
        var d = date
        var cnt = days
        if (days < 0) {
            while (cnt < 0) {
                d = d.minusDays(1)
                if (isWorkingDay(d)) {
                    cnt++
                }
            }
        } else {
            while (cnt > 0) {
                d = d.plusDays(1)
                if (isWorkingDay(d)) {
                    cnt--
                }
            }
        }
        return d
    }

    /**
     * Gets the next working day.
     * @param date The date.
     * @return The next working date.
     */
    fun nextWorkingDay(date: LocalDate?): LocalDate =
        nextWorkingDay(ZonedDateTime.of(date, LocalTime.MIN, ZoneId.systemDefault())).toLocalDate()

    /**
     * Gets the next working day.
     * @param date The date.
     * @return The next working date.
     */
    fun nextWorkingDay(date: ZonedDateTime): ZonedDateTime = addDays(date, 1, true)

    /**
     * Gets the previous working day.
     * @param date The date.
     * @return The previous working date.
     */
    fun previousWorkingDay(date: LocalDate?): LocalDate =
        previousWorkingDay(ZonedDateTime.of(date, LocalTime.MIN, ZoneId.systemDefault())).toLocalDate()

    /**
     * Gets the previous working day.
     * @param date The date.
     * @return The previous working date.
     */
    fun previousWorkingDay(date: ZonedDateTime): ZonedDateTime = addDays(date, -1, true)

    /**
     * Adds at a number of days to the given date. If workingDay is true then add more days so the result is
     * a (norwegian) working day.
     * @param date The date.
     * @param days Number of days to add.
     * @param workingDay The result should be a working day.
     * @return The working date.
     */
    fun addDays(date: LocalDate?, days: Int, workingDay: Boolean): LocalDate =
        addDays(ZonedDateTime.of(date, LocalTime.MIN, ZoneId.systemDefault()), days, workingDay).toLocalDate()

    /**
     * Adds at a number of days to the given date. If workingDay is true then add more days so the result is
     * a (norwegian) working day.
     * @param date The date.
     * @param days Number of days to add.
     * @param workingDay The result should be a working day.
     * @return The working date.
     */
    fun addDays(date: ZonedDateTime, days: Int, workingDay: Boolean): ZonedDateTime {
        var d = date.plusDays(days.toLong())
        if (workingDay) {
            val aday = if (days < 0) -1 else 1
            while (!isWorkingDay(d)) {
                d = d.plusDays(aday.toLong())
            }
        }
        return d
    }

    /**
     * Checks if the given day is a working day
     * @param date The date to check.
     * @param weekendDays which days are considered the weekend and does not count as working days
     * @return true if working day.
     */
    fun isWorkingDay(date: Date,
                     weekendDays: Collection<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)): Boolean =
        isWorkingDay(ZonedDateTimeDeserializer.deserialize(date.time), weekendDays)

    /**
     * Checks if the given day is a working day
     * @param date The date to check.
     * @param weekendDays which days are considered the weekend and does not count as working days
     * @return true if working day.
     */
    fun isWorkingDay(date: LocalDate?,
                     weekendDays: Collection<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)): Boolean =
        isWorkingDay(ZonedDateTime.of(date, LocalTime.MIN, ZoneId.systemDefault()), weekendDays)

    /**
     * Checks if the given day is a working day
     * @param date The date to check.
     * @param weekendDays which days are considered the weekend and does not count as working days
     * @return true if working day.
     */
    fun isWorkingDay(date: LocalDateTime?,
                     weekendDays: Collection<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)): Boolean =
        isWorkingDay(ZonedDateTime.of(date, ZoneId.systemDefault()), weekendDays)

    /**
     * Checks if the given day is a working day
     * @param date The date to check.
     * @param weekendDays which days are considered the weekend and does not count as working days
     * @return true if working day.
     */
    @Suppress("MagicNumber", "ReturnCount")
    fun isWorkingDay(date: ZonedDateTime,
                     weekendDays: Collection<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)): Boolean {
        // check if this a weekend
        val dayOfWeek = date.dayOfWeek
        if (weekendDays.contains(dayOfWeek)) {
            return false
        }

        // check new years day
        val dayOfYear = date.dayOfYear
        if (dayOfYear == 1) {
            return false
        }

        // check and international workers day and constitution day
        val month = date.month
        val dayOfMonth = date.dayOfMonth
        if (month == Month.MAY && (dayOfMonth == 1 || dayOfMonth == 17)) {
            return false
        }

        // christmas
        if (month == Month.DECEMBER && XMAS_DAYS.contains(dayOfMonth)) {
            return false
        }

        // easter
        val year = date.year
        val easterSunday = easterSunday(year)
        val easterSundayDayOfYear = easterSunday.dayOfYear
        val maundyThursdayDayOfYear = easterSundayDayOfYear - 3
        if (dayOfYear >= maundyThursdayDayOfYear && dayOfYear <= easterSundayDayOfYear + 1) {
            return false
        }

        // christian ascension
        return if (dayOfYear == easterSundayDayOfYear + 39) {
            false
        } else dayOfYear != easterSundayDayOfYear + 50

        // pentecost
    }

    /**
     * Algorithm for calculating the date of Easter Sunday (Meeus/Jones/Butcher Gregorian algorithm)
     * http://en.wikipedia.org/wiki/Computus#Meeus.2FJones.2FButcher_Gregorian_algorithm.
     * @param year The year to calculate for.
     * @return easter sunday
     */
    @Suppress("MagicNumber")
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val x = h + l - 7 * m + 114
        val month = x / 31
        val day = x % 31 + 1
        return LocalDate.of(year, month, day)
    }

    private fun clearTimeFields(d: Date?): Date {
        val c = Calendar.getInstance()
        c.time = d
        c[Calendar.HOUR_OF_DAY] = 0
        c[Calendar.MINUTE] = 0
        c[Calendar.SECOND] = 0
        c[Calendar.MILLISECOND] = 0
        return c.time
    }

    @Suppress("MagicNumber")
    private val XMAS_DAYS = IntRange(24, 26)
}
