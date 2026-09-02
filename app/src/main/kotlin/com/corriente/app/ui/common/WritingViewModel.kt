package com.corriente.app.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Одно сообщение пользователю (ошибка записи, подтверждение). Текста здесь нет —
 * только ресурс строки и аргументы форматирования (R6.3, ROADMAP.md §8: ViewModel не
 * должен склеивать русский текст в коде, `stringResource` резолвится уже в Composable,
 * который умеет обе локали).
 */
data class UiMessage(val resId: Int, val args: List<Any> = emptyList())

/** Короткая форма конструктора [UiMessage] для `onError`/[WritingViewModel.postMessage]. */
fun uiMessage(resId: Int, vararg args: Any): UiMessage = UiMessage(resId, args.toList())

/**
 * База для ViewModel, которые пишут в репозитории (F0.2). Любая запись идёт через [launchWrite]:
 * необработанное исключение репозитория раньше доходило до обработчика по умолчанию и убивало
 * процесс — теперь оно превращается в [messages] для снекбара, а форма остаётся на экране.
 *
 * Никаких generic-обвязок (BUILD_PLAN.md §0 правило 4): три члена и один метод.
 */
abstract class WritingViewModel : ViewModel() {

    private val _messages = MutableStateFlow<UiMessage?>(null)
    val messages: StateFlow<UiMessage?> = _messages

    /** Экран зовёт после показа снекбара. */
    fun consumeMessage() {
        _messages.value = null
    }

    /** Показать сообщение вне пути записи (напр. «валюта уже зафиксирована»). */
    protected fun postMessage(resId: Int, vararg args: Any) {
        _messages.value = uiMessage(resId, *args)
    }

    /**
     * Запускает запись в [viewModelScope]. При успехе — [onSuccess] (закрыть форму, выставить
     * `finished`). При исключении — сообщение из [onError] в [messages]; [onSuccess] не
     * вызывается, состояние формы не трогается.
     */
    protected fun launchWrite(
        onError: (Throwable) -> UiMessage,
        onSuccess: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                block()
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _messages.value = onError(e)
            }
        }
    }
}

/**
 * Похоже ли исключение на нарушение уникального индекса БД (дубликат имени и т.п.).
 * Определяется по имени класса/тексту, чтобы не тянуть `android.database` в юнит-тесты.
 */
fun Throwable.looksLikeConstraintViolation(): Boolean =
    this::class.qualifiedName?.contains("Constraint", ignoreCase = true) == true ||
        message?.contains("UNIQUE", ignoreCase = true) == true ||
        message?.contains("constraint", ignoreCase = true) == true
