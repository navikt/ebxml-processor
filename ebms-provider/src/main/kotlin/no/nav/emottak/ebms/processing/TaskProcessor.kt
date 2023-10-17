package no.nav.emottak.ebms.processing

import no.nav.emottak.Event

class TaskProcessor(val task: suspend () -> Unit, val korrelasjonsId: String): Processor() {
    override fun process() {
        suspend {
            task.invoke()
        }
    }

    override fun korrelasjonsId(): String {
        return korrelasjonsId
        //TODO("Not yet implemented")
    }

    override fun persisterHendelse(event: Event): Boolean {
        return true;
        //TODO("Not yet implemented")
    }
}