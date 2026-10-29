package it.snel.kaartscan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ServerStatus {
    /** not logged in, or nothing asked yet */
    UNKNOWN,
    /** the last request got a normal answer */
    OK,
    /** the server answered, but not as expected (HTTP 5xx, error_system(), no JSON, ...) */
    UNEXPECTED,
    /** no connection or no answer */
    DOWN,
}

data class ServerSnapshot(
    val status: ServerStatus = ServerStatus.UNKNOWN,
    /** System.currentTimeMillis() of the last answer, null if none */
    val lastContactAt: Long? = null,
    val lastLatencyMs: Long? = null,
    val avgLatencyMs: Long? = null,
    val okCount: Int = 0,
    val unexpectedCount: Int = 0,
    val downCount: Int = 0,
    val lastProblem: String? = null,
    val lastProblemAt: Long? = null,
)

/**
 * What the app knows about the server, for the status dot. Every HTTP
 * request reports here; the status is the outcome of the most recent one.
 * Thread safe.
 */
class ServerStats {
    private val _snapshot = MutableStateFlow(ServerSnapshot())
    val snapshot: StateFlow<ServerSnapshot> = _snapshot.asStateFlow()

    fun reset() {
        _snapshot.value = ServerSnapshot()
    }

    /** an answer arrived; [latencyMs] from sending the request to the end of the body */
    fun ok(latencyMs: Long) = _snapshot.update { s ->
        s.copy(
            status = ServerStatus.OK,
            lastContactAt = System.currentTimeMillis(),
            lastLatencyMs = latencyMs,
            // exponential moving average, recent requests count most
            avgLatencyMs = s.avgLatencyMs?.let { (it * 4 + latencyMs) / 5 } ?: latencyMs,
            okCount = s.okCount + 1,
        )
    }

    /**
     * The server answered, but wrongly. Called after [ok] for the same
     * request when the problem only shows in the body, so that one is
     * counted as unexpected instead.
     */
    fun unexpected(problem: String, countedAsOk: Boolean = true) = _snapshot.update { s ->
        val now = System.currentTimeMillis()
        s.copy(
            status = ServerStatus.UNEXPECTED,
            lastContactAt = now,
            okCount = if (countedAsOk) (s.okCount - 1).coerceAtLeast(0) else s.okCount,
            unexpectedCount = s.unexpectedCount + 1,
            lastProblem = problem,
            lastProblemAt = now,
        )
    }

    fun down(problem: String) = _snapshot.update { s ->
        s.copy(
            status = ServerStatus.DOWN,
            downCount = s.downCount + 1,
            lastProblem = problem,
            lastProblemAt = System.currentTimeMillis(),
        )
    }
}
