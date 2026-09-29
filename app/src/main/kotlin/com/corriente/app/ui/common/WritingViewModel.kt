package com.corriente.app.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corriente.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.OutputStream
import kotlin.coroutines.CoroutineContext

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
 * A2.3: некорректный ввод пользователя, обнаруженный внутри [WritingViewModel.launchWrite] — блок «завершился
 * бы успешно» и экран закрыл бы форму, ничего не сохранив; вместо этого исключение превращается в [ui] через
 * `onError`, а форма остаётся открытой.
 */
class InvalidInputException(val ui: UiMessage) : Exception()

/**
 * Положительна ли сумма в тексте поля. Не зависит от валюты (для валюты без дробной части «0.5» проверит
 * ViewModel уже при сохранении) — нужна, чтобы кнопка «Сохранить» была неактивна на пустой/нулевой сумме.
 */
fun amountTextIsPositive(text: String): Boolean {
    val probe = com.corriente.money.Currency(com.corriente.money.CurrencyCode("XXX"), minorUnits = 2, displayScale = 2, symbol = "¤")
    return (com.corriente.money.AmountInput.fromText(text, probe).toMinorOrNull(probe)?.raw ?: 0L) > 0L
}

/**
 * База для ViewModel, которые пишут в репозитории (F0.2). Любая запись идёт через [launchWrite]:
 * необработанное исключение репозитория раньше доходило до обработчика по умолчанию и убивало
 * процесс — теперь оно превращается в [messages] для снекбара, а форма остаётся на экране.
 *
 * Никаких generic-обвязок (BUILD_PLAN.md §0 правило 4): три члена и один метод.
 */
abstract class WritingViewModel(
    /** Контекст для файлового ввода-вывода (A2.1); в тестах подменяется тестовым диспетчером. */
    protected val io: CoroutineContext = Dispatchers.IO,
) : ViewModel() {

    private val _messages = MutableStateFlow<UiMessage?>(null)
    val messages: StateFlow<UiMessage?> = _messages

    /** Экран зовёт после показа снекбара. */
    fun consumeMessage() {
        _messages.value = null
    }

    protected fun postMessage(message: UiMessage) {
        _messages.value = message
    }

    /** Показать сообщение вне пути записи (напр. «валюта уже зафиксирована»). */
    protected fun postMessage(resId: Int, vararg args: Any) {
        _messages.value = uiMessage(resId, *args)
    }

    /**
     * A2.1: записывает файл, выбранный пользователем через SAF. [open] (открытие `ContentResolver`-потока — может
     * бросить `FileNotFoundException`/`SecurityException`, а у медленного провайдера — подвесить поток) и [bytes]
     * выполняются на [io] внутри `try`; итог — сообщение «Сохранено»/«Не удалось сохранить», а не падение.
     */
    protected fun writeFile(open: () -> OutputStream, bytes: () -> ByteArray) {
        viewModelScope.launch(io) {
            val ok = try {
                open().use { it.write(bytes()) }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                false
            }
            postMessage(if (ok) R.string.file_saved else R.string.file_save_failed)
        }
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
