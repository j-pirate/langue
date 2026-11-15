#!/usr/bin/env bash
# Reproduce the managed cloud toolchain; stores downloads outside the Git checkout.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/activate-cloud.sh
export LANGUE_WITH_EMULATOR=0
LANGUE_BUILD_ONLY=0
for argument in "$@"; do
  case "$argument" in
    --with-emulator) export LANGUE_WITH_EMULATOR=1 ;;
    --build-only) LANGUE_BUILD_ONLY=1 ;;
    *) echo 'Usage: cloud-setup.sh [--build-only] [--with-emulator]' >&2; exit 2 ;;
  esac
done
mkdir -p "$LANGUE_TOOLS_DIR" "$ANDROID_USER_HOME" "$GRADLE_USER_HOME"
python3 - <<'PY'
import hashlib, os, pathlib, subprocess, urllib.parse, zipfile, tarfile
base=pathlib.Path(os.environ['LANGUE_TOOLS_DIR'])
artifacts=[
 ('https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz', 'jdk21.tar.gz', 'd7f5d5cf865a2902b70ff549b97586a7d3f234e016f041ab447ea740c7d66d1c'),
 ('https://downloads.gradle.org/distributions/gradle-8.9-bin.zip', 'gradle-8.9-bin.zip', 'd725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab'),
 ('https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip', 'android-cli.zip', '2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258'),
]
for url,name,checksum in artifacts:
 target=base/name
 if not target.exists(): subprocess.run(['curl','-fSL','--retry','2',url,'-o',str(target)],check=True)
 actual=hashlib.sha256(target.read_bytes()).hexdigest()
 if actual != checksum: raise SystemExit('SHA-256 mismatch: '+name)
 if name=='jdk21.tar.gz':
  if not (base/'jdk-21.0.13+9').is_dir():
   with tarfile.open(target) as archive: archive.extractall(base,filter='data')
 elif name.startswith('gradle'):
  if not (base/'gradle-8.9').is_dir(): zipfile.ZipFile(target).extractall(base)
 else:
  parent=base/'android-sdk/cmdline-tools'
  if not (parent/'latest').is_dir():
   parent.mkdir(parents=True,exist_ok=True)
   zipfile.ZipFile(target).extractall(parent)
   (parent/'cmdline-tools').rename(parent/'latest')
(base/'gradle-8.9/bin/gradle').chmod(0o755)
for path in (base/'android-sdk/cmdline-tools/latest/bin').iterdir(): path.chmod(0o755)
proxy=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',''))
if proxy.hostname:
 if proxy.username or proxy.password: raise SystemExit('Use a credential-free managed proxy endpoint for Java.')
 props=pathlib.Path(os.environ['GRADLE_USER_HOME'])/'gradle.properties'
 content=props.read_text() if props.exists() else ''
 values={'systemProp.https.proxyHost':proxy.hostname,'systemProp.https.proxyPort':str(proxy.port or 80),
         'systemProp.http.proxyHost':proxy.hostname,'systemProp.http.proxyPort':str(proxy.port or 80)}
 if pathlib.Path('/etc/ssl/certs/java/cacerts').is_file():
  values['systemProp.javax.net.ssl.trustStore']='/etc/ssl/certs/java/cacerts'
 lines=[line for line in content.splitlines() if line.split('=',1)[0] not in values]
 props.write_text('\n'.join(lines+[key+'='+value for key,value in values.items()])+'\n')
args=[str(base/'android-sdk/cmdline-tools/latest/bin/sdkmanager'),'--sdk_root='+str(base/'android-sdk')]
if proxy.hostname: args+=['--proxy=http','--proxy_host='+proxy.hostname,'--proxy_port='+str(proxy.port or 80)]
# Reuse the managed system's verified CA trust, including its HTTPS proxy CA.
if pathlib.Path('/etc/ssl/certs/java/cacerts').is_file():
 os.environ['JAVA_TOOL_OPTIONS']=os.environ.get('JAVA_TOOL_OPTIONS','')+' -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts'
# Running this setup accepts the SDK terms printed by sdkmanager.
subprocess.run(args+['--licenses'],input='y\n'*100,text=True,check=True)
packages=['platform-tools','platforms;android-35','build-tools;34.0.0','build-tools;35.0.0']
if os.environ.get('LANGUE_WITH_EMULATOR')=='1':
 packages+=['emulator','system-images;android-28;default;x86_64']
subprocess.run(args+packages,check=True)
pathlib.Path('local.properties').write_text('sdk.dir='+str(base/'android-sdk')+'\n')
PY
if [[ "$LANGUE_WITH_EMULATOR" == 1 ]]; then
  python3 -m venv "$LANGUE_TOOLS_DIR/adb-venv"
  "$LANGUE_TOOLS_DIR/adb-venv/bin/pip" install -r scripts/device-requirements.txt
fi
if [[ "$LANGUE_BUILD_ONLY" == 1 ]]; then
  ./gradlew :engine:test :app:assembleUniversalDebug :app:compileUniversalDebugUnitTestKotlin :app:assembleUniversalDebugAndroidTest
else
  ./gradlew :engine:test :app:assembleUniversalDebug :app:testUniversalDebugUnitTest :app:lintUniversalDebug
fi
