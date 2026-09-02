# R6.1 (ROADMAP.md §8): правила R8 для релизной сборки.
#
# Общие правила Android/Compose/AndroidX уже приходят как consumer-rules из самих
# библиотек (AAR) — здесь только то, что специфично для этого приложения.

# --- kotlinx-serialization (BackupPayload и другие @Serializable модели, core/data) ---
# Официальная рекомендация библиотеки (kotlinx.serialization README, раздел Android/R8):
# без этого блока R8 может удалить сгенерированный $$serializer или Companion у классов
# из core/data/src/main/kotlin/com/corriente/data/backup/BackupPayload.kt, и чтение/запись
# бэкапа сломается тихо (десериализация упадёт с исключением на реальном устройстве, но
# не в unit-тестах — они гоняют JVM без R8).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    <fields>;
}

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <1>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class **
-keepclasseswithmembers class <1>$$serializer {
    <1>$$serializer INSTANCE;
    *** serialize(...);
    *** deserialize(...);
}

# --- Room (core/data/src/main/kotlin/com/corriente/data/db) ---
# Дополнительных правил не требуется: с KSP (а не kapt-рефлексией) Room генерирует
# `_Impl`-классы с прямыми вызовами конструкторов сущностей и DAO — R8 видит эти ссылки
# при обычном анализе достижимости, ничего не обращается к сущностям по имени класса
# через рефлексию. Строка оставлена как документация решения (проверялось чтением
# сгенерированного кода Room 2.8.4, не билдом — ./gradlew недоступен в песочнице).

# --- androidx.work (AutoBackupWorker, RecurrenceWorker, WidgetMidnightWorker) ---
# WorkManager инстанцирует ListenableWorker по имени класса через рефлексию (хранит имя
# в собственной БД и поднимает Worker при следующем запуске) — без явного keep R8 может
# переименовать класс или вырезать пустой конструктор, и фоновые задачи (автобэкап,
# повторяющиеся операции, полуночный пересчёт виджета) перестанут запускаться на реальном
# устройстве без единой ошибки в логах сборки.
-keep public class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
