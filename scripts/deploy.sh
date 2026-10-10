#!/usr/bin/env bash
# Сборка debug и установка на телефон по Wi-Fi (adb «Беспроводная отладка»). Данные приложения сохраняются.
#
#   scripts/deploy.sh pair  <ip:порт_сопряжения> <код>   # один раз
#   scripts/deploy.sh connect <ip:порт>                  # если телефон не нашёлся сам (порт меняется после перезагрузки)
#   scripts/deploy.sh                                    # собрать, установить, запустить
#
# Устанавливается debug-сборка с этого компьютера: подпись debug-ключа та же, поэтому это именно обновление
# поверх прежней. Если на телефоне стоит сборка с другой подписью, Android откажет — сначала экспорт в JSON.
set -euo pipefail
cd "$(dirname "$0")/.."

ADB="${ADB:-$(command -v adb || true)}"
[ -x "${ADB:-}" ] || ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
[ -x "$ADB" ] || { echo "adb не найден: поставьте platform-tools или задайте ADB=/путь/к/adb" >&2; exit 1; }

PKG=com.corriente.app
SAVED=scripts/.device   # последний адрес для connect; в .gitignore

case "${1:-deploy}" in
  pair)
    [ $# -eq 3 ] || { echo "usage: $0 pair <ip:порт> <код>" >&2; exit 2; }
    "$ADB" pair "$2" "$3"
    echo "Готово. Теперь возьмите IP:порт с главного экрана «Беспроводной отладки» и выполните: $0 connect <ip:порт>"
    exit 0 ;;
  connect)
    [ $# -eq 2 ] || { echo "usage: $0 connect <ip:порт>" >&2; exit 2; }
    "$ADB" connect "$2"
    echo "$2" > "$SAVED"
    exit 0 ;;
  deploy) ;;
  *) echo "usage: $0 [pair <ip:порт> <код> | connect <ip:порт>]" >&2; exit 2 ;;
esac

have_device() { "$ADB" devices | awk 'NR>1 && $2=="device"' | grep -q .; }

if ! have_device; then
  # сопряжённый телефон обычно подхватывается сам через mDNS — даём пару секунд
  sleep 3
fi
if ! have_device && [ -f "$SAVED" ]; then
  "$ADB" connect "$(cat "$SAVED")" >/dev/null || true
fi
if ! have_device; then
  cat >&2 <<MSG
Телефон не найден. Проверьте: телефон и компьютер в одной Wi-Fi сети, «Беспроводная отладка» включена.
Порт подключения меняется при каждом включении отладки — возьмите актуальный и выполните:
  $0 connect <ip:порт>
Если телефон ещё не сопряжён:  $0 pair <ip:порт_сопряжения> <код>
MSG
  exit 1
fi

# Один телефон может быть виден дважды (по IP и через mDNS) — берём одно устройство, иначе adb/Gradle ругаются.
# Своё можно задать заранее: ANDROID_SERIAL=... scripts/deploy.sh
if [ -z "${ANDROID_SERIAL:-}" ]; then
  # реальный телефон важнее запущенного эмулятора
  export ANDROID_SERIAL="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}' | sort -t- -k1,1 | grep -v '^emulator-' | head -1)"
  [ -n "$ANDROID_SERIAL" ] || export ANDROID_SERIAL="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}' | head -1)"
fi
echo "Устройство: $ANDROID_SERIAL"

./gradlew :app:installDebug
"$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null
echo "Установлено и запущено."
