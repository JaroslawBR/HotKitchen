package hotkitchen.plugins.routes

import hotkitchen.MealCategories
import hotkitchen.Meals
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.mealRoutes() = authenticate("auth-jwt") {
    route("/meals") {
        get {
            val idParam = call.request.queryParameters["id"]?.toIntOrNull()
            if (idParam == null) {
                val list = transaction {
                    Meals.selectAll().map { row ->
                        val base = rowToMealDTOBare(row)
                        base.copy(categoryIds = categoryIdsFor(base.mealId))
                    }
                }
                call.respond(HttpStatusCode.OK, list)
            } else {
                val item = transaction {
                    Meals.selectAll().where { Meals.id eq idParam }.firstOrNull()?.let { row ->
                        val base = rowToMealDTOBare(row)
                        base.copy(categoryIds = categoryIdsFor(base.mealId))
                    }
                }
                if (item == null) call.respond(HttpStatusCode.BadRequest) else call.respond(HttpStatusCode.OK, item)
            }
        }

        post {
            if (!call.isStaff()) return@post call.respond(HttpStatusCode.Forbidden, StatusResponse("Access denied"))
            val body = runCatching { call.receive<MealPost>() }
                .getOrElse { return@post call.respond(HttpStatusCode.BadRequest) }

            val exists = transaction { Meals.select(Meals.id).where { Meals.id eq body.mealId }.empty().not() }
            if (exists) return@post call.respond(HttpStatusCode.BadRequest)

            // 1) dodaj posiłek
            val ok = runCatching {
                transaction {
                    Meals.insert {
                        it[id] = body.mealId
                        it[title] = body.title
                        it[price] = body.price
                        it[imageUrl] = body.imageUrl
                    }
                }
            }.isSuccess
            if (!ok) return@post call.respond(HttpStatusCode.BadRequest)

            // 2) relacje (kolejność z POST; best-effort)
            val relIds = body.categoryIds.distinct()
            runCatching {
                transaction {
                    relIds.withIndex().forEach { (idx, cid) ->
                        try {
                            MealCategories.insert {
                                it[mealId] = body.mealId
                                it[categoryId] = cid
                                it[position] = idx
                            }
                        } catch (_: Exception) { /* ignoruj pojedyncze kolizje */ }
                    }
                }
            }

            call.respond(
                HttpStatusCode.OK,
                MealDTO(
                    mealId = body.mealId,
                    title = body.title,
                    price = body.price,
                    imageUrl = body.imageUrl,
                    categoryIds = relIds
                )
            )
        }
    }
}
