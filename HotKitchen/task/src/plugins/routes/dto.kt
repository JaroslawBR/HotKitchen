package hotkitchen.plugins.routes


import kotlinx.serialization.Serializable

@Serializable data class SignupRequest(val email: String, val userType: String, val password: String)
@Serializable data class SigninRequest(val email: String, val password: String)
@Serializable data class StatusResponse(val status: String)
@Serializable data class TokenResponse(val token: String)

@Serializable
data class ProfileDTO(
    val name: String,
    val userType: String,
    val phone: String,
    val email: String,
    val address: String
)

@Serializable
data class ProfileUserRequest(
    val name: String,
    val userType: String,
    val phone: String,
    val email: String,
    val address: String
)

@Serializable
data class MealPost(
    val mealId: Int,
    val title: String,
    val price: Float,
    val imageUrl: String,
    val categoryIds: List<Int>
)

@Serializable
data class MealDTO(
    val mealId: Int,
    val title: String,
    val price: Float,
    val imageUrl: String,
    val categoryIds: List<Int>
)

@Serializable
data class CategoryPost(
    val categoryId: Int,
    val title: String,
    val description: String
)

@Serializable
data class CategoryDTO(
    val categoryId: Int,
    val title: String,
    val description: String
)

@Serializable
data class OrderDTO(
    val orderId: Int,
    val userEmail: String,
    val mealsIds: List<Int>,
    val price: Float,
    val address: String,
    val status: String
)

const val ORDER_STATUS_IN_PROGRESS = "IN PROGRESS"
const val ORDER_STATUS_COMPLETE = "COMPLETE"
