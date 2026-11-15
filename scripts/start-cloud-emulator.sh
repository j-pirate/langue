#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/activate-cloud.sh
if [[ ! -x "$ANDROID_HOME/emulator/emulator" || ! -d "$ANDROID_HOME/system-images/android-28/default/x86_64" ]]; then
  echo 'Install the test emulator with: bash scripts/cloud-setup.sh --build-only --with-emulator' >&2
  exit 1
fi
if python3 - <<'PY'
import socket
try:
    with socket.create_connection(('127.0.0.1',5555),timeout=1): pass
except OSError: raise SystemExit(1)
PY
then
  echo 'Port 5555 already in use. Use run-device-tests.py to check the local test emulator.'
  exit 0
fi
mkdir -p "$ANDROID_AVD_HOME"
python3 - <<'PY'
from pathlib import Path
import os,subprocess
p=Path(os.environ['ANDROID_AVD_HOME'])/'langue-api28.avd/config.ini'
if not p.exists():
    subprocess.run([os.environ['ANDROID_HOME']+'/cmdline-tools/latest/bin/avdmanager',
                   'create','avd','-n','langue-api28','-k','system-images;android-28;default;x86_64',
                   '-d','pixel','--path',str(p.parent)],input='no\n',text=True,check=True)
    values={'hw.lcd.width':'720','hw.lcd.height':'1280','hw.lcd.density':'240',
            'hw.ramSize':'2048','hw.cpu.ncore':'2','hw.keyboard':'yes','disk.dataPartition.size':'2G'}
    lines=[l for l in p.read_text().splitlines() if l.split('=',1)[0].strip() not in values]
    p.write_text('\n'.join(lines+[k+'='+v for k,v in values.items()])+'\n')
PY
nohup emulator -avd langue-api28 -no-metrics -no-window -no-audio -no-boot-anim -no-snapshot \
  -accel off -gpu swiftshader -port 5554 > "$LANGUE_TOOLS_DIR/emulator.log" 2>&1 < /dev/null &
printf '%s\n' "$!" > "$LANGUE_TOOLS_DIR/langue-emulator.pid"
echo 'Emulator started; run-device-tests.py waits for Android boot and checks readiness.'
