package no.nav.emottak.cpa.persistence

import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test

class CPARepositoryTest : DBTest() {


    @Test
    fun testDatabase() {
        transaction {
            CPA.selectAll().first()
        }
    }
}