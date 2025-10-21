package hotkitchen.plugins.routes


import hotkitchen.MealCategories
import hotkitchen.Meals
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder

/* Walidacje */
private val emailRegex = Regex("^[A-Za-z0-9]+[A-Za-z0-9._%+-]*@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
fun isValidEmail(s: String) = emailRegex.matches(s)
fun isValidPassword(s: String): Boolean {
    if (s.length < 6) return false
    val hasLetter = s.any { it.isLetter() }
    val hasDigit = s.any { it.isDigit() }
    return hasLetter && hasDigit
}

/* RBAC */
fun ApplicationCall.isStaff(): Boolean {
    val p = principal<JWTPrincipal>() ?: return false
    return p.payload.getClaim("userType").asString().equals("staff", ignoreCase = true)
}

/* Mapowanie – bez zagnieżdżonych transakcji */
fun rowToMealDTOBare(row: ResultRow): MealDTO =
    MealDTO(
        mealId = row[Meals.id],
        title = row[Meals.title],
        price = row[Meals.price],
        imageUrl = row[Meals.imageUrl],
        categoryIds = emptyList()
    )

fun categoryIdsFor(mealId: Int): List<Int> =
    MealCategories
        .select(MealCategories.categoryId)
        .where { MealCategories.mealId eq mealId }
        .orderBy(MealCategories.position to SortOrder.ASC)
        .map { it[MealCategories.categoryId] }
