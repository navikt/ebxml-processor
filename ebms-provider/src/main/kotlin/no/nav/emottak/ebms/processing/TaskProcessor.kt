package no.nav.emottak.ebms.processing

import no.nav.emottak.Event
import no.nav.emottak.util.marker
import kotlin.reflect.jvm.reflect

class TaskProcessor(val taskName: String,
                    val korrelasjonsId: String,
                    val task: () -> Unit): Processor() {
    override fun process() {
            task.invoke()
    }

    override fun korrelasjonsId(): String {
        return korrelasjonsId
        //TODO("Not yet implemented")
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

class TaskFactory(val korrelasjonsId: String) {
    val tasks: ArrayList<Processor> = ArrayList();
    fun addTask(taskName: String, task: () -> Unit): TaskFactory {
        tasks.add(
            TaskProcessor(taskName, korrelasjonsId, task)
        )
        return this;
    }
}