#!/usr/bin/env bash
# Package an existing, signed universal Debug APK; this never publishes a Release.
set -Eeuo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
apk="$repo_root/app/build/outputs/apk/universal/debug/app-universal-debug.apk"
notes="$repo_root/RELEASE_NOTES.md"
output="$repo_root/app/build/distribution/test"
version_code=2
version_name=1.1.0
build_tools=""

usage() {
    cat <<'HELP'
Usage: scripts/package-test-apk.sh [options]

Prepare langue.apk, RELEASE_NOTES.md and SHA256SUMS.txt for a TEST release.
The APK must be universal, debuggable and signed with an Android Debug certificate.
No APK is built, no key is read and nothing is published.

  --apk PATH            Existing universal Debug APK
  --notes PATH          Test release notes (default: repository RELEASE_NOTES.md)
  --output DIR          Destination (default: app/build/distribution/test)
  --version-code N      Expected versionCode (default: 2)
  --version-name NAME   Expected versionName (default: 1.1.0)
  --build-tools DIR     Android SDK directory containing apksigner and aapt
  --help                Show this help

After downloading all three files, check them with: sha256sum -c SHA256SUMS.txt
HELP
}

fail() {
    printf 'Packaging refused: %s\n' "$*" >&2
    if [[ "${GITHUB_ACTIONS:-}" == true ]]; then
        local annotation="$*"
        annotation=${annotation//%/%25}
        annotation=${annotation//$'\r'/%0D}
        annotation=${annotation//$'\n'/%0A}
        printf '::error title=Test APK preparation refused::%s\n' "$annotation"
    fi
    exit 1
}

trap 'status=$?; if [[ "${GITHUB_ACTIONS:-}" == true ]]; then printf "::error title=Test APK preparation failed::Line %s, exit %s.\n" "$LINENO" "$status"; fi; exit "$status"' ERR

while (($#)); do
    case "$1" in
        --help|-h) usage; exit 0 ;;
        --apk|--notes|--output|--version-code|--version-name|--build-tools)
            (($# >= 2)) || fail "Missing value for $1"
            [[ -n "$2" ]] || fail "Empty value for $1"
            case "$1" in
                --apk) apk=$2 ;;
                --notes) notes=$2 ;;
                --output) output=$2 ;;
                --version-code) version_code=$2 ;;
                --version-name) version_name=$2 ;;
                --build-tools) build_tools=$2 ;;
            esac
            shift 2
            ;;
        *) fail "Unknown option: $1 (see --help)" ;;
    esac
done

[[ "$version_code" =~ ^[1-9][0-9]*$ ]] || fail 'versionCode must be a positive integer'
[[ "$version_name" != *$'\n'* && "$version_name" != *"'"* ]] || fail 'Invalid versionName'
[[ -f "$apk" && -r "$apk" ]] || fail "APK not readable: $apk"
[[ -s "$notes" && -r "$notes" ]] || fail "Release notes not readable or empty: $notes"
LC_ALL=C grep -qi 'test' "$notes" || fail 'Release notes must explicitly identify a test version'

if [[ -z "$build_tools" ]]; then
    sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
    if [[ -n "$sdk_root" && -d "$sdk_root/build-tools" ]]; then
        # Use the newest installed complete build-tools version.
        while IFS= read -r candidate; do
            if [[ -x "$candidate/apksigner" && -x "$candidate/aapt" ]]; then
                build_tools=$candidate
            fi
        done < <(printf '%s\n' "$sdk_root"/build-tools/* | sort -V)
    fi
fi
if [[ -n "$build_tools" ]]; then
    apksigner="$build_tools/apksigner"
    aapt="$build_tools/aapt"
else
    apksigner=$(command -v apksigner) || fail 'apksigner unavailable; set ANDROID_HOME or --build-tools'
    aapt=$(command -v aapt) || fail 'aapt unavailable; set ANDROID_HOME or --build-tools'
fi
[[ -x "$apksigner" && -x "$aapt" ]] || fail 'Incomplete Android build-tools directory'

certificate_info=$("$apksigner" verify --print-certs "$apk") || fail 'APK signature verification failed'
signer_dns=$(sed -n 's/^Signer #[0-9][0-9]* certificate DN: //p' <<< "$certificate_info")
[[ -n "$signer_dns" ]] || fail 'APK has no verifiable signer certificate'
while IFS= read -r signer_dn; do
    [[ "$signer_dn" =~ (^|,[[:space:]]*)CN=Android[[:space:]]+Debug(,|$) ]] || fail 'Every signer must use an Android Debug test certificate'
done <<< "$signer_dns"

badging=$("$aapt" dump badging "$apk") || fail 'Cannot read the APK manifest'
package_line=${badging%%$'\n'*}
[[ "$package_line" == *"name='org.langue.universal'"* ]] || fail 'APK must be the universal application'
[[ "$package_line" == *"versionCode='$version_code'"* ]] || fail "Expected versionCode $version_code"
[[ "$package_line" == *"versionName='$version_name'"* ]] || fail "Expected versionName $version_name"
manifest=$("$aapt" dump xmltree "$apk" AndroidManifest.xml) || fail 'Cannot inspect the APK manifest'
grep -Eq '^[[:space:]]+A: android:debuggable\(0x0101000f\)=\(type 0x12\)0x(ffffffff|1)([[:space:]]|$)' <<< "$manifest" \
    || fail 'APK must explicitly be debuggable (a test build)'

# Validate before creating output. Replace only the three generated files; never
# recursively delete a supplied path or overwrite the source release notes.
apk=$(realpath -- "$apk")
notes=$(realpath -- "$notes")
output=$(realpath -m -- "$output")
[[ "$output" != / && "$output" != "$repo_root" ]] || fail 'Use a dedicated output directory'
[[ "$output/langue.apk" != "$apk" && "$output/RELEASE_NOTES.md" != "$notes" ]] \
    || fail 'Output must not overwrite a source file'
mkdir -p -- "$output"
for name in langue.apk RELEASE_NOTES.md SHA256SUMS.txt; do
    [[ ! -L "$output/$name" ]] || fail "Output file is a symlink: $name"
    [[ ! -e "$output/$name" || -f "$output/$name" ]] || fail "Output path is not a regular file: $name"
done

staging=$(mktemp -d "$output/.package-test-apk.XXXXXXXX")
trap 'rm -f -- "$staging/langue.apk" "$staging/RELEASE_NOTES.md" "$staging/SHA256SUMS.txt"; rmdir -- "$staging"' EXIT
install -m 644 -- "$apk" "$staging/langue.apk"
install -m 644 -- "$notes" "$staging/RELEASE_NOTES.md"
(
    cd -- "$staging"
    sha256sum langue.apk RELEASE_NOTES.md > SHA256SUMS.txt
    sha256sum -c SHA256SUMS.txt
)
for name in langue.apk RELEASE_NOTES.md SHA256SUMS.txt; do
    mv -f -- "$staging/$name" "$output/$name"
done
printf 'Verified Android Debug test APK: version %s (code %s).\n' "$version_name" "$version_code"
printf 'Installation files prepared in %s\n' "$output"
