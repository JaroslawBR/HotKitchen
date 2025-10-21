package hotkitchen.plugins.routes

import hotkitchen.Profiles
import hotkitchen.Users
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

fun Route.profileRoutes() = authenticate("auth-jwt") {
    route("/me") {
        get {
            val p = call.principal<JWTPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val emailFromToken = p.payload.getClaim("email").asString()

            val dto = transaction {
                val u = Users.select(Users.id, Users.email, Users.userType).where { Users.email eq emailFromToken }.firstOrNull()
                    ?: return@transaction null
                val prof = Profiles
                    .select(Profiles.userId, Profiles.name, Profiles.phone, Profiles.address)
                    .where { Profiles.userId eq u[Users.id] }
                    .firstOrNull() ?: return@transaction null

                ProfileDTO(
                    name = prof[Profiles.name],
                    userType = u[Users.userType],
                    phone = prof[Profiles.phone],
                    email = u[Users.email],
                    address = prof[Profiles.address]
                )
            }

            if (dto == null) call.respond(HttpStatusCode.BadRequest) else call.respond(HttpStatusCode.OK, dto)
        }

        put {
            val p = call.principal<JWTPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
            val emailFromToken = p.payload.getClaim("email").asString()
            val body = runCatching { call.receive<ProfileUserRequest>() }
                .getOrElse { return@put call.respond(HttpStatusCode.BadRequest) }

            if (emailFromToken != body.email) return@put call.respond(HttpStatusCode.BadRequest)

            val updated = transaction {
                val u = Users.select(Users.id, Users.email, Users.userType).where { Users.email eq emailFromToken }.firstOrNull()
                    ?: return@transaction null
                val uid = u[Users.id]

                Users.update({ Users.id eq uid }) { it[userType] = body.userType }

                val exists = Profiles.select(Profiles.userId).where { Profiles.userId eq uid }.empty().not()
                if (exists) {
                    Profiles.update({ Profiles.userId eq uid }) {
                        it[name] = body.name
                        it[phone] = body.phone
                        it[address] = body.address
                    }
                } else {
                    Profiles.insert {
                        it[userId] = uid
                        it[name] = body.name
                        it[phone] = body.phone
                        it[address] = body.address
                        it[email] = body.email
                    }
                }

                ProfileDTO(body.name, body.userType, body.phone, body.email, body.address)
            }

            if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(HttpStatusCode.OK, updated)
        }

        delete {
            val p = call.principal<JWTPrincipal>() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
            val emailFromToken = p.payload.getClaim("email").asString()

            val deleted = transaction {
                val u = Users.select(Users.id).where { Users.email eq emailFromToken }.firstOrNull() ?: return@transaction false
                val uid = u[Users.id]
                Profiles.deleteWhere { Profiles.userId eq uid }
                Users.deleteWhere { Users.id eq uid } > 0
            }
            if (deleted) call.respond(HttpStatusCode.OK) else call.respond(HttpStatusCode.NotFound)
        }
    }
}
