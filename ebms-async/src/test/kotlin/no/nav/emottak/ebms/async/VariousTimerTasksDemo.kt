package no.nav.emottak.ebms.async

import arrow.resilience.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Timer
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timer
import kotlin.time.Duration.Companion.seconds

// Velg hvilke timer-typer som skal skeduleres (alle kan brukes i parallell)
val inkluderJavaTimer = true
val inkluderJavaExecutor = true
val inkluderArrowRepeat = true

// Velg om den skedulerte rutina av og til skal kaste exceptions, og om den skal beskyttes med try/catch
val throwExceptionsAfter10seconds = true
val useTryCatch = true

//
var start = 0L

fun main() {
    // Sett opp timere
    val timerWithKotlinExtensions = if (inkluderJavaTimer) scheduleJavaTimerWithKotlinExtensions() else null
    val scheduledThreadPoolExecutor = if (inkluderJavaExecutor) scheduleJavaScheduledThreadPoolExecutor() else null
    // Arrow Schedule skedulerer ikke en task, men setter igang en evig while/repeat.
    // Må derfor gjøres i egen coroutine.
    if (inkluderArrowRepeat) GlobalScope.launch(Dispatchers.IO) { scheduleWithArrowRepeat() }

    // Kjør main-loop i parallell med timer-rutiner, med jevnlig utskrift, avsluttes etter en gitt periode
    val printLoopDelay = 10
    val maxLoops = 3
    println("Main() vil gå i ca ${printLoopDelay * maxLoops} sekunder")

    var loops = 0
    start = System.currentTimeMillis()
    runBlocking {
        while (loops < maxLoops) {
            loops++
            delay(printLoopDelay.seconds)
            println("Main() har gått i ${printLoopDelay * loops} sekunder")
        }

        // Stopp timere når main-loop avsluttes
        println("Main() avslutter")
        scheduledThreadPoolExecutor?.shutdown()
        // Denne er ikke nødvendig når timeren startes med daemon=true
        timerWithKotlinExtensions?.cancel()
    }
}

// java.util.Timer med Kotlin extensions.
// Delay/period er i millisekunder.
// Daemon = true betyr at tråden vil stoppes når applikasjonen går ned.
fun scheduleJavaTimerWithKotlinExtensions(): Timer {
    println("Skedulerer JavaTimerWithKotlinExtensions, hvert sekund etter 3 sekunder")
    return timer(
        name = "javaTimerWithKotlinExtensions",
        initialDelay = 3000L,
        period = 1000L,
        daemon = true
    ) {
        // Hvis denne feiler, vil timer-tråden stoppe med exception, mens Main-tråden fortsetter
        runTheStuff("JavaTimerWithKotlinExtensions rutine kjørt")
    }
}

// java.util.concurrent.ScheduledThreadPoolExecutor, Thread pool med 1 tråd.
// Delay/period er i angitt enhet.
fun scheduleJavaScheduledThreadPoolExecutor(): ScheduledThreadPoolExecutor {
    println("Skedulerer JavaScheduledThreadPoolExecutor, hvert 4. sekund etter 5 sekunder")
    val executor = ScheduledThreadPoolExecutor(1)
    // Hvis Runnable'n feiler, vil timeren stoppe (ikke flere kjøringer), mens Main-tråden fortsetter
    executor.scheduleAtFixedRate(
        getRunnable("JavaScheduledThreadPoolExecutor rutine kjørt"),
        5L,
        4,
        TimeUnit.SECONDS
    )
    return executor
}

// arrow.resilience.Schedule, repeat jevnlig for alltid, så lenge det ikke oppstår feil
// Kan ikke sette opp delay, duration i angitt enhet.
suspend fun scheduleWithArrowRepeat() {
    println("Starter ArrowRetry, hvert 3. sekund")
    Schedule
        .spaced<Unit>(3.seconds)
        .repeat {
            // Hvis denne feiler, vil timeren stoppe med exception, mens Main-tråden fortsetter
            runTheStuff("ArrowRetry rutine kjørt")
        }
}

fun runTheStuff(message: String) {
    if (useTryCatch) {
        try {
            doActualStuffPossiblyThrowingException(message)
        } catch (e: Exception) {
            println("Håndterer exception i rutine: ${e.message}")
        }
    } else {
        doActualStuffPossiblyThrowingException(message)
    }
}

fun getRunnable(message: String): Runnable {
    return Runnable() {
        runTheStuff(message)
    }
}

fun doActualStuffPossiblyThrowingException(message: String) {
    val time = System.currentTimeMillis() - start
    println("Millis: $time, $message")
    if (throwExceptionsAfter10seconds && time > 10000) throw RuntimeException("Exception i rutine")
}
