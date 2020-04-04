abstract class Control {
    abstract val name: String
    abstract fun update(parts: List<String>)
}

class DoubleControl(override val name: String, var value: Double): Control() {
    override fun update(parts: List<String>) {
        value = parts[0].toDouble()
    }
}

inline fun <reified T: Enum<T>> makeEnumControl(name: String, value: T) = EnumControl(name, value, T::class.java)

class EnumControl<T: Enum<T>>(override val name: String, var value: T, private val klass: Class<T>): Control() {
    override fun update(parts: List<String>) {
        val newValue = java.lang.Enum.valueOf(klass, parts[0])
        value = newValue
    }
}
