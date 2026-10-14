#!/usr/bin/env bash
# Проверка окружения для сборки Tavern Rush. Ничего не меняет и не скачивает.
# Печатает [ OK ] / [WARN] / [FAIL] и что поставить. Код выхода 1, если не хватает обязательного.
# Подробности — docs/SETUP.md.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FAIL=0
ok()   { printf '[ OK ] %s\n' "$*"; }
warn() { printf '[WARN] %s\n' "$*"; }
fail() { printf '[FAIL] %s\n' "$*"; FAIL=1; }
hint() { printf '       %s\n' "$*"; }

echo "Проверка окружения Tavern Rush (корень: $ROOT)"
echo

# ---- JDK
if command -v java >/dev/null 2>&1; then
  ver=$(java -version 2>&1 | head -1 | sed -E 's/^[^"]*"([^"]+)".*/\1/')
  major=${ver%%.*}
  [ "$major" = "1" ] && major=$(printf '%s' "$ver" | cut -d. -f2)
  case "$major" in
    17) ok "JDK $ver" ;;
    ''|*[!0-9]*) warn "не удалось определить версию JDK ($ver); нужен 17" ;;
    *) if [ "$major" -gt 17 ]; then
         warn "JDK $ver: проект рассчитан на 17 (AGP 8.5.2, Kotlin 1.9.24, jvmTarget 17); новее может работать, но не проверялось"
         hint "лучше поставить JDK 17 (docs/SETUP.md §1)"
       else
         fail "JDK $ver слишком старый: нужен 17"
         hint "поставьте Temurin 17: https://adoptium.net/"
       fi ;;
  esac
  if [ -n "${JAVA_HOME:-}" ] && [ ! -x "$JAVA_HOME/bin/java" ]; then
    warn "JAVA_HOME=$JAVA_HOME не указывает на JDK (нет bin/java)"
  fi
else
  fail "java не найден"
  hint "поставьте JDK 17: Ubuntu — sudo apt install openjdk-17-jdk; macOS — brew install --cask temurin@17"
fi

# ---- Android SDK
sdk=""; how=""
for c in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
  if [ -n "$c" ] && [ -d "$c" ]; then sdk="$c"; how="переменная окружения"; break; fi
done
if [ -z "$sdk" ] && [ -f "$ROOT/local.properties" ]; then
  d=$(grep -E '^sdk\.dir=' "$ROOT/local.properties" | head -1 | cut -d= -f2- | sed 's#\\:#:#g; s#\\\\#/#g')
  if [ -n "$d" ] && [ -d "$d" ]; then sdk="$d"; how="local.properties"; fi
fi
guess=""
if [ -z "$sdk" ]; then
  for c in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" /opt/android-sdk /usr/lib/android-sdk; do
    if [ -d "$c" ]; then guess="$c"; break; fi
  done
fi
if [ -n "$sdk" ]; then
  ok "Android SDK: $sdk ($how)"
elif [ -n "$guess" ]; then
  fail "Android SDK найден в $guess, но Gradle его не увидит: не задан ANDROID_HOME и нет local.properties"
  hint "export ANDROID_HOME=\"$guess\"   либо создайте local.properties со строкой sdk.dir=$guess"
  sdk="$guess"
else
  fail "Android SDK не найден"
  hint "поставьте Android Studio или Command line tools (docs/SETUP.md §1) и задайте ANDROID_HOME"
fi
if [ -n "$sdk" ]; then
  if [ -d "$sdk/platforms/android-34" ]; then ok "platform android-34"
  else fail "нет $sdk/platforms/android-34"; hint "sdkmanager \"platforms;android-34\""; fi
  if [ -d "$sdk/build-tools/34.0.0" ]; then ok "build-tools 34.0.0"
  else fail "нет $sdk/build-tools/34.0.0"; hint "sdkmanager \"build-tools;34.0.0\""; fi
  if [ -x "$sdk/platform-tools/adb" ] || command -v adb >/dev/null 2>&1; then ok "adb (platform-tools)"
  else warn "adb не найден — установить APK на телефон через adb не получится"; hint "sdkmanager \"platform-tools\""; fi
fi

# ---- Gradle wrapper
if [ -f "$ROOT/gradlew" ]; then
  [ -x "$ROOT/gradlew" ] && ok "gradlew исполняемый" || { warn "gradlew не исполняемый"; hint "chmod +x gradlew scripts/*.sh"; }
else fail "нет gradlew — это не корень проекта?"; fi
if [ -f "$ROOT/gradle/wrapper/gradle-wrapper.jar" ]; then ok "gradle-wrapper.jar"; else fail "нет gradle/wrapper/gradle-wrapper.jar"; fi
if [ -f "$ROOT/gradle/wrapper/gradle-wrapper.properties" ]; then
  url=$(grep '^distributionUrl' "$ROOT/gradle/wrapper/gradle-wrapper.properties" | sed 's#.*/##; s#\\##g')
  ok "Gradle ставить не нужно: wrapper скачает $url"
fi

# ---- Git
if command -v git >/dev/null 2>&1; then
  ok "$(git --version)"
  if git -C "$ROOT" rev-parse --git-dir >/dev/null 2>&1; then
    br=$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null)
    dirty=$(git -C "$ROOT" status --porcelain | wc -l | tr -d ' ')
    ok "git-репозиторий: ветка $br, незакоммиченных изменений: $dirty"
  else
    warn "это не git-репозиторий: scripts/verify.sh работает только в репозитории"
    hint "git clone git/TavernRush.gitbundle TavernRush"
  fi
else
  fail "git не найден"
fi

# ---- Необязательное
if command -v magick >/dev/null 2>&1; then ok "ImageMagick 7 (magick): контактные листы verify.sh будут создаваться"
elif command -v montage >/dev/null 2>&1; then warn "есть ImageMagick 6 (montage), но нет magick: verify.sh не создаст листы; вручную: montage a.png b.png -tile 4x -geometry x900+3+3 out.jpg"
else warn "ImageMagick не найден — контактные листы кадров не создаются (на проверку не влияет)"; fi
if command -v python3 >/dev/null 2>&1; then ok "python3 $(python3 --version 2>&1 | cut -d' ' -f2)"; else warn "python3 не найден — не будут работать gen_mechanics_coverage.py и pack_handoff.sh"; fi

# ---- Ресурсы и сеть
mem=""
if [ -r /proc/meminfo ]; then mem=$(awk '/MemTotal/ {printf "%d", $2/1048576}' /proc/meminfo)
elif command -v sysctl >/dev/null 2>&1; then mem=$(sysctl -n hw.memsize 2>/dev/null | awk '{printf "%d", $1/1073741824}'); fi
if [ -n "$mem" ]; then
  if [ "$mem" -ge 4 ]; then ok "ОЗУ ≈ ${mem} ГБ"; else warn "ОЗУ ≈ ${mem} ГБ: Gradle настроен на -Xmx2048m, возможны сбои"; fi
fi
if command -v curl >/dev/null 2>&1; then
  if curl -fsS -m 6 -o /dev/null -I https://repo.maven.apache.org/maven2/ 2>/dev/null; then ok "Maven Central доступен (первая сборка скачает зависимости)"
  else warn "Maven Central недоступен: первая сборка и тесты (Robolectric) не смогут скачать зависимости"; fi
fi

echo
if [ "$FAIL" -eq 0 ]; then
  echo "Итог: обязательное на месте. Дальше: ./gradlew assembleDebug testDebugUnitTest (ожидается 208 тестов)."
else
  echo "Итог: не хватает обязательного — исправьте [FAIL] выше (docs/SETUP.md)."
fi
exit "$FAIL"
