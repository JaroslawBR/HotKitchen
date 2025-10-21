package hotkitchen.plugins.routes

import hotkitchen.Meals
import hotkitchen.Orders
import hotkitchen.Profiles
import hotkitchen.Users
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.math.absoluteValue
import kotlin.random.Random

private fun rowToOrderDTO(row: ResultRow): OrderDTO =
    OrderDTO(
        orderId = row[Orders.orderId],
        userEmail = row[Orders.userEmail],
        mealsIds = row[Orders.mealsIds].split(',').mapNotNull { it.toIntOrNull() },
        price = row[Orders.price],
        address = row[Orders.address],
        status = row[Orders.status]
    )

fun Route.ordersRoutes() = authenticate("auth-jwt") {

    post("/order") {
        val requestedMealIds = runCatching { call.receive<List<Int>>() }
            .getOrElse { return@post call.respond(HttpStatusCode.BadRequest) }

        if (requestedMealIds.isEmpty()) {
            return@post call.respond(HttpStatusCode.BadRequest)
        }

        val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val emailFromToken = principal.payload.getClaim("email").asString()
        if (emailFromToken.isNullOrBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest)
        }

        val userIdVal = transaction {
            Users
                .select(Users.id)
                .where { Users.email eq emailFromToken }
                .firstOrNull()
                ?.get(Users.id)
        } ?: return@post call.respond(HttpStatusCode.BadRequest)

        val addressOrder = transaction {
            Profiles
                .select(Profiles.address)
                .where { Profiles.userId eq userIdVal }
                .firstOrNull()
                ?.get(Profiles.address)
        } ?: return@post call.respond(HttpStatusCode.BadRequest)

        if (addressOrder.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest)
        }

        val mealsData = transaction {
            Meals
                .select(Meals.id, Meals.price)
                .where { Meals.id inList requestedMealIds }
                .associate { it[Meals.id] to it[Meals.price] }
        }
        if (mealsData.size != requestedMealIds.distinct().size) {
            return@post call.respond(HttpStatusCode.BadRequest)
        }

        val totalPrice = requestedMealIds.sumOf { mealsData[it]!!.toDouble() }.toFloat()

        val newOrderId = Random.nextInt().absoluteValue
        val mealsIdsString = requestedMealIds.joinToString(",")

        val dto = transaction {
            Orders.insert {
                it[orderId] = newOrderId
                it[userEmail] = emailFromToken
                it[mealsIds] = mealsIdsString
                it[price] = totalPrice
                it[address] = addressOrder
                it[status] = ORDER_STATUS_IN_PROGRESS
            }
            OrderDTO(
                orderId = newOrderId,
                userEmail = emailFromToken,
                mealsIds = requestedMealIds,
                price = totalPrice,
                address = addressOrder,
                status = ORDER_STATUS_IN_PROGRESS
            )
        }

        call.respond(HttpStatusCode.OK, dto)
    }

    post("/order/{orderId}/markReady") {
        val orderIdParam = call.parameters["orderId"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, StatusResponse("Invalid order ID"))

        val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val userType = principal.payload.getClaim("userType").asString()
        if (userType != "staff") {
            return@post call.respond(HttpStatusCode.Forbidden, StatusResponse("Access denied"))
        }

        val updatedRow = transaction {
            val exists = Orders
                .select(Orders.orderId)
                .where { Orders.orderId eq orderIdParam }
                .empty()
                .not()

            if (!exists) return@transaction null

            Orders.update({ Orders.orderId eq orderIdParam }) {
                it[status] = ORDER_STATUS_COMPLETE
            }

            Orders
                .selectAll()
                .where { Orders.orderId eq orderIdParam }
                .firstOrNull()
        }

        if (updatedRow == null) {
            call.respond(HttpStatusCode.BadRequest)
        } else {
            call.respond(HttpStatusCode.OK, rowToOrderDTO(updatedRow))
        }
    }

    get("/orderHistory") {
        val all = transaction {
            Orders
                .selectAll()
                .map { rowToOrderDTO(it) }
        }
        call.respond(HttpStatusCode.OK, all)
    }

    get("/orderIncomplete") {
        val list = transaction {
            Orders
                .selectAll()
                .where { Orders.status eq ORDER_STATUS_IN_PROGRESS }
                .map { rowToOrderDTO(it) }
        }
        call.respond(HttpStatusCode.OK, list)
    }
}
