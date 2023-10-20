package no.nav.emottak.ebms.processing

import no.nav.emottak.Event
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TaskProcessor<T>(val taskName: String,
                       val korrelasjonsId: String,
                       val task: () -> T ): Processor() {
    var result: T? = null
    override fun process() {
        suspend{
            result = task.invoke()
        }
    }

    override fun korrelasjonsId(): String {
        return korrelasjonsId
    }

    override fun persisterHendelse(event: Event): Boolean {
        when (event.eventStatus) {
            Event.Status.STARTED -> log.info(taskName, "$event")
            Event.Status.OK -> log.info(taskName, "$event")
            Event.Status.FAILED -> log.error(taskName, "$event")
        }
        return true; // TODO publiser hendelse
    }
}

fun <T> runWithEvents(eventDescription: String, korrelasjonsId: String, task: () -> T): T {
        val log = LoggerFactory.getLogger(task::class.java.simpleName)
        val result: T?

        persisterHendelse(Event(eventDescription, Event.Status.STARTED, eventDescription, korrelasjonsId), log)
        try {
            result = task.invoke()
            persisterHendelse(Event(eventDescription, Event.Status.OK, eventDescription, korrelasjonsId), log)
        } catch (t: Throwable) {
            persisterHendelse(Event(eventDescription, Event.Status.FAILED, eventDescription, korrelasjonsId), log)
            throw t;
        }
        return result
}

data class Task<T>(val description: String, val task: () -> T )

fun <T> runTasks(korrelasjonsId: String, tasks: List<Task<T>>): List<T> {
    val results = ArrayList<T>()
    tasks.forEach { t -> results.add(
        runWithEvents(t.description, korrelasjonsId, t.task)
    ) }
    return results
}

fun persisterHendelse(event: Event, logger: Logger?): Boolean {
    when (event.eventStatus) {
        Event.Status.STARTED, Event.Status.OK -> logger?.info("$event")
        Event.Status.FAILED -> logger?.error("$event")
    }
    return true; // TODO publiser hendelse
}

class TaskFactory(val korrelasjonsId: String) {
    val tasks: ArrayList<Processor> = ArrayList();
    fun addTask(taskName: String, task: () -> Unit): TaskFactory {
        tasks.add(
            TaskProcessor(taskName, korrelasjonsId, task)
        )
        return this;
    }

    fun runAll() {
        tasks.forEach{t -> t.processWithEvents()}
    }
}