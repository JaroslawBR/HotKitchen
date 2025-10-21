package hotkitchen.plugins.routes

import hotkitchen.Categories
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.categoryRoutes() = authenticate("auth-jwt") {
    route("/categories") {
        get {
            val idParam = call.request.queryParameters["id"]?.toIntOrNull()
            if (idParam == null) {
                val list = transaction {
                    Categories.selectAll().map {
                        CategoryDTO(
                            categoryId = it[Categories.id],
                            title = it[Categories.title],
                            description = it[Categories.description]
                        )
                    }
                }
                call.respond(HttpStatusCode.OK, list)
            } else {
                val item = transaction {
                    Categories
                        .selectAll().where { Categories.id eq idParam }
                        .firstOrNull()
                        ?.let {
                            CategoryDTO(
                                categoryId = it[Categories.id],
                                title = it[Categories.title],
                                description = it[Categories.description]
                            )
                        }
                }
                if (item == null) call.respond(HttpStatusCode.BadRequest) else call.respond(HttpStatusCode.OK, item)
            }
        }

        post {
            if (!call.isStaff()) return@post call.respond(HttpStatusCode.Forbidden, StatusResponse("Access denied"))
            val body = runCatching { call.receive<CategoryPost>() }
                .getOrElse { return@post call.respond(HttpStatusCode.BadRequest) }

            val exists = transaction { Categories.select(Categories.id).where { Categories.id eq body.categoryId }.empty().not() }
            if (exists) return@post call.respond(HttpStatusCode.BadRequest)

            val dto = transaction {
                Categories.insert {
                    it[id] = body.categoryId
                    it[title] = body.title
                    it[description] = body.description
                }
                CategoryDTO(body.categoryId, body.title, body.description)
            }
            call.respond(HttpStatusCode.OK, dto)
        }
    }
}
