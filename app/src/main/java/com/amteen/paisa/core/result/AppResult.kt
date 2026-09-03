package com.amteen.paisa.core.result

/**
 * What can go wrong, as data rather than as an exception.
 *
 * Storage failures in particular must reach the UI as a message, never as a crash
 * — see CLAUDE.md rule 2.
 */
sealed interface AppError {

    /** A field the user can fix. [field] lets the form highlight the right input. */
    data class Validation(val field: String, val message: String) : AppError

    /** The requested record is gone — usually a stale deep link or back stack. */
    data class NotFound(val what: String) : AppError

    /** Reading or writing a file failed. */
    data class Storage(val message: String, val cause: Throwable? = null) : AppError

    /**
     * The file on disk was written by a newer version of the app. Refusing is the
     * only safe move: overwriting would destroy fields this build cannot represent.
     */
    data class SchemaTooNew(val fileVersion: Int, val supportedVersion: Int) : AppError

    val displayMessage: String
        get() = when (this) {
            is Validation -> message
            is NotFound -> "$what could not be found."
            is Storage -> message
            is SchemaTooNew ->
                "This data was saved by a newer version of Paisa " +
                    "(format $fileVersion, this build understands $supportedVersion). " +
                    "Update the app to open it."
        }
}

sealed interface AppResult<out T> {
    data class Ok<T>(val value: T) : AppResult<T>
    data class Err(val error: AppError) : AppResult<Nothing>

    val isOk: Boolean get() = this is Ok

    fun valueOrNull(): T? = (this as? Ok)?.value

    fun errorOrNull(): AppError? = (this as? Err)?.error

    companion object {
        val Success: AppResult<Unit> = Ok(Unit)
    }
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Ok -> AppResult.Ok(transform(value))
    is AppResult.Err -> this
}

inline fun <T> AppResult<T>.onOk(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Ok) action(value)
    return this
}

inline fun <T> AppResult<T>.onErr(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Err) action(error)
    return this
}
