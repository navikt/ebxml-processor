package no.nav.emottak.ebms.async.util

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.time.Duration

class DurationUtilSpec : DescribeSpec({

    describe("Tests of durationUntil") {
        val now = LocalDateTime.parse("2026-08-15T10:00:00")

        it("should return Duration of 2 hours when 'now' is 10:00 and LocalTime is 12:00") {
            val time = LocalTime.parse("12:00")
            val duration = time.durationUntil(now)
            duration.inWholeHours shouldBe 2
        }

        it("should return Duration of 90 minutes when 'now' is 10:00 and LocalTime is 11:30") {
            val time = LocalTime.parse("11:30")
            val duration = time.durationUntil(now)
            duration.inWholeMinutes shouldBe 90
        }

        it("should return Duration of 10 hours when 'now' is 10:00 and LocalTime is 20:00") {
            val time = LocalTime.parse("20:00")
            val duration = time.durationUntil(now)
            duration.inWholeHours shouldBe 10
        }

        it("should return Duration of 23 hours when 'now' is 10:00 and LocalTime is 09:00") {
            val time = LocalTime.parse("09:00")
            val duration = time.durationUntil(now)
            duration.inWholeHours shouldBe 23
        }
    }

    describe("Tests of readableInterval") {

        it("should return '1 days' when Duration is '1d'") {
            val interval = Duration.parse("1d")
            interval.readableInterval() shouldBe "1 days"
        }

        it("should return '2 days' when Duration is '2d'") {
            val interval = Duration.parse("2d")
            interval.readableInterval() shouldBe "2 days"
        }

        it("should return '12 hours' when Duration is '12h'") {
            val interval = Duration.parse("12h")
            interval.readableInterval() shouldBe "12 hours"
        }

        it("should return '1 days, 12 hours' when Duration is '1d 12h'") {
            val interval = Duration.parse("1d 12h")
            interval.readableInterval() shouldBe "1 days, 12 hours"
        }

        it("should return '2 hours, 30 minutes' when Duration is '2h 30m'") {
            val interval = Duration.parse("2h 30m")
            interval.readableInterval() shouldBe "2 hours, 30 minutes"
        }

        it("should return '45 minutes' when Duration is '45m'") {
            val interval = Duration.parse("45m")
            interval.readableInterval() shouldBe "45 minutes"
        }

        it("should return '3 days, 15 hours, 20 minutes' when Duration is '3d 15h 20m'") {
            val interval = Duration.parse("3d 15h 20m")
            interval.readableInterval() shouldBe "3 days, 15 hours, 20 minutes"
        }

        it("should return '1 days, 20 minutes' when Duration is '1d 20m'") {
            val interval = Duration.parse("1d 20m")
            interval.readableInterval() shouldBe "1 days, 20 minutes"
        }

        it("should return '1 days' when Duration is '24h'") {
            val interval = Duration.parse("24h")
            interval.readableInterval() shouldBe "1 days"
        }

        it("should return '2 days' when Duration is '48h'") {
            val interval = Duration.parse("48h")
            interval.readableInterval() shouldBe "2 days"
        }

        it("should return '2 days, 2 hours' when Duration is '50h'") {
            val interval = Duration.parse("50h")
            interval.readableInterval() shouldBe "2 days, 2 hours"
        }

        it("should return '1 hours, 30 minutes' when Duration is '90m'") {
            val interval = Duration.parse("90m")
            interval.readableInterval() shouldBe "1 hours, 30 minutes"
        }

        it("should return seconds when less than one minute") {
            val interval = Duration.parse("30s")
            interval.readableInterval() shouldBe "30 seconds"
        }

        it("should not return seconds when more than one minute") {
            val interval = Duration.parse("90s")
            interval.readableInterval() shouldBe "1 minutes"
        }
    }
})
