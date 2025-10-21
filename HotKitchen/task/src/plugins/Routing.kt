package hotkitchen.plugins

import hotkitchen.plugins.routes.authRoutes
import hotkitchen.plugins.routes.categoryRoutes
import hotkitchen.plugins.routes.mealRoutes
import hotkitchen.plugins.routes.ordersRoutes
import hotkitchen.plugins.routes.profileRoutes
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/") { call.respondText("Hello World!") }

        authRoutes()
        profileRoutes()
        mealRoutes()
        categoryRoutes()
        ordersRoutes()
    }
}




