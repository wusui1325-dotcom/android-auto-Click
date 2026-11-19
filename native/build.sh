#!/usr/bin/env bash
# Rebuild the prebuilt root uinput helper into app/src/main/assets/root_uinput/<abi>/.
# CI does not compile this (no NDK in the release workflow); committed binaries ship in the APK.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/native/root_uinput.c"
OUT_BASE="$ROOT/app/src/main/assets/root_uinput"
NDK="${NDK:-${ANDROID_NDK_HOME:-${ANDROID_HOME:+$ANDROID_HOME/ndk/27.3.13750724}}}"
if [[ -z "${NDK}" || ! -d "$NDK" ]]; then
  echo "Set ANDROID_NDK_HOME or ANDROID_HOME with NDK 27.3.13750724" >&2
  exit 1
fi
PREBUILT="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
API=26
compile() {
  local triple="$1" abi="$2"
  local cc="$PREBUILT/${triple}${API}-clang"
  local dest="$OUT_BASE/$abi"
  mkdir -p "$dest"
  "$cc" -Os -fPIE -pie -ffunction-sections -fdata-sections     -Wl,--gc-sections -Wl,-z,relro,-z,now -s     -o "$dest/root_uinput" "$SRC"
  echo "built $dest/root_uinput ($("$PREBUILT/llvm-size" "$dest/root_uinput" | tail -1))"
}
compile aarch64-linux-android arm64-v8a
compile armv7a-linux-androideabi armeabi-v7a
compile x86_64-linux-android x86_64
compile i686-linux-android x86
echo done
