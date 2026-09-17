package com.manhwaread.core.common

import java.io.IOException

/**
 * Результат доменной операции: значение или [AppError].
 * В отличие от kotlin.Result, ветка ошибки типизирована доменными ошибками,
 * поэтому вызывающий код обязан обработать именно их.
 *
 * [map], [flatMap], [getOrElse], [fold] — inline-расширения ниже по файлу
 * (inline запрещён на виртуальных членах интерфейса).
 */
sealed interface DomainResult<out T> {
    data class Success<out T>(val value: T) : DomainResult<T>
    data class Failure(val error: AppError) : DomainResult<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    /** Значение при успехе, иначе null. */
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    /** Ошибка при провале, иначе null. */
    fun errorOrNull(): AppError? = when (this) {
        is Success -> null
        is Failure -> error
    }

    /** Заменяет ошибку; успех проходит насквозь. */
    fun mapError(transform: (AppError) -> AppError): DomainResult<T> = when (this) {
        is Success -> this
        is Failure -> Failure(transform(error))
    }

    companion object {
        fun <T> success(value: T): DomainResult<T> = Success(value)

        fun failure(error: AppError): DomainResult<Nothing> = Failure(error)

        /**
         * Выполняет [block], отображая IOException в [AppError.Network],
         * остальные исключения — в [AppError.Unknown].
         */
        inline fun <T> capturing(block: () -> T): DomainResult<T> = try {
            Success(block())
        } catch (e: IOException) {
            Failure(AppError.Network(e))
        } catch (e: Exception) {
            Failure(AppError.Unknown(e))
        }
    }
}

/** Трансформирует значение при успехе; ошибка проходит насквозь. */
inline fun <T, R> DomainResult<T>.map(transform: (T) -> R): DomainResult<R> = when (this) {
    is DomainResult.Success -> DomainResult.Success(transform(value))
    is DomainResult.Failure -> this
}

/** Цепочка операций, возвращающих [DomainResult]; ошибка прерывает цепочку. */
inline fun <T, R> DomainResult<T>.flatMap(transform: (T) -> DomainResult<R>): DomainResult<R> = when (this) {
    is DomainResult.Success -> transform(value)
    is DomainResult.Failure -> this
}

/** Значение при успехе, иначе результат [onFailure]. */
inline fun <T> DomainResult<T>.getOrElse(onFailure: (AppError) -> T): T = when (this) {
    is DomainResult.Success -> value
    is DomainResult.Failure -> onFailure(error)
}

/** Свертка обеих веток в одно значение. */
inline fun <T, R> DomainResult<T>.fold(onSuccess: (T) -> R, onFailure: (AppError) -> R): R = when (this) {
    is DomainResult.Success -> onSuccess(value)
    is DomainResult.Failure -> onFailure(error)
}
