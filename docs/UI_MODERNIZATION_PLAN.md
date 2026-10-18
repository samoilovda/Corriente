# Инструкция для ИИ-агента: модернизация визуального стиля Corriente (M3 Expressive / Neo-Fintech)

Этот документ — пошаговое техническое задание для ИИ-агента по переводу интерфейса Corriente (`:app`, `:desktop`, `:widget`) из базового «коробочного» Material 3 в выразительный современный стиль **Material 3 Expressive / Neo-Fintech**.

База: коммит `b091445` (после выполнения `docs/UI_PLAN.md` и `docs/audit/2026-10-18/AGENT_IMPLEMENTATION.md`).

---

## 0. Обязательные правила и ограничения (Прочитать перед стартом!)

1. **Инварианты неприкосновенны (`docs/INVARIANTS.md`):**
   - **I-1 (Никаких Float/Double для денег):** деньги везде представлены `Money` / `Minor` (`Long`). Статический сканер `buildSrc/src/main/kotlin/corriente/buildsupport/InvariantGuards.kt` **запрещает** слова `Float`, `Double`, `.toFloat()`, `.toDouble()` во всех файлах `:app`, `:desktop`, `:widget`, кроме явно перечисленных в `InvariantGuards.floatAllowlist`.
   - Литералы вида `0.16f` для `alpha` или `12.dp` разрешены везде, но если в новом/изменяемом файле нужна геометрия `Canvas` или `LinearProgressIndicator` с приведением `.toFloat()`, либо держите эту отрисовку в уже разрешённых файлах (`MonthOverviewHeader.kt`, `ReportCharts.kt`, `ReportScreen.kt`, `FxReportScreen.kt`, `RingCanvas.kt`), либо осознанно добавьте чисто визуальный файл в `InvariantGuards.floatAllowlist` с комментарием-обоснованием и проверьте `./gradlew :buildSrc:test`.
   - **I-8 (Валюты не смешиваются):** ни одна карточка, кольцо или сводка не суммирует разные валюты.
   - **I-24 (Полный офлайн):** никаких сетевых шрифтов (Google Fonts по сети), иконок по URL или внешних SDK.
2. **Контраст и доступность (WCAG AA + TalkBack):**
   - Обычный текст к фону — контраст `>= 4.5:1`, крупный текст и значимые иконки — `>= 3.0:1` (проверяется в `ContrastTest.kt`).
   - Минимальный размер интерактивной цели — `48.dp`.
   - Все существующие `semantics`, `contentDescription`, `heading()`, `liveRegion` и `Role` обязаны сохраниться: инструментальные тесты в `app/src/androidTest` ищут узлы по тексту и семантике.
3. **Строковые ресурсы и локализация (`StringResourcesParityTest`):**
   - Любая новая строка добавляется синхронно в `app/src/main/res/values/strings.xml` (RU) и `app/src/main/res/values-en/strings.xml` (EN). Хардкод пользовательских строк в `@Composable` запрещён (кроме `:desktop`, где интерфейс русскоязычный).
4. **Дисциплина коммитов и проверок:**
   - Один этап (`M-1` … `M-6`) — один атомарный коммит с префиксом `M-X: краткое описание`.
   - Перед каждым коммитом обязательно запускать:
     ```bash
     ./gradlew check assembleDebug --no-daemon --max-workers=4
     ```
   - Если затронуты экраны с инструментальными тестами (`TransactionsScreen`, `TxnEntryScreen`, `SettingsScreen`, `BudgetsScreen`, навигация), убедиться, что не сломаны селекторы в `app/src/androidTest/`.

---

## Этап M-1: Выразительная типографика, форматирование сумм и иконки категорий по умолчанию

**Файлы:**
- `app/src/main/kotlin/com/corriente/app/ui/theme/Type.kt`
- `app/src/main/kotlin/com/corriente/app/ui/common/MoneyText.kt`
- `app/src/main/kotlin/com/corriente/app/ui/common/CategoryIconCatalog.kt`
- `app/src/test/kotlin/com/corriente/app/ui/common/MoneyTextFormatTest.kt` (новый тест)

### Задачи:
1. **Обновить типографическую шкалу (`Type.kt`):**
   - Настроить `CorrienteTypography` с выразительной иерархией заголовков и цифр:
     - `displaySmall` / `headlineLarge` / `headlineMedium`: `fontWeight = FontWeight.SemiBold`, `letterSpacing = (-0.5).sp`, `fontFeatureSettings = "tnum"`.
     - `titleLarge`: `fontWeight = FontWeight.SemiBold`, `letterSpacing = (-0.25).sp`.
     - `titleMedium` / `titleSmall`: `fontWeight = FontWeight.Medium`.
     - `labelLarge`: `fontWeight = FontWeight.Medium`.
   - В `MoneyTextStyle` сохранить `fontFeatureSettings = "tnum"`, `fontWeight = FontWeight.SemiBold`.
2. **Визуальный акцент целой и дробной части в `MoneyText.kt`:**
   - Добавить чистую функцию `splitMoneyDisplay(formatted: String): MoneyDisplayParts` (где `primary: String` — знак и целая часть, `secondary: String` — дробная часть `.XX` и символ/код валюты, если есть дробная часть).
   - В `MoneyText` добавить опциональный параметр `deemphasizeFraction: Boolean = false`. Когда `true`, рендерить через `buildAnnotatedString`: целую часть в полном цвете и размере `style`, а дробную часть и символ валюты — с `alpha = 0.68f` и весом `FontWeight.Normal`. При этом `semantics` узла должен отдавать полную слитную строку `text` без разбиения для TalkBack.
   - Покрыть `splitMoneyDisplay` unit-тестами (`MoneyTextFormatTest`: суммы с дробной частью `-1 250.50 ₽`, без дробной `125 ¥`, с 3 знаками `1.234 KWD`, переводы).
3. **Фолбэк-иконки категорий вместо сырых букв (`CategoryIconCatalog.kt`):**
   - В `CategoryGlyph` (когда `icon.isNullOrBlank()`), вместо бледной первой буквы названия, сопоставлять стандартные категории или возвращать аккуратную дефолтную векторную иконку/глиф, либо стилизовать буквенный монограмм-бейдж с `fontWeight = FontWeight.SemiBold`.

**Критерий приёмки M-1:** `./gradlew testDebugUnitTest` зелёный, `MoneyTextFormatTest` проходит, `ContrastTest` проходит.

---

## Этап M-2: Главный экран («Операции») — Bento-шапка, воздушное кольцо и группировка списка

**Файлы:**
- `app/src/main/kotlin/com/corriente/app/ui/transactions/MonthOverviewHeader.kt`
- `app/src/main/kotlin/com/corriente/app/ui/transactions/TransactionsScreen.kt`
- `app/src/main/kotlin/com/corriente/app/ui/common/GroupShape.kt` (вынести общую функцию формы группы)

### Задачи:
1. **Модернизировать кольцо расходов `Ring` в `MonthOverviewHeader.kt`:**
   - Использовать `Stroke(width = strokeWidth, cap = StrokeCap.Round)` для непустых сегментов кольца.
   - Когда активных сегментов (`slices.count { it.valueMinor > 0 }`) больше одного, вычитать зазор `gapDegrees = 4f` из каждого `sweep` (если `sweep > gapDegrees`), чтобы между сегментами появились аккуратные промежутки.
   - Фоновый трек пустого кольца (`empty`) рисовать тоньше (`strokeWidth * 0.65f`) цветом `surfaceContainerHighest`.
   - **Центр кольца:** внутри `Box(Modifier.size(HEADER_SIZE), contentAlignment = Alignment.Center)` поверх `Ring` (в центре бублика, ширина `RING_SIZE - 36.dp`) вывести компактную колонку:
     - мелкая подпись `stringResource(R.string.category_kind_expense)` (`labelSmall`, `onSurfaceVariant`);
     - сумма расходов `MoneyText(overview.expenseText, MoneyKind.EXPENSE, style = MaterialTheme.typography.titleMedium, deemphasizeFraction = true, maxLines = 1)` (с `clearAndSetSemantics {}`, чтобы не дублировать озвучку `ringDescription` и карточек ниже).
2. **Обновить иконки на орбите кольца (`CategoryIcon` в `MonthOverviewHeader.kt`):**
   - Заменить бледные кружки `color.copy(alpha = 0.18f)` на выразительные плашки `Surface` с `shape = RoundedCornerShape(14.dp)` (squircle), `color = surfaceContainerLow`, тонкой рамкой `BorderStroke(1.dp, color.copy(alpha = 0.35f))` и внутренним цветным акцентом, а процент под иконкой выводить в мини-пилюле или плотным `labelSmall` (`FontWeight.SemiBold`).
3. **Bento-плитки сводки месяца (`MonthOverviewHeader.kt`):**
   - Оформить блок «Расходы за месяц» и «Доходы за месяц» (`MonthAmount`) как две симметричные карточки `Surface` (`shape = MaterialTheme.shapes.medium`, `color = MaterialTheme.colorScheme.surfaceContainerLow`, внутренний отступ `12.dp`), выровненные по высоте (`Modifier.height(IntrinsicSize.Max)`).
   - Плитку «Текущий баланс» (`onBalanceClick`) сделать на всю ширину (`Modifier.fillMaxWidth().padding(horizontal = 16.dp)`) с `shape = MaterialTheme.shapes.medium`, `color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)` — строка: слева подпись `overview_balance`, справа `MoneyText(..., deemphasizeFraction = true)`.
   - Убрать грубый `HorizontalDivider()` в самом низу `MonthOverviewHeader`.
4. **Контейнерная группировка операций по дням и редизайн строки (`TransactionsScreen.kt`):**
   - Вынести `groupItemShape(index: Int, size: Int, outer: Dp = 20.dp, inner: Dp = 4.dp)` в `ui/common/GroupShape.kt` и переиспользовать его в `AccountsScreen.kt` и `TransactionsScreen.kt`.
   - В `TransactionsScreen`:
     - `DayHeader(section)`: убрать сплошную серую заливку от края до края; сделать стильную плашку с `surface.copy(alpha = 0.95f)` и отступами `padding(horizontal = 20.dp, vertical = 8.dp)`, дата — `titleSmall` (`FontWeight.SemiBold`), дневной итог справа — `MoneyText` со стилем `labelLarge` и цветом `onSurfaceVariant`.
     - Строки внутри дня (`itemsIndexed(section.rows)`): убрать полноширинный `HorizontalDivider()`. Каждую строку `SwipeableTxnRow` рендерить с `padding(horizontal = 12.dp, vertical = 1.dp)` и `clip(groupItemShape(index, section.rows.size))`, фон строки — `MaterialTheme.colorScheme.surfaceContainerLow`.
   - В `TxnRowItem`:
     - Увеличить бейдж иконки категории с `28.dp` до `42.dp`, форма — `RoundedCornerShape(14.dp)` (squircle), фон — `dotColor.copy(alpha = 0.16f)`, иконка внутри — `size = 20.dp` в полный цвет категории `dotColor` (с проверкой контраста или `dotColor` на тональной подложке). Для переводов (`row.isTransfer`) показывать такой же бейдж `42.dp` с иконкой `Icons.Outlined.SwapHoriz` в `secondaryContainer`.
     - Маркер цвета счёта (`row.accountColor`) сделать аккуратным индикатором `10.dp` с обводкой `2.dp` цвета фона карточки в правом нижнем углу бейджа.
     - Заголовок категории — `bodyLarge` (`FontWeight.Medium`), сумма справа — `MoneyText` с `style = MaterialTheme.typography.titleMedium` и `deemphasizeFraction = true`.

**Критерий приёмки M-2:** все тесты `TransactionsViewModelTest`, `FabVisibilityTest`, `TxnEmptyKindTest` и инструментальные `TxnFilterSheetInstrumentedTest`, `PlanningEntryInstrumentedTest` проходят.

---

## Этап M-3: Экран ввода операции (`TxnEntryScreen`) — единая капсула суммы/заметки и живая клавиатура

**Файлы:**
- `app/src/main/kotlin/com/corriente/app/ui/txnentry/TxnEntryScreen.kt`

### Задачи:
1. **Современный блок суммы и заметки (`AmountBar` + поле `Note`):**
   - Убрать старомодную нижнюю черту (`TextField` с подчёркиванием) у поля заметки (`R.string.txn_entry_note`).
   - Оформить поле заметки как мягкую тональную капсулу `Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow)` сразу под `AmountBar` (или объединённым блоком), где `TextField` имеет `focusedIndicatorColor = Color.Transparent`, `unfocusedIndicatorColor = Color.Transparent`, `disabledIndicatorColor = Color.Transparent`.
   - Внутри `AmountBar`:
     - Таблетку выбора счёта слева сделать более заметной (`Surface` с `shape = RoundedCornerShape(16.dp)`, `color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)`), показывать иконку кошелька и код валюты.
     - Шрифт суммы сделать `fontWeight = FontWeight.SemiBold`, `fontFeatureSettings = "tnum"`, добавить авто-уменьшение размера шрифта (`40.sp` → `32.sp` → `26.sp`) при длине строки `> 9` и `> 13` символов, чтобы длинные выражения калькулятора (`12 500 + 3 400`) никогда не обрезались.
2. **Морфинг клавиш калькулятора (`Keypad`):**
   - Для каждой кнопки в `Keypad` создать `val interactionSource = remember { MutableInteractionSource() }` и `val isPressed by interactionSource.collectIsPressedAsState()`.
   - Анимировать радиус скругления кнопки при нажатии (M3 Expressive shape morphing):
     - в покое: `RoundedCornerShape(24.dp)`, при нажатии (`isPressed`): `RoundedCornerShape(12.dp)`.
   - Кнопкам операторов (`+`, `−`, `×`, `÷`) задать `containerColor = MaterialTheme.colorScheme.secondaryContainer`, цифровым кнопкам — `MaterialTheme.colorScheme.surfaceContainerLow`, кнопке `=` — `MaterialTheme.colorScheme.primary`.
3. **Тональная кнопка выбора категории (`CategoryField`):**
   - Заменить `OutlinedButton` с тонкой серой рамкой на `Surface` (`onClick = onClick`, `enabled = enabled`, `shape = MaterialTheme.shapes.large`, `color = if (category != null && category.color != 0) Color(category.color).copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh`).
   - Иконку выбранной категории помещать в круг/squircle `32.dp`, а текст выводить с `fontWeight = FontWeight.Medium`.
4. **Ячейки выбора категории в шторке (`CategoryCell`):**
   - Заменить тонкую рамку `1.dp` на тональные карточки: невыбранная — `background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))`, выбранная — `background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), RoundedCornerShape(16.dp))` + рамка `2.dp` `primary`.

**Критерий приёмки M-3:** проходят `TxnEntryViewModelTest`, `TxnEntryFlowInstrumentedTest`, `PinnedTemplateInstrumentedTest`, `ArchivedAccountEditInstrumentedTest`, `CreateAccountFromEntryInstrumentedTest`.

---

## Этап M-4: Аналитика и графики (`ReportScreen`, `ReportCharts`, `AccountBalanceScreen`)

**Файлы:**
- `app/src/main/kotlin/com/corriente/app/ui/report/ReportCharts.kt`
- `app/src/main/kotlin/com/corriente/app/ui/report/ReportScreen.kt`
- `app/src/main/kotlin/com/corriente/app/ui/accountbalance/AccountBalanceScreen.kt`

### Задачи:
1. **Карточная компоновка в `ReportScreen.kt`:**
   - Обернуть сводку `Total` + `wholeCurrencyBudgetBar` + кнопку перехода в бюджеты в единую карточку `Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))`.
   - Сумма `Total` рендерится через `MoneyText` со стилем `headlineSmall` и `deemphasizeFraction = true`.
   - Убрать разделители `HorizontalDivider()` между графиками; вместо этого `MonthlyBarChart` и `CategoryDonut` рендерятся внутри собственных карточек `Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))`.
2. **Обновить `MonthlyBarChart` в `ReportCharts.kt`:**
   - Вместо острых прямоугольников `drawRect` использовать `drawRoundRect` со скруглением `CornerRadius(8.dp.toPx(), 8.dp.toPx())`.
   - Для каждого столбца сначала рисовать фоновый трек на всю высоту (`color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)`, `CornerRadius(8.dp.toPx())`), а поверх него — заполненный столбец (минимальная видимая высота для ненулевых значений — `4.dp.toPx()`).
   - Последний (или максимальный) столбец выделять полным цветом `MaterialTheme.colorScheme.primary`, остальные — `MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)`.
3. **Обновить `CategoryDonut` в `ReportCharts.kt`:**
   - Добавить `StrokeCap.Round` и небольшие зазоры между сегментами (как в `MonthOverviewHeader`), а в легенде выводить название категории и процент жирнее (`FontWeight.Medium`).
4. **Обновить `AccountBalanceLineChart` в `ReportCharts.kt`:**
   - Построить `androidx.compose.ui.graphics.Path` по точкам графика:
     - Нарисовать под линией мягкую градиентную заливку `drawPath(fillPath, brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.24f), Color.Transparent)))`.
     - Нарисовать саму линию через `drawPath(strokePath, color = lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))`.
     - На последней точке графика нарисовать акцентный кружок (`drawCircle(color = lineColor, radius = 4.dp.toPx())`).
5. **Редизайн строк категорий в `ReportScreen.kt`:**
   - В списке категорий отчёта (`items(state.rows)`) добавить:
     - Цветной индикатор-точку `12.dp` (цвет из соответствующего `state.slices` по `categoryId` или `PALETTE`).
     - Наглядную тонкую полоску доли категории (`LinearProgressIndicator(progress = { row.sharePercent / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))` — `ReportScreen.kt` уже входит в `InvariantGuards.floatAllowlist`).
     - Бейдж изменения к прошлому периоду (`row.changeText`) в маленькой тональной капсуле `Surface(shape = CircleShape, color = surfaceContainerHigh)`.

**Критерий приёмки M-4:** проходят `ChartGeometryTest`, `ReportViewModelTest`, `ReportPeriodTest`, `AccountBalanceViewModelTest`.

---

## Этап M-5: Бюджеты, Регулярные операции и Счета (`BudgetsScreen`, `RecurringScreen`, `AccountsScreen`)

**Файлы:**
- `app/src/main/kotlin/com/corriente/app/ui/budgets/BudgetsScreen.kt`
- `app/src/main/kotlin/com/corriente/app/ui/recurring/RecurringScreen.kt`
- `app/src/main/kotlin/com/corriente/app/ui/accounts/AccountsScreen.kt`
- `buildSrc/src/main/kotlin/corriente/buildsupport/InvariantGuards.kt` (при необходимости для прогресс-бара бюджетов, хотя `BudgetsScreen.kt` уже использует `row.progress.barFraction`)

### Задачи:
1. **Карточки бюджетов (`BudgetsScreen.kt`):**
   - Заменить плоские `ListItem` на выразительные карточки `Surface` (`shape = MaterialTheme.shapes.large`, `color = MaterialTheme.colorScheme.surfaceContainerLow`, `modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)`):
     - Верхний ряд: название категории (или «На всё») со стилем `titleMedium` (`FontWeight.SemiBold`) слева, лимит в месяц (`row.amountText`) справа, иконка удаления `IconButton` в правом верхнем углу (сохранить `contentDescription = stringResource(R.string.delete)`, чтобы не сломать существующие тесты).
     - Средний ряд: утолщённый прогресс-бар `LinearProgressIndicator` высотой `10.dp` со скруглёнными краями (`Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)`), цвет при перерасходе — `error`, иначе `primary`.
     - Нижний ряд: текст «Потрачено … · Осталось …» (`BudgetProgressLine`) со стилем `labelMedium`.
2. **Карточки регулярных платежей (`RecurringScreen.kt`):**
   - Сгруппировать список правил и блок ближайших операций в скруглённые карточки `Surface(color = surfaceContainerLow, shape = MaterialTheme.shapes.large)` с отступами `horizontal = 16.dp, vertical = 4.dp`, выделив дату следующего списания тональным чипом/бейджем.
3. **Полировка карточек итогов валют (`AccountsScreen.kt`):**
   - В `CurrencyTotalCard` увеличить скругление и внутренние отступы (`horizontal = 24.dp, vertical = 18.dp`), сумму выводить со стилем `headlineSmall` и `deemphasizeFraction = true`, чтобы карточки валют сверху выглядели как полноценные банковские дашборд-виджеты.

**Критерий приёмки M-5:** проходят `BudgetsViewModelTest`, `RecurringViewModelTest`, `AccountsViewModelTest`, `CurrencyCardsTest`.

---

## Этап M-6: Единый стиль на Desktop (`:desktop`) и полировка виджета (`:widget`)

**Файлы:**
- `desktop/src/main/kotlin/com/corriente/desktop/Main.kt`
- `desktop/src/main/kotlin/com/corriente/desktop/Common.kt`
- `desktop/src/main/kotlin/com/corriente/desktop/TransactionsScreen.kt`
- `desktop/src/main/kotlin/com/corriente/desktop/RingCanvas.kt`
- `widget/src/main/kotlin/com/corriente/widget/CorrienteWidget.kt`

### Задачи:
1. **Фирменная палитра и формы на Desktop (`desktop/Main.kt`, `desktop/Common.kt`):**
   - Заменить дефолтные фиолетовые `darkColorScheme()` / `lightColorScheme()` в `desktop/Main.kt` на фирменную бирюзово-синюю палитру Corriente (те же тона `#006780` / `#5DD5FC` и `surfaceContainer*`, что в `app/.../theme/Color.kt`) и `Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp))`.
   - В `desktop/TransactionsScreen.kt`:
     - Плашку баланса перевести с `RoundedCornerShape(6.dp)` на `RoundedCornerShape(16.dp)` и `surfaceContainerHigh`.
     - Кнопки «− Расход» и «+ Доход» перевести с «кислотных» `EXPENSE_COLOR`/`INCOME_COLOR` на гармоничные тональные кнопки M3 (`primary` для расхода, `secondaryContainer` для дохода).
     - В `desktop/RingCanvas.kt` добавить `StrokeCap.Round` и зазоры между сегментами кольца аналогично Android.
2. **Виджет (`CorrienteWidget.kt`):**
   - В `LargeBody` обернуть каждую кнопку быстрой категории в тональную пилюлю (`background(GlanceTheme.colors.secondaryContainer)`, `cornerRadius(12.dp)`), текст — `color = GlanceTheme.colors.onSecondaryContainer`, `fontWeight = FontWeight.Medium`, показывать до 8 символов названия категории (`category.name.take(8)`) вместо обрезки до 3 букв (`take(3)`), если нет эмодзи-иконки.

**Критерий приёмки M-6:** `./gradlew :desktop:compileKotlin :widget:assembleDebug check` проходит без ошибок и предупреждений.

---

## Финальная проверка перед сдачей

После выполнения всех 6 этапов агент обязан выполнить:

```bash
./gradlew check assembleDebug :desktop:test --no-daemon --max-workers=4
```

И убедиться, что:
1. `verifyInvariantGuards` в `:buildSrc` не выдаёт ни одного нарушения `I-1`, `I-3`, `I-20`, `I-24`, `I-25`.
2. `StringResourcesParityTest` и `ContrastTest` проходят.
3. Все unit-тесты во всех модулях (`:core:money`, `:core:data`, `:core:data-jvm`, `:app`, `:desktop`) зелёные.
4. При наличии подключённого эмулятора/устройства проходят `./gradlew :app:connectedDebugAndroidTest` и сделаны контрольные скриншоты в светлой и тёмной темах.
