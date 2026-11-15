#!/usr/bin/env python3
"""Run the real Android instrumentation APK against a local, insecure test emulator.

Uses the ADB wire protocol directly: native adb requires a writable ~/.android.
No credentials, application server, or external network service are involved.
"""
import pathlib
import os
import re
import sys
import time
from adb_shell.adb_device import AdbDeviceTcp

ROOT = pathlib.Path(__file__).resolve().parents[1]
EXPECTED_API = os.environ.get('LANGUE_TEST_API', '28')
if EXPECTED_API not in ('26', '28', '35'):
    raise SystemExit('LANGUE_TEST_API must be 26, 28 or 35')
REPORT = ROOT / 'app/build/reports/device' / ('api' + EXPECTED_API)
REPORT.mkdir(parents=True, exist_ok=True)
device = None
for attempt in range(180):
    try:
        device = AdbDeviceTcp('127.0.0.1', 5555, default_transport_timeout_s=10)
        device.connect(auth_timeout_s=5)
        if device.shell('getprop sys.boot_completed', read_timeout_s=10).strip() == '1':
            break
        device.close()
    except Exception:
        if device:
            device.close()
    if attempt % 15 == 0:
        print('Waiting for local emulator boot…', flush=True)
    time.sleep(2)
else:
    raise SystemExit('Emulator not ready on 127.0.0.1:5555; start langue-api28 first.')

def shell(command, **kwargs):
    # TCG emulation may take longer than the library's ten-second default.
    kwargs.setdefault('transport_timeout_s', 60)
    kwargs.setdefault('read_timeout_s', 60)
    return device.shell(command, **kwargs)

try:
    actual_api = shell('getprop ro.build.version.sdk').strip()
    if actual_api != EXPECTED_API:
        raise SystemExit('Wrong emulator API: expected ' + EXPECTED_API + ', actual ' + actual_api)
    print('Android version: ' + shell('getprop ro.build.version.release').strip() + ' / API ' + actual_api, flush=True)
    for setting in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
        shell('settings put global ' + setting + ' 0')
    shell('input keyevent 82')
    for apk, remote in [
        ('app/build/outputs/apk/universal/debug/app-universal-debug.apk', '/data/local/tmp/langue.apk'),
        ('app/build/outputs/apk/androidTest/universal/debug/app-universal-debug-androidTest.apk', '/data/local/tmp/langue-test.apk'),
    ]:
        source = ROOT / apk
        if not source.is_file():
            raise SystemExit('Compile the APK before running device tests: ' + str(source))
        print('Installing ' + source.name, flush=True)
        device.push(str(source), remote, read_timeout_s=120, transport_timeout_s=120)
        result = shell('pm install -r -t ' + remote, read_timeout_s=120, transport_timeout_s=120)
        print(result, flush=True)
        if 'Success' not in result:
            raise SystemExit('APK installation failed')
    print('Running Android instrumentation…', flush=True)
    chunks = []
    with (REPORT / 'instrumentation.txt').open('w') as output:
        for chunk in device.streaming_shell(
            'am instrument -w -r -e package org.langue.app org.langue.universal.test/androidx.test.runner.AndroidJUnitRunner',
            read_timeout_s=180, transport_timeout_s=180,
        ):
            chunks.append(chunk)
            output.write(chunk)
            output.flush()
            print(chunk, end='', flush=True)
    result = ''.join(chunks)
    match = re.search(r'OK \((\d+) tests?\)', result)
    if not match or int(match.group(1)) != 12 or 'FAILURES!!!' in result:
        raise SystemExit('Android device suite failed; see ' + str(REPORT / 'instrumentation.txt'))
    shell('am start -W -n org.langue.universal/org.langue.app.MainActivity', transport_timeout_s=60, read_timeout_s=60)
    for attempt in range(12):
        shell('rm -f /data/local/tmp/langue-screen.xml')
        dump = shell('uiautomator dump /data/local/tmp/langue-screen.xml', transport_timeout_s=60, read_timeout_s=60)
        if 'dumped to:' in dump:
            hierarchy = shell('cat /data/local/tmp/langue-screen.xml')
            if 'Aucun contenu disponible.' in hierarchy:
                break
        time.sleep(2)
    else:
        raise SystemExit('Empty application has not rendered after the device suite')
    (REPORT / 'empty-app.png').write_bytes(shell('screencap -p', decode=False, read_timeout_s=30))
    print('\n12 device tests passed; screenshot: ' + str(REPORT / 'empty-app.png'))
finally:
    if device:
        device.close()
