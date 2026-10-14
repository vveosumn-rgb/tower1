> **Обновление R06:** текущий статус — `docs/STATUS_R06.md` и `PIPELINE/QA_R06.md`. Текст ниже сохраняет историческую очередь/оценку R05; не считать все старые «нет» актуальными. R06 — отдельный пакет `.preview.r06`; R04/R05 не удалять, автоматического импорта нет.

> **Обновление R05:** актуальны README.md, docs/CONTROLS.md, docs/AUDIT_R05.md и PIPELINE/QA_R05.md. Ниже сохранены сведения R04. Новый пакет com.tavernrush.game.preview.r05 устанавливается рядом; R04 не удалять. R06 — отдельная экспедиция, решения больше не требуют вопросов владельцу.

# SETUP — что установить, как собрать, проверить и поставить на телефон

Проверено: Ubuntu 24.04 (песочница: 2 CPU, 4 ГБ ОЗУ), JDK 17.0.20, Android SDK 34, Gradle 8.7 из wrapper. **На macOS, Windows и на реальном телефоне не проверялось** — такие места помечены «не проверено». Пути ниже — относительно корня проекта (папка, где лежит `gradlew`).

## 1. Что нужно поставить

| Что | Версия | Для чего | Где взять | Проверка |
|---|---|---|---|---|
| JDK | **17** (AGP 8.5.2 и Kotlin 1.9.24 настроены на jvmTarget 17) | сборка и тесты | Temurin 17: https://adoptium.net/ · Ubuntu: `sudo apt install openjdk-17-jdk` · macOS: `brew install --cask temurin@17` · Windows: установщик Temurin или JDK из Android Studio (папка `jbr`) | `java -version` → 17.x |
| Android SDK | platform **android-34**, build-tools **34.0.0**, platform-tools (adb) | сборка APK; adb — установка на телефон | Android Studio → SDK Manager, либо «Command line tools only»: https://developer.android.com/studio#command-line-tools-only | `ls $ANDROID_HOME/platforms/android-34` |
| Gradle | **ставить не нужно**: в проекте wrapper 8.7 (`./gradlew`) | всё | скачивается сам при первом запуске | `./gradlew --version` |
| Git | 2.x | клон, история | https://git-scm.com | `git --version` |
| Интернет | при первой сборке | Gradle 8.7, плагины AGP/Kotlin, Robolectric (android-all 34 и нативная графика) с Maven Central и Google. Кэш `~/.gradle` вырастает до ≈ 1,2 ГБ | — | — |
| ImageMagick | любой | **необязательно**: контактные листы кадров. `scripts/verify.sh` вызывает `magick montage`, без него молча пропускает | https://imagemagick.org | `magick -version` или `montage -version` |
| Python 3 | любой | необязательно: разовые скрипты, `scripts/gen_mechanics_coverage.py`, упаковка `scripts/pack_handoff.sh` | https://python.org | `python3 --version` |
| Телефон или эмулятор | Android 8.0+ (API 26+), портрет | запуск APK | — | — |

Ресурсы: ≥ 4 ГБ ОЗУ (в `gradle.properties` стоит `-Xmx2048m`, `org.gradle.workers.max=2`), ~3 ГБ свободного диска.

Проверить всё одной командой: `scripts/check_env.sh` (ничего не меняет, печатает [ OK ] / [WARN] / [FAIL] и что поставить).

### Как поставить Android SDK без Android Studio

```bash
export ANDROID_HOME="$HOME/Android/Sdk"            # Linux; macOS: $HOME/Library/Android/sdk
mkdir -p "$ANDROID_HOME/cmdline-tools"
# распакуйте скачанные «Command line tools» так, чтобы получилось:
#   $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

### Как сказать Gradle, где SDK

Либо переменная окружения `ANDROID_HOME`, либо файл `local.properties` в корне проекта (он в `.gitignore`):

```
sdk.dir=/полный/путь/к/Android/Sdk
# Windows: sdk.dir=C\:\\Users\\Имя\\AppData\\Local\\Android\\Sdk
```

Без этого Gradle падает с `SDK location not found` — даже если SDK лежит на диске (в песочнице именно так и было: SDK в `/opt/android-sdk`, нужен `export ANDROID_HOME=/opt/android-sdk`).

## 2. Получить код

- **Из архива передачи** (рекомендуется): `git clone git/TavernRush.gitbundle TavernRush && cd TavernRush`, затем `git checkout hoplite/epidamnos-dyrrhachion-98e05d52` (клон уже должен быть на этой ветке). Внутри полная история (13+ коммитов).
- **Из GitHub**, если есть доступ: `git clone https://github.com/meshipitowork/gurn.git`, ветка `hoplite/epidamnos-dyrrhachion-98e05d52`.
- `source/TavernRush/` в архиве — чистое дерево без истории: годится, чтобы читать и собирать, но `scripts/verify.sh` требует git-репозиторий (он записывает коммит и «чистоту» дерева).

## 3. Сборка и тесты (Linux, macOS, WSL)

```bash
scripts/check_env.sh                      # окружение
export ANDROID_HOME=...                   # если не задано (см. выше)
./gradlew assembleDebug                   # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest               # 208 тестов; кадры PNG → ./screenshots/
./gradlew assembleDebug testDebugUnitTest # всё сразу (так делает verify.sh)
./gradlew testDebugUnitTest --tests 'com.tavernrush.game.Round4Test'   # один класс
```

Ожидаемый результат на коммите, с которого собран архив: `BUILD SUCCESSFUL`, **208 тестов, 0 ошибок и пропусков**, 103 PNG в `screenshots/`, APK `com.tavernrush.game.preview` версии `0.4.0-r04` (versionCode 1004).

Время (песочница, 2 CPU): холодный первый прогон — несколько минут плюс скачивание зависимостей; повторный — 40–110 с.

Результаты: отчёт тестов — `app/build/reports/tests/testDebugUnitTest/index.html`, XML — `app/build/test-results/testDebugUnitTest/`. Каталог `screenshots/` и `out/` в `.gitignore` — это нормально.

## 4. Windows (не проверено)

- **Android Studio** (по требованию AGP 8.5 — Koala 2024.1.1 или новее; сверьтесь с таблицей совместимости в документации Android): откройте папку проекта, JDK для Gradle — встроенный 17 (Settings → Build Tools → Gradle → Gradle JDK), `local.properties` создаст сама студия. Задачи — из панели Gradle (`app → Tasks → build → assembleDebug`, `verification → testDebugUnitTest`).
- **PowerShell**: `$env:ANDROID_HOME="C:\Users\Имя\AppData\Local\Android\Sdk"; .\gradlew.bat assembleDebug testDebugUnitTest`.
- Скрипты `scripts/*.sh` — через Git Bash или WSL2.
- **Тесты с кадрами** (Robolectric, `GraphicsMode.NATIVE`) гонялись только на Linux x86_64. Если на Windows/macOS они падают с ошибкой нативной графики — запускайте на Linux (WSL2, Docker, CI).

## 5. Полная проверка этапа: `scripts/verify.sh <ЭТАП>`

```bash
export ANDROID_HOME=...                 # как выше
export TAVERNRUSH_OUT=$HOME/tr-out      # куда класть логи и APK (по умолчанию ../pipeline)
scripts/verify.sh R05-1                 # имя этапа: буквы, цифры, «_», «-»
```

Что делает: удаляет `screenshots/*.png`, запускает `./gradlew assembleDebug testDebugUnitTest` (таймаут 1500 с), проверяет, что кадров не меньше ожидаемого, копирует APK в `$TAVERNRUSH_OUT/apk/tavernrush-<ЭТАП>.apk`, пишет лог `logs/verify_<ЭТАП>.log` и итог `last_verify.txt` (`OK` — всё хорошо и дерево чистое; `OK_DIRTY` — есть незакоммиченное, повторите после коммита; `FAIL` — смотрите лог).

Ожидаемое число кадров зашито в `scripts/verify.sh` по префиксу имени этапа: `R02*` — 31, `R03*`/`G*` — 60, `R04*` — 95, остальные — 19. **Для нового этапа добавьте строку с новым числом**, иначе проверка либо не ловит потерю кадров, либо ложно проходит.

macOS: нужна команда `timeout` (`brew install coreutils` и `alias timeout=gtimeout` или поправьте скрипт). Контактные листы создаются только если есть ImageMagick 7 (`magick`).

## 6. Поставить APK на телефон или эмулятор

APK в архиве: `apk/TavernRush-R04.apk` (последний), `apk/TavernRush-R03.apk`; присланные заказчиком R01 и R02 — в `original_attachments/`. Список с версиями и sha256 — `apk/README.md`.

1. **Через adb** (удобнее всего): на телефоне включите «Параметры разработчика» → «Отладка по USB», подключите кабель, подтвердите ключ.
   ```bash
   adb devices
   adb install -r apk/TavernRush-R04.apk
   adb shell monkey -p com.tavernrush.game.preview -c android.intent.category.LAUNCHER 1   # запуск
   adb logcat | grep -i tavern                                                              # журнал
   ```
2. **Без adb**: скопируйте APK на телефон, откройте его файловым менеджером и разрешите «Установку из неизвестных источников» для этого приложения.
3. **Эмулятор**: Android Studio → Device Manager → Pixel с API 34, портрет; затем `adb install -r`.

Что нужно знать:
- Название на экране — «Tavern Rush · тест»; пакет `com.tavernrush.game.preview` (у debug-сборки суффикс `.preview`), поэтому старая игра без суффикса (`com.tavernrush.game`) не затрагивается. **Но присланные заказчиком R01 и R02 тоже `.preview`** (по `aapt2`: `0.1.1-r01`/1001 и `0.2.0-r02`/1002) и подписаны другим отладочным ключом, чем сборки R03/R04 из архива (по `apksigner`: R01 и R02 — сертификат с отпечатком SHA-256 `8d6e2546…`, R03 и R04 — `a212f9b2…`; R01→R02 обновляются друг поверх друга, R02→R03/R04 — только после удаления).
- **Подпись.** APK из архива подписаны отладочным ключом песочницы; ваша сборка — вашим `~/.android/debug.keystore` (фиксированного ключа в проекте нет, релизная подпись не настроена). Поверх сборки с другой подписью установка не пройдёт (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`): `adb uninstall com.tavernrush.game.preview` — **прогресс при этом пропадёт**, экспорта сохранений нет.
- Сохранение лежит в приватном каталоге приложения (`files/save`).
- **Не проверено:** установка R03 и R04 на реальном устройстве мной не выполнялась; проверялись пакет и версия APK по `aapt2 dump badging`. Звук, вибрация, FPS и нагрев на устройстве не измерялись.

## 7. Типичные проблемы

| Симптом | Причина и что делать |
|---|---|
| `SDK location not found` | не задан `ANDROID_HOME` и нет `local.properties` (см. §1) |
| `Unsupported class file major version` / ошибки Kotlin на запуске | не тот JDK; нужен 17 (`java -version`, `JAVA_HOME`) |
| `Permission denied: ./gradlew` | `chmod +x gradlew scripts/*.sh` (в zip права могли потеряться) |
| Gradle падает по памяти | закройте другое; в `gradle.properties` поднимите `-Xmx`; `./gradlew --stop` |
| Тесты не качают Robolectric | нужен доступ к `https://repo.maven.apache.org/maven2` (прокси — настройте Gradle) |
| Падает нативная графика в тестах | не Linux x86_64 — запускайте на Linux (§4) |
| `magick: command not found` в логе verify | только контактные листы не создаются; на статус не влияет |
| Тесты зелёные, а `verify.sh` пишет FAIL `screenshots=…` | кадров меньше ожидаемого числа для этапа — обновите/исправьте (§5) |
| `OK_DIRTY` | в дереве незакоммиченное; закоммитьте и повторите verify |
| Зависший демон | `./gradlew --stop` |

## 8. Где что лежит после сборки

| Путь | Что |
|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | debug-APK |
| `app/build/test-results/testDebugUnitTest/*.xml` | результаты тестов |
| `screenshots/*.png` | кадры из визуальных тестов (git-ignore); что есть что — `docs/SCREENSHOT_INDEX.md` |
| `$TAVERNRUSH_OUT/{logs,apk,last_verify.txt}` | результаты `scripts/verify.sh` |
| `out/` | произвольные артефакты (git-ignore) |
