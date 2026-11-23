package ua.kpi.solarrgr.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Role { ADMIN, VIEWER }

data class Session(val email: String, val role: Role, val expiresAt: Long)

/**
 * Навчальна токен-автентифікація. Токен генерується локально і НЕ є підписаним JWT;
 * сервер не перевіряє його. Для реальної системи потрібен бекенд з видачею та перевіркою токенів.
 */
class AuthManager(private val clock: () -> Long = System::currentTimeMillis) {
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    fun login(email: String, password: String): Result<Session> {
        val e = email.trim()
        if (!EMAIL.matches(e)) return Result.failure(IllegalArgumentException("Некоректний email"))
        if (password != DEMO_PASSWORD) return Result.failure(IllegalArgumentException("Невірний пароль"))
        val role = if (e.equals(ADMIN_EMAIL, ignoreCase = true)) Role.ADMIN else Role.VIEWER
        val s = Session(e, role, clock() + TTL_MS)
        _session.value = s
        return Result.success(s)
    }

    fun logout() { _session.value = null }

    /** Актуальна сесія; прострочена сесія скидається. */
    fun current(): Session? {
        val s = _session.value ?: return null
        if (clock() >= s.expiresAt) { _session.value = null; return null }
        return s
    }

    fun isAdmin() = current()?.role == Role.ADMIN

    companion object {
        const val ADMIN_EMAIL = "admin@solar.test"
        const val DEMO_PASSWORD = "solar1234"
        const val TTL_MS = 3_600_000L
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
