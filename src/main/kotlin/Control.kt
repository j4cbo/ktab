import kotlin.reflect.KClass

abstract class Control {
    abstract fun update(str: String)
}

class DoubleControl(var value: Double): Control() {
    override fun update(str: String) {
        value = str.toDouble()
    }
}

inline fun <reified T: Enum<T>> makeEnumControl(value: T) = EnumControl(value, T::class.java)

class EnumControl<T: Enum<T>>(var value: T, val klass: Class<T>): Control() {
    override fun update(str: String) {
       value = java.lang.Enum.valueOf(klass, str)
    }
}
