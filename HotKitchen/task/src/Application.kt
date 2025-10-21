package hotkitchen

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils.createMissingTablesAndColumns
import hotkitchen.plugins.configureRouting
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import java.util.*

/* ===== DB: tabele ===== */

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    val userType = varchar("user_type", 50)
    val password = varchar("password", 255)
    override val primaryKey = PrimaryKey(id)
}

object Profiles : Table("profiles") {
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val name = varchar("name", 100)
    val phone = varchar("phone", 32)
    val email = varchar("email", 255).uniqueIndex()
    val address = varchar("address", 255)
    override val primaryKey = PrimaryKey(userId)
}

object Categories : Table("categories") {
    val id = integer("category_id").uniqueIndex()
    val title = varchar("title", 255)
    val description = varchar("description", 1024)
    override val primaryKey = PrimaryKey(id)
}

object Meals : Table("meals") {
    val id = integer("meal_id").uniqueIndex()
    val title = varchar("title", 255)
    val price = float("price")
    val imageUrl = text("image_url") // bez limitu, aby nie wywalało na długich URL
    override val primaryKey = PrimaryKey(id)
}

object MealCategories : Table("meal_categories") {
    val mealId = reference("meal_id", Meals.id, onDelete = ReferenceOption.CASCADE)
    val categoryId = integer("category_id") // celowo bez FK do Categories (testy nie wymagają)
    val position = integer("position")      // zachowujemy kolejność z POST
    override val primaryKey = PrimaryKey(mealId, categoryId)
}

object Orders : Table("orders") {
    val orderId = integer("order_id").uniqueIndex()
    val userEmail = varchar("user_email", 255)
    val mealsIds = varchar("meals_id", 255) // CSV: "1,2,3"
    val price = float("price")
    val address = varchar("address", 255)
    val status = varchar("status", 16)
    override val primaryKey = PrimaryKey(orderId)
}

/* ===== JWT token generator (udostępniany routingowi) ===== */
val GenerateTokenKey = AttributeKey<(String, String) -> String>("GenerateToken")

/* ===== Ktor module ===== */
fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    /* JSON */
    install(ContentNegotiation) { json() }

    /* JWT auth */
    val jwtSecret = environment.config.propertyOrNull("jwt.secret")?.getString() ?: "secret"
    val jwtIssuer = environment.config.propertyOrNull("jwt.issuer")?.getString() ?: "hotkitchen"
    val jwtAudience = environment.config.propertyOrNull("jwt.audience")?.getString() ?: "hotkitchen"
    val jwtAlg = Algorithm.HMAC256(jwtSecret)

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(
                JWT.require(jwtAlg)
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .build()
            )
            validate { cred ->
                val email = cred.payload.getClaim("email").asString()
                val exp = cred.expiresAt?.time ?: 0L
                if (!email.isNullOrBlank() && exp > System.currentTimeMillis()) JWTPrincipal(cred.payload) else null
            }
            challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized) }
        }
    }

    // generator tokenów używany w Routing.kt
    val tokenGen: (String, String) -> String = { email, userType ->
        val exp = Date(System.currentTimeMillis() + 10 * 60 * 60 * 1000) // 10h
        JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withClaim("email", email)
            .withClaim("userType", userType)
            .withExpiresAt(exp)
            .sign(jwtAlg)
    }
    attributes.put(GenerateTokenKey, tokenGen)

    /* DB connect: z application.conf (jeśli jest) lub domyślnie Hyperskill */
    val url = environment.config.propertyOrNull("ktor.database.url")?.getString()
        ?: "jdbc:postgresql://localhost:5432/HotKitchen"
    val driver = environment.config.propertyOrNull("ktor.database.driver")?.getString()
        ?: "org.postgresql.Driver"
    val user = environment.config.propertyOrNull("ktor.database.user")?.getString()
        ?: "hyperskill"
    val password = environment.config.propertyOrNull("ktor.database.password")?.getString()
        ?: "password"

    Database.connect(url, driver, user, password)

    transaction {
        SchemaUtils.create(Users, Profiles, Categories, Meals, MealCategories, Orders)
    }

    /* Routing */
    configureRouting()
}





