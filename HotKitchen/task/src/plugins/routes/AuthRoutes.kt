package hotkitchen.plugins.routes

import hotkitchen.GenerateTokenKey
import hotkitchen.Users
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.authRoutes() {

    post("/signup") {
        val body = runCatching { call.receive<SignupRequest>() }
            .getOrElse { return@post call.respond(HttpStatusCode.Forbidden, StatusResponse("Invalid email or password")) }

        if (!isValidEmail(body.email)) return@post call.respond(HttpStatusCode.Forbidden, StatusResponse("Invalid email"))
        if (!isValidPassword(body.password)) return@post call.respond(HttpStatusCode.Forbidden, StatusResponse("Invalid password"))

        val exists = transaction { Users.select(Users.id).where { Users.email eq body.email }.empty().not() }
        if (exists) return@post call.respond(HttpStatusCode.Forbidden, StatusResponse("User already exists"))

        transaction {
            Users.insert {
                it[email] = body.email
                it[userType] = body.userType
                it[password] = body.password
            }
        }

        val token = call.application.attributes[GenerateTokenKey](body.email, body.userType)
        call.respond(HttpStatusCode.OK, TokenResponse(token))
    }

    post("/signin") {
        val body = runCatching { call.receive<SigninRequest>() }
            .getOrElse { return@post call.respond(HttpStatusCode.Forbidden, StatusResponse("Invalid email or password")) }

        val row = transaction {
            Users
                .select(Users.email, Users.userType, Users.password)
                .where { Users.email eq body.email }
                .firstOrNull()
        }
        val ok = row != null && row[Users.password] == body.password
        if (!ok) return@post call.respond(HttpStatusCode.Forbidden, StatusResponse("Invalid email or password"))

        val token = call.application.attributes[GenerateTokenKey](row[Users.email], row[Users.userType])
        call.respond(HttpStatusCode.OK, TokenResponse(token))
    }

    authenticate("auth-jwt") {
        get("/validate") {
            val p = call.principal<JWTPrincipal>()!!
            val email = p.payload.getClaim("email").asString()
            val userType = p.payload.getClaim("userType").asString()
            call.respondText("Hello, $userType $email", ContentType.Text.Plain)
        }
    }
}
