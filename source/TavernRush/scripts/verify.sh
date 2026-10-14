#!/usr/bin/env bash
# Сборка APK + unit-тесты + скриншоты. Использование: scripts/verify.sh <STAGE_ID>
set -o pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 2
if [ -f /data/tools/env.sh ]; then source /data/tools/env.sh; fi
STAGE="${1:-manual}"
if [[ ! "$STAGE" =~ ^[A-Za-z0-9_-]+$ ]]; then echo "Некорректный STAGE_ID"; exit 2; fi
OUT="${TAVERNRUSH_OUT:-$(dirname "$ROOT")/pipeline}"
mkdir -p "$OUT/logs" "$OUT/apk" screenshots
rm -f screenshots/*.png
LOG="$OUT/logs/verify_${STAGE}.log"
DIRTY=$(git status --porcelain | wc -l)
HEAD=$(git rev-parse --short HEAD)
VERSION_CODE=$(sed -nE "s/.*versionCode = ([0-9]+).*/\1/p" app/build.gradle.kts | head -1)
timeout 1500 ./gradlew assembleDebug testDebugUnitTest --console=plain > "$LOG" 2>&1
RC=$?
if [ "$(git rev-parse --short HEAD)" != "$HEAD" ]; then
  echo "HEAD изменился во время проверки" >> "$LOG"
  RC=3
fi
NOW=$(date -u +%FT%TZ)
if [ $RC -eq 0 ]; then
  N=$(ls screenshots/*.png 2>/dev/null | wc -l)
  EXPECTED=19
  [[ "$STAGE" == R02* ]] && EXPECTED=31
  [[ "$STAGE" == R03* || "$STAGE" == G* ]] && EXPECTED=60
  [[ "$STAGE" == R04* ]] && EXPECTED=95
  [[ "$STAGE" == R05* ]] && EXPECTED=119
  [[ "$STAGE" == R06* ]] && EXPECTED=148
  [[ "$STAGE" == R07* ]] && EXPECTED=166
  [[ "$STAGE" == R08* ]] && EXPECTED=174
  [[ "$STAGE" == R09* ]] && EXPECTED=185
  [[ "$STAGE" == R10* ]] && EXPECTED=205
  [[ "$STAGE" == R11* ]] && EXPECTED=230
  [[ "$STAGE" == R12* ]] && EXPECTED=263
  [[ "$STAGE" == R13* ]] && EXPECTED=272
  [[ "$STAGE" == R14* ]] && EXPECTED=278
  [[ "$STAGE" == R15* ]] && EXPECTED=290
  [[ "$STAGE" == R16* ]] && EXPECTED=302
  [[ "$STAGE" == R17* ]] && EXPECTED=326
  [[ "$STAGE" == R18* ]] && EXPECTED=345
  if [ "$N" -lt "$EXPECTED" ]; then
    echo "FAIL $STAGE $HEAD $NOW screenshots=$N expected>=$EXPECTED" > "$OUT/last_verify.txt"
    cat "$OUT/last_verify.txt"
    exit 4
  fi
  if [[ "$STAGE" == R05* || "$STAGE" == R06* || "$STAGE" == R07* || "$STAGE" == R08* || "$STAGE" == R09* || "$STAGE" == R10* || "$STAGE" == R11* || "$STAGE" == R12* || "$STAGE" == R13* || "$STAGE" == R14* || "$STAGE" == R15* || "$STAGE" == R16* || "$STAGE" == R17* || "$STAGE" == R18* ]]; then
    python3 - "$STAGE" <<'CHECK_TESTS' >> "$LOG" 2>&1
import pathlib, sys, xml.etree.ElementTree as E
rows=[E.parse(f).getroot() for f in pathlib.Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
sums={k:sum(int(r.attrib.get(k,0)) for r in rows) for k in ['tests','failures','errors','skipped']}
print('REGRESSION XML CHECK:',sums)
assert sums['tests'] >= (780 if sys.argv[1].startswith("R18") else 747 if sys.argv[1].startswith("R17") else 719 if sys.argv[1].startswith("R16") else 691 if sys.argv[1].startswith("R15") else 666 if sys.argv[1].startswith("R14") else 648 if sys.argv[1].startswith('R13') else 622 if sys.argv[1].startswith('R12') else 568 if sys.argv[1].startswith('R11') else 524 if sys.argv[1].startswith('R10') else 456 if sys.argv[1].startswith('R09') else 408 if sys.argv[1].startswith('R08') else 368 if sys.argv[1].startswith('R07') else 310 if sys.argv[1].startswith('R06') else 235) and sums['failures']==sums['errors']==sums['skipped']==0, sums
CHECK_TESTS
    if [ $? -ne 0 ]; then
      echo "FAIL $STAGE $HEAD $NOW invalid_test_summary" > "$OUT/last_verify.txt"
      cat "$OUT/last_verify.txt"; exit 5
    fi
  fi
  SOURCE_ONLY=0
  if [ "${VERSION_CODE:-0}" -ge 1014 ]; then
    if [ -f "${TAVERNRUSH_BASELINE_APK:-}" ] && python3 scripts/check_update_signature.py "$TAVERNRUSH_BASELINE_APK" app/build/outputs/apk/debug/app-debug.apk >> "$LOG" 2>&1; then
      cp app/build/outputs/apk/debug/app-debug.apk "$OUT/apk/tavernrush-${STAGE}.apk"
    else
      SOURCE_ONLY=1
      mkdir -p "$OUT/local_candidates"
      cp app/build/outputs/apk/debug/app-debug.apk "$OUT/local_candidates/not-for-update-${STAGE}.apk"
      echo "RELEASE BLOCKED: source checkpoint only; generated APK is not distributed as a compatible update" >> "$LOG"
    fi
  else
    cp app/build/outputs/apk/debug/app-debug.apk "$OUT/apk/tavernrush-${STAGE}.apk"
  fi
  if [ "$N" -gt 0 ]; then
    python3 - "$OUT/logs/contact_${STAGE}.png" <<'CONTACT'
from PIL import Image, ImageOps
from pathlib import Path
import sys, math
files=sorted(Path('screenshots').glob('*.png'))
canvas=Image.new('RGB',(6*278,math.ceil(len(files)/6)*593),'#111111')
for i,f in enumerate(files):
    with Image.open(f) as im:
        im.thumbnail((270,585))
        canvas.paste(im.convert('RGB'),((i%6)*278+4,(i//6)*593+4))
canvas.save(sys.argv[1])
CONTACT
  fi
  STATUS="OK"; [ "$SOURCE_ONLY" -eq 1 ] && STATUS="OK_SOURCE_ONLY"
  if [ "$DIRTY" -gt 0 ]; then STATUS="OK_DIRTY"; [ "$SOURCE_ONLY" -eq 1 ] && STATUS="OK_DIRTY_SOURCE_ONLY"; fi
  echo "$STATUS $STAGE $HEAD $NOW screenshots=$N uncommitted=$DIRTY" > "$OUT/last_verify.txt"
else
  echo "FAIL $STAGE $HEAD $NOW rc=$RC" > "$OUT/last_verify.txt"
  grep -E "^e: |error:|FAILED|What went wrong|Exception" -A6 "$LOG" | head -100
fi
grep -E "tests completed|BUILD (SUCCESSFUL|FAILED)" "$LOG" | tail -3
cat "$OUT/last_verify.txt"
[ "$RC" -eq 0 ] && [ "$DIRTY" -gt 0 ] && echo "ВНИМАНИЕ: есть незакоммиченные изменения — закоммитьте и перезапустите verify."
exit $RC
