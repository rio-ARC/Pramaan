package app.pramaan.server

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object EventLog {
    fun log(
        sessionId: String,
        type: String,
        actorId: String? = null,
        detail: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        transaction {
            SessionEventsTable.insert {
                it[SessionEventsTable.sessionId] = sessionId
                it[SessionEventsTable.type] = type
                it[SessionEventsTable.actorId] = actorId
                it[SessionEventsTable.at] = timestamp
                it[SessionEventsTable.detail] = detail
            }
        }
    }

    fun getEvents(sessionId: String): List<SessionEventRecord> = transaction {
        SessionEventsTable.selectAll()
            .where { SessionEventsTable.sessionId eq sessionId }
            .orderBy(SessionEventsTable.at)
            .map { row ->
                SessionEventRecord(
                    id = row[SessionEventsTable.id],
                    sessionId = row[SessionEventsTable.sessionId],
                    type = row[SessionEventsTable.type],
                    actorId = row[SessionEventsTable.actorId],
                    at = row[SessionEventsTable.at],
                    detail = row[SessionEventsTable.detail]
                )
            }
    }
}
