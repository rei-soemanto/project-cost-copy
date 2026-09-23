package com.costproject.app.data

/**
 * Every failure the data layer can report upward.
 *
 * Repositories are the error boundary: Ktor and serialization exceptions are
 * caught there and mapped to one of these, so ViewModels never see a platform
 * exception type. Each subtype carries a message fit to show the user.
 */
sealed class DataError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** No connection, DNS failure, timeout - the server was never reached. */
    class Network(cause: Throwable? = null) :
        DataError("Tidak dapat terhubung ke server. Periksa koneksi internet Anda.", cause)

    /** Session is gone (refresh failed or was revoked). The user must log in again. */
    class Unauthorized(message: String = "Sesi Anda telah berakhir. Silakan masuk kembali.") :
        DataError(message)

    /** Wrong email or password on login. */
    class InvalidCredentials :
        DataError("Email atau kata sandi salah.")

    /** The server rejected the input. [fieldErrors] maps field path to message. */
    class Validation(message: String, val fieldErrors: Map<String, String> = emptyMap()) :
        DataError(message)

    /** Duplicate - e.g. an email that is already registered. */
    class Conflict(message: String) : DataError(message)

    /** Signed in, but not allowed - e.g. a non-admin asking for everyone's data. */
    class Forbidden(message: String = "Anda tidak memiliki akses untuk tindakan ini.") : DataError(message)

    class NotFound(message: String = "Data tidak ditemukan.") : DataError(message)

    class RateLimited :
        DataError("Terlalu banyak percobaan. Silakan coba lagi nanti.")

    /** 5xx, or a response the client could not understand. */
    class Server(val statusCode: Int? = null, cause: Throwable? = null) :
        DataError("Terjadi kesalahan pada server. Silakan coba lagi.", cause)
}
