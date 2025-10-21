import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class City(val name: String, val population: Int)

fun main() {
    val jsonString = readln()

    val city = 

    println("${city.name} ${city.population}")
}