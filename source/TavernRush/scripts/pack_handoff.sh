#!/usr/bin/env bash
# Собирает пакет передачи проекта одним zip: чистое дерево исходников (HEAD), полная история git (bundle),
# APK, документы, присланные заказчиком файлы, кадры (JPEG), контрольные суммы и MANIFEST.
#
# Использование (из корня проекта; в пакет попадает только закоммиченное):
#   scripts/pack_handoff.sh [ИМЯ] [КАТАЛОГ_ВЫВОДА]
#     ИМЯ              имя пакета и корневой папки внутри zip (по умолчанию TavernRush-HANDOFF)
#     КАТАЛОГ_ВЫВОДА   куда положить zip (по умолчанию ./out — каталог в .gitignore)
# Переменные окружения (необязательные):
#   APK_DIRS     каталоги с *.apk через «:» (по умолчанию ./out:$TAVERNRUSH_OUT/apk)
#   ATTACH_DIR   каталог с присланными заказчиком файлами (рекурсивно) → original_attachments/
#   SHOTS_DIR    каталог с PNG-кадрами (по умолчанию ./screenshots)
#   SHEETS_DIR   каталог с готовыми JPEG-листами (по умолчанию ./out)
#   JPEG_Q       качество JPEG кадров 1–100 (по умолчанию 75)
#   SHOT_RESIZE  масштаб кадров для ImageMagick (по умолчанию 50% — пиксель-арт, увеличенный в 3 раза, плохо
#                сжимается; полные PNG легко получить тестами)
#   NOTE         строка в MANIFEST.md (например, на каком коммите собраны APK)
# Нужны: git, tar, python3 (или zip). Для JPEG-кадров — ImageMagick (convert или magick), иначе кадры копируются как PNG.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

NAME="${1:-TavernRush-HANDOFF}"
OUTDIR="${2:-$ROOT/out}"
mkdir -p "$OUTDIR"; OUTDIR="$(cd "$OUTDIR" && pwd)"

say() { printf '%s\n' "$*"; }
die() { printf 'ОШИБКА: %s\n' "$*" >&2; exit 1; }
sha() { if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }
human() { du -h "$1" 2>/dev/null | cut -f1; }

command -v git >/dev/null 2>&1 || die "нужен git"
git rev-parse --git-dir >/dev/null 2>&1 || die "не git-репозиторий"
DIRTY=$(git status --porcelain | wc -l | tr -d ' ')
[ "$DIRTY" = "0" ] || say "ВНИМАНИЕ: $DIRTY незакоммиченных изменений — в пакет попадёт только закоммиченное (HEAD)."
HEAD_SHA=$(git rev-parse --short HEAD); HEAD_FULL=$(git rev-parse HEAD)
BRANCH=$(git rev-parse --abbrev-ref HEAD)
HEAD_SUBJ=$(git log -1 --format=%s)
DATE_UTC=$(date -u +%FT%TZ)
VNAME=$(grep -E 'versionName' app/build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
VCODE=$(grep -E 'versionCode' app/build.gradle.kts | head -1 | sed -E 's/[^0-9]*([0-9]+).*/\1/')
NCOMMITS=$(git rev-list --count HEAD)
# New source checkpoints must never silently ship a freshly generated incompatible key.
if [ "$VCODE" -ge 1014 ] && [ "${SOURCE_ONLY:-0}" != "1" ]; then
  [ -f "${TAVERNRUSH_BASELINE_APK:-}" ] || die "нужен проверенный baseline APK для сверки подписи; для исходников укажите SOURCE_ONLY=1"
fi

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
STAGE="$TMP/$NAME"
mkdir -p "$STAGE"/{source,git,apk,original_attachments,evidence,screenshots/frames,screenshots/sheets}

# ---- 1. Исходники (чистое дерево HEAD) и история
git archive --format=tar --prefix=TavernRush/ HEAD | tar -x -C "$STAGE/source"
git bundle create "$STAGE/git/TavernRush.gitbundle" HEAD --branches --tags >/dev/null 2>&1
git bundle verify "$STAGE/git/TavernRush.gitbundle" >/dev/null 2>&1 || die "git bundle не прошёл проверку"

# ---- 2. Документы верхнего уровня (копии из HEAD)
SRC="$STAGE/source/TavernRush"
cpdoc() { if [ -f "$SRC/$1" ]; then cp "$SRC/$1" "$STAGE/$2"; else say "нет файла $1 — пропущен"; fi; }
cpdoc PIPELINE/handoff/START_HERE.md   00_START_HERE.md
cpdoc PIPELINE/handoff/START_PROMPT.md 01_START_PROMPT.md
cpdoc docs/SETUP.md                    02_SETUP.md
cpdoc docs/ONBOARDING.md               03_ONBOARDING.md
cpdoc docs/ROADMAP.md                  04_ROADMAP.md
cpdoc docs/MECHANICS_COVERAGE.md       05_MECHANICS_COVERAGE.md
cpdoc docs/CODE_MAP.md                 06_CODE_MAP.md
cpdoc docs/STATUS_R18.md                07_STATUS_R18.md
cpdoc docs/COVERAGE_R18.md              08_COVERAGE_R18.md
cpdoc PIPELINE/QA_R18.md                09_QA_R18.md
cpdoc docs/REVIEW_R06.md                10_REVIEW_R06.md
cpdoc docs/SCOPE_REGISTER.md             11_SCOPE_REGISTER.md
cpdoc docs/SCOPE_REGISTER.json           12_SCOPE_REGISTER.json
cpdoc docs/SOURCE_INDEX_R18.json          13_SOURCE_INDEX_R18.json
cpdoc docs/specs/TZ_WORLD_FIRST_R17.md 14_TZ_WORLD_FIRST_R17.md
cpdoc docs/specs/WEAPONS_40_NEW.md 15_WEAPONS_40_NEW.md
cpdoc docs/specs/DUNGEON_WATER_CONDUIT_R18.md 16_DUNGEON_WATER_CONDUIT_R18.md
cpdoc docs/SCREENSHOT_INDEX.md         screenshots/INDEX.md

# ---- 3. APK (без дублей по хешу)
SEEN=" "
IFS=':' read -r -a APKD <<< "${APK_DIRS:-$ROOT/out:${TAVERNRUSH_OUT:-$ROOT/../pipeline}/apk}"
[ "${SOURCE_ONLY:-0}" = "1" ] && APKD=()
for d in "${APKD[@]}"; do
  [ -d "$d" ] || continue
  for f in "$d"/*.apk; do
    [ -f "$f" ] || continue
    if [ "$VCODE" -ge 1014 ]; then
      python3 "$ROOT/scripts/check_update_signature.py" "$TAVERNRUSH_BASELINE_APK" "$f" || die "APK не является допустимым обновлением; упаковка отменена"
    fi
    h=$(sha "$f"); case "$SEEN" in *" $h "*) continue ;; esac
    SEEN="$SEEN$h "; cp "$f" "$STAGE/apk/$(basename "$f")"
  done
done

# ---- 4. Присланные заказчиком файлы (имена приводятся к ASCII, оригиналы — в README)
ATT_ROWS=""
if [ -n "${ATTACH_DIR:-}" ] && [ -d "$ATTACH_DIR" ]; then
  while IFS= read -r -d '' f; do
    base=$(basename "$f"); h=$(sha "$f")
    case "$SEEN" in *" $h "*) continue ;; esac
    SEEN="$SEEN$h "
    nonascii=$(LC_ALL=C; case "$base" in *[!A-Za-z0-9._-]*) echo 1 ;; esac)
    if [ -n "$nonascii" ]; then
      ext="${base##*.}"; stored="attachment-${h:0:8}.$ext"
    else stored="$base"; fi
    cp "$f" "$STAGE/original_attachments/$stored"
    ATT_ROWS="$ATT_ROWS| \`$stored\` | $base | $(wc -c < "$f" | tr -d ' ') | \`$h\` |
"
  done < <(find "$ATTACH_DIR" -type f -print0 | sort -z)
fi
{
  echo "# original_attachments — файлы, присланные заказчиком (без изменений)"
  echo
  echo "| Имя в архиве | Исходное имя | Байт | sha256 |"
  echo "|---|---|---|---|"
  printf '%s' "$ATT_ROWS"
  echo
  if [ -z "$ATT_ROWS" ]; then echo "В этой среде оригинальные бинарные вложения недоступны и не включены. Импортированные документы/исходники и история сохранены в source/git; это не копии всех присланных APK/ZIP."; echo; fi
  echo "Историческая расшифровка имён: \`TavernRush-R01.apk\`, \`TavernRush-R02.apk\` — APK R01 и R02 (пакет \`.preview\`, другой отладочный ключ, чем у сборок из \`apk/\`); \`TavernRush-source-R02.zip\` — исходники R02 (импортированы в коммит bd8beab); \`tavernrush-recovered-source-checkpoint.tar.gz\` — восстановленный срез (baseline); \`Pixel-RPG-Integration-v2.zip/.pdf\` и пакет дизайна v3 — распакованы в \`source/TavernRush/PIPELINE/design_pixel_rpg_v2/\` и \`design_v3/\`."
} > "$STAGE/original_attachments/README.md"

# ---- 5. README по APK
AAPT=""; APKSIGNER=""
SDKD="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -n "$SDKD" ]; then
  AAPT=$(ls "$SDKD"/build-tools/*/aapt2 2>/dev/null | tail -1 || true)
  APKSIGNER=$(ls "$SDKD"/build-tools/*/apksigner 2>/dev/null | tail -1 || true)
fi
{
  echo "# apk — сборки и присланные APK"
  echo
  echo "| Файл | Версия | Пакет | Подпись (SHA-256 сертификата, 16 знаков) | Байт | sha256 |"
  echo "|---|---|---|---|---|---|"
  for f in "$STAGE"/apk/*.apk "$STAGE"/original_attachments/*.apk; do
    [ -f "$f" ] || continue
    info="—|—"
    if [ -n "$AAPT" ]; then
      line=$("$AAPT" dump badging "$f" 2>/dev/null | head -1 || true)
      pk=$(printf '%s' "$line" | sed -nE "s/^package: name='([^']+)'.*/\1/p"); vn=$(printf '%s' "$line" | sed -nE "s/.*versionName='([^']+)'.*/\1/p"); vc=$(printf '%s' "$line" | sed -nE "s/.*versionCode='([^']+)'.*/\1/p")
      [ -n "$pk" ] && info="$vn ($vc)|$pk"
    fi
    sig="—"
    if [ -n "$APKSIGNER" ]; then
      sg=$("$APKSIGNER" verify --print-certs "$f" 2>/dev/null | sed -nE 's/.*certificate SHA-256 digest: ([0-9a-f]+).*/\1/p' | head -1 || true)
      [ -n "$sg" ] && sig="\`${sg:0:16}\`"
    fi
    rel="${f#$STAGE/}"
    echo "| \`$rel\` | ${info%%|*} | ${info##*|} | $sig | $(wc -c < "$f" | tr -d ' ') | \`$(sha "$f")\` |"
  done
  echo
  if [ "${SOURCE_ONLY:-0}" = "1" ]; then
    echo "Это контрольная точка исходников $VNAME / $VCODE, без APK. Последний переданный совместимый установщик R13. Приватный ключ R06–R13 в этой среде отсутствует; не удалять установленную игру. Сборка тестов не является проверкой установки."
  else
    echo "Пакет/версия/сертификат каждого APK приведены выше. Для versionCode >=1014 упаковка сверяет подпись и увеличение версии с TAVERNRUSH_BASELINE_APK. Физическая установка не проверена. Не удалять старое приложение ради несовместимой подписи."
  fi
} > "$STAGE/apk/README.md"

# ---- 6. Кадры: JPEG вместо PNG, листы как есть
IM=""
if command -v magick >/dev/null 2>&1; then IM="magick"; elif command -v convert >/dev/null 2>&1; then IM="convert"; fi
SHOTS="${SHOTS_DIR:-$ROOT/screenshots}"; NSH=0
for f in "$SHOTS"/*.png; do
  [ -f "$f" ] || continue
  b=$(basename "$f" .png)
  if [ -n "$IM" ] && $IM "$f" -resize "${SHOT_RESIZE:-50%}" -quality "${JPEG_Q:-75}" "$STAGE/screenshots/frames/$b.jpg" 2>/dev/null; then :
  else cp "$f" "$STAGE/screenshots/frames/"; fi
  NSH=$((NSH+1))
done
SHEETS="${SHEETS_DIR:-$ROOT/out}"
for f in "$SHEETS"/*.jpg; do [ -f "$f" ] && cp "$f" "$STAGE/screenshots/sheets/"; done

# ---- 6b. Доказательства прогона: журналы verify и JVM-замеры
OUTV="${TAVERNRUSH_OUT:-$ROOT/../pipeline}"
for f in "$OUTV"/last_verify.txt "$OUTV"/logs/verify_*.log "$SHOTS"/preset_timing.txt "$SHOTS"/logic_timing.txt; do
  [ -f "$f" ] && cp "$f" "$STAGE/evidence/"
done
{
  echo "# evidence — следы проверок (только чтение)"
  echo
  echo "- \`last_verify.txt\`, \`verify_*.log\` — итог и полный журнал \`scripts/verify.sh\` (строки \`PASSED\` — тесты)."
  echo "- \`preset_timing.txt\`, \`logic_timing.txt\` — замеры времени кадра и шага логики **на JVM** (прокси; не FPS телефона)."
} > "$STAGE/evidence/README.md"

# ---- 7. MANIFEST
{
  echo "# MANIFEST — пакет передачи $NAME"
  echo
  echo "- Собран: $DATE_UTC"
  echo "- Исходники: ветка \`$BRANCH\`, коммит \`$HEAD_SHA\` (\`$HEAD_FULL\`) — «$HEAD_SUBJ»; коммитов в истории: $NCOMMITS"
  echo "- Версия приложения: $VNAME (versionCode $VCODE)"
  echo "- Кадров в screenshots/frames: $NSH (JPEG, ${SHOT_RESIZE:-50%} исходного разрешения; полные PNG — \`./gradlew testDebugUnitTest\`, в \`screenshots/\`)"
  LV="${TAVERNRUSH_OUT:-$ROOT/../pipeline}/last_verify.txt"
  if [ -f "$LV" ]; then echo "- Последний verify (\`last_verify.txt\`): \`$(cat "$LV")\`"; fi
  [ -n "${NOTE:-}" ] && echo "- Примечание: $NOTE"
  [ "$DIRTY" = "0" ] || echo "- ВНИМАНИЕ: при сборке пакета было $DIRTY незакоммиченных изменений (в пакет они не вошли)"
  echo
  echo "## Состав"
  echo
  echo "| Путь | Что | Размер |"
  echo "|---|---|---|"
  for p in 00_START_HERE.md 01_START_PROMPT.md 02_SETUP.md 03_ONBOARDING.md 04_ROADMAP.md 05_MECHANICS_COVERAGE.md 06_CODE_MAP.md 07_STATUS_R18.md 08_COVERAGE_R18.md 09_QA_R18.md 10_REVIEW_R06.md 11_SCOPE_REGISTER.md 12_SCOPE_REGISTER.json 13_SOURCE_INDEX_R18.json source git apk original_attachments evidence screenshots; do
    [ -e "$STAGE/$p" ] && echo "| \`$p\` | $(case $p in 0*|1[0-3]_*) echo "документ (копия из репозитория)";; source) echo "чистое дерево исходников";; git) echo "полная история git (bundle)";; apk) echo "APK и README";; original_attachments) echo "присланное заказчиком";; evidence) echo "журналы проверок и замеры";; screenshots) echo "кадры (JPEG), листы, INDEX.md";; esac) | $(human "$STAGE/$p") |"
  done
  echo
  echo "Проверка целостности: \`sha256sum -c CHECKSUMS.sha256\` (macOS: \`shasum -a 256 -c CHECKSUMS.sha256\`). Начните с \`00_START_HERE.md\`."
} > "$STAGE/MANIFEST.md"

# ---- 8. Контрольные суммы
( cd "$STAGE" && find . -type f ! -name CHECKSUMS.sha256 -print0 | sort -z | while IFS= read -r -d '' f; do printf '%s  %s\n' "$(sha "$f")" "${f#./}"; done > CHECKSUMS.sha256 )

# ---- 9. Zip (python: корректные флаги UTF-8 и права; запасной вариант — zip)
ZIP="$OUTDIR/$NAME.zip"; rm -f "$ZIP"
if command -v python3 >/dev/null 2>&1; then
  python3 - "$TMP" "$NAME" "$ZIP" <<'PY'
import os, sys, zipfile
base, name, out = sys.argv[1:4]
root = os.path.join(base, name)
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
    for d, dirs, files in os.walk(root):
        dirs.sort(); files.sort()
        for f in files:
            p = os.path.join(d, f)
            z.write(p, os.path.relpath(p, base))
PY
elif command -v zip >/dev/null 2>&1; then
  ( cd "$TMP" && zip -r -X -q "$ZIP" "$NAME" )
else die "нужен python3 или zip"
fi
if command -v unzip >/dev/null 2>&1; then unzip -tq "$ZIP" >/dev/null || die "zip повреждён"; fi

say "Готово: $ZIP"
say "  размер: $(human "$ZIP"), sha256: $(sha "$ZIP")"
say "  коммит: $HEAD_SHA, версия: $VNAME, кадров: $NSH"
