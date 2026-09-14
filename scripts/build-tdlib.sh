#!/usr/bin/env bash
#
# Builds TDLib (vendored at third_party/tdlib) into libtdjson.so for the ABIs
# Master Control ships, and installs the result into telegram/src/main/jniLibs.
#
# Why this script exists
# ----------------------
# TDLib is a source dependency, not a binary download: the exact code that ends
# up in the APK is the code in third_party/tdlib, pinned to a released version.
# This script drives the documented TDLib Android build steps
# (third_party/tdlib/example/android/*.sh) but restricts the ABI list to what
# the app declares in telegram/build.gradle.kts, so no unnecessary ABIs are
# produced or packaged.
#
# Usage
# -----
#   scripts/build-tdlib.sh --android-sdk-root "$HOME/Android/Sdk"
#   scripts/build-tdlib.sh --include-x86-64            # emulator/testing ABI
#   scripts/build-tdlib.sh --openssl-dir /path/to/prebuilt/openssl
#   scripts/build-tdlib.sh --clean                     # wipe build outputs first
#
# Requirements (host)
# -------------------
#   bash, cmake >= 3.22, ninja, JDK 17 (java/javac/jar), perl, gperf, make,
#   php, tar, unzip, Android SDK with NDK 26.3.11579264 and cmake 3.22.1.
#
# Network
# -------
#   Only OpenSSL sources are downloaded (pinned tag, from the official OpenSSL
#   GitHub repository) and only when --openssl-dir is not supplied. TDLib itself
#   is never downloaded: it is vendored.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TDLIB_DIR="$REPO_ROOT/third_party/tdlib"
BUILD_ROOT="$REPO_ROOT/build/tdlib"
JNI_OUTPUT="$REPO_ROOT/telegram/src/main/jniLibs"

# Pinned versions. Keep in sync with gradle/libs.versions.toml (ndkVersion) and
# with third_party/tdlib/CMakeLists.txt (project version).
TDLIB_VERSION="1.8.67"
NDK_VERSION="26.3.11579264"
ANDROID_PLATFORM="android-26"   # == minSdk
ANDROID_STL="c++_static"
OPENSSL_VERSION="OpenSSL_1_1_1w"
SDK_CMAKE_VERSION="3.22.1"

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
OPENSSL_DIR=""
INCLUDE_X86_64=0
DO_CLEAN=0
ABIS_OVERRIDE=""

usage() {
  sed -n '2,40p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

fail() { echo "ERROR: $*" >&2; exit 1; }
info() { echo "==> $*"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --android-sdk-root) ANDROID_SDK_ROOT="${2:-}"; shift 2 ;;
    --ndk-version)      NDK_VERSION="${2:-}"; shift 2 ;;
    --openssl-dir)      OPENSSL_DIR="${2:-}"; shift 2 ;;
    --abis)             ABIS_OVERRIDE="${2:-}"; shift 2 ;;
    --include-x86-64)   INCLUDE_X86_64=1; shift ;;
    --clean)            DO_CLEAN=1; shift ;;
    -h|--help)          usage; exit 0 ;;
    *)                  usage; fail "unknown argument: $1" ;;
  esac
done

# ---------------------------------------------------------------------------
# 1. Resolve the ABI list (must match telegram/build.gradle.kts abiFilters).
# ---------------------------------------------------------------------------
if [[ -n "$ABIS_OVERRIDE" ]]; then
  IFS=',' read -r -a ABIS <<< "$ABIS_OVERRIDE"
else
  ABIS=("arm64-v8a" "armeabi-v7a")
  if [[ "$INCLUDE_X86_64" == "1" ]]; then ABIS+=("x86_64"); fi
fi
info "ABIs: ${ABIS[*]}"

# ---------------------------------------------------------------------------
# 2. Verify the vendored TDLib source matches the pinned version.
# ---------------------------------------------------------------------------
[[ -d "$TDLIB_DIR" ]] || fail "vendored TDLib not found at $TDLIB_DIR (git submodule/checkout missing?)"
DECLARED_VERSION="$(grep -oE 'project\(TDLib VERSION [0-9.]+' "$TDLIB_DIR/CMakeLists.txt" | awk '{print $3}')"
[[ "$DECLARED_VERSION" == "$TDLIB_VERSION" ]] \
  || fail "third_party/tdlib is version ${DECLARED_VERSION:-unknown} but this script pins $TDLIB_VERSION. Update both together."
[[ -f "$TDLIB_DIR/LICENSE_1_0.txt" ]] || fail "TDLib license file missing; cannot satisfy NOTICE/THIRD_PARTY_LICENSES requirements"
info "TDLib $TDLIB_VERSION (Boost License 1.0) verified at $TDLIB_DIR"

# ---------------------------------------------------------------------------
# 3. Verify host tooling.
# ---------------------------------------------------------------------------
for tool in cmake ninja java perl gperf make tar unzip sed grep; do
  command -v "$tool" >/dev/null 2>&1 || fail "required tool not found: $tool"
done
[[ -n "$ANDROID_SDK_ROOT" && -d "$ANDROID_SDK_ROOT" ]] \
  || fail "Android SDK not found. Pass --android-sdk-root PATH or set ANDROID_SDK_ROOT."
ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/$NDK_VERSION"
[[ -d "$ANDROID_NDK_ROOT" ]] \
  || fail "NDK $NDK_VERSION not installed at $ANDROID_NDK_ROOT (sdkmanager \"ndk;$NDK_VERSION\")"
TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake"
[[ -f "$TOOLCHAIN_FILE" ]] || fail "NDK toolchain file missing: $TOOLCHAIN_FILE"

HOST_ARCH="linux-x86_64"
case "$(uname -s)" in
  Darwin) HOST_ARCH="darwin-x86_64" ;;
  Linux)  HOST_ARCH="linux-x86_64" ;;
  *)      fail "unsupported host OS: $(uname -s)" ;;
esac
STRIP="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$HOST_ARCH/bin/llvm-strip"
[[ -x "$STRIP" ]] || fail "llvm-strip not found at $STRIP"
info "NDK $NDK_VERSION at $ANDROID_NDK_ROOT"

if [[ "$DO_CLEAN" == "1" ]]; then
  info "Cleaning $BUILD_ROOT and $JNI_OUTPUT"
  rm -rf "$BUILD_ROOT" "$JNI_OUTPUT"
fi
mkdir -p "$BUILD_ROOT" "$JNI_OUTPUT"

# ---------------------------------------------------------------------------
# 4. OpenSSL: use a prebuilt directory, or build it with TDLib's own script.
# ---------------------------------------------------------------------------
if [[ -z "$OPENSSL_DIR" ]]; then
  OPENSSL_DIR="$BUILD_ROOT/openssl"
fi
if [[ ! -d "$OPENSSL_DIR" ]]; then
  info "Building OpenSSL ($OPENSSL_VERSION) with TDLib's official script"
  ( cd "$TDLIB_DIR/example/android" \
    && ./build-openssl.sh "$ANDROID_SDK_ROOT" "$NDK_VERSION" "$OPENSSL_DIR" "$OPENSSL_VERSION" ) \
    || fail "OpenSSL build failed"
else
  info "Using prebuilt OpenSSL at $OPENSSL_DIR"
fi
for abi in "${ABIS[@]}"; do
  [[ -d "$OPENSSL_DIR/$abi" ]] || fail "OpenSSL was not built for $abi (expected $OPENSSL_DIR/$abi)"
done

# ---------------------------------------------------------------------------
# 5. Generate TDLib sources once (host build), then build per ABI.
# ---------------------------------------------------------------------------
GEN_DIR="$BUILD_ROOT/build-native-json"
if [[ ! -d "$GEN_DIR" ]]; then
  info "Generating TDLib source files"
  mkdir -p "$GEN_DIR"
  ( cd "$GEN_DIR" && cmake -DTD_ANDROID_JSON=ON -DTD_GENERATE_SOURCE_FILES=ON "$TDLIB_DIR" && cmake --build . ) \
    || fail "TDLib source generation failed"
fi

for abi in "${ABIS[@]}"; do
  abi_dir="$BUILD_ROOT/build-$abi"
  info "Building TDLib JSON interface for $abi"
  mkdir -p "$abi_dir"
  (
    cd "$abi_dir"
    cmake \
      -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
      -DOPENSSL_ROOT_DIR="$OPENSSL_DIR/$abi" \
      -DCMAKE_BUILD_TYPE=RelWithDebInfo \
      -GNinja \
      -DANDROID_ABI="$abi" \
      -DANDROID_STL="$ANDROID_STL" \
      -DANDROID_PLATFORM="$ANDROID_PLATFORM" \
      -DTD_ANDROID_JSON=ON \
      "$TDLIB_DIR"
    cmake --build . --target tdjson
  ) || fail "TDLib build failed for $abi"

  built="$abi_dir/td/libtdjson.so"
  [[ -f "$built" ]] || fail "expected output not produced: $built"

  dest_dir="$JNI_OUTPUT/$abi"
  mkdir -p "$dest_dir"
  # Debug symbols are kept next to the stripped library for crash triage; only
  # the stripped file is packaged (jniLibs/*.so).
  cp -f "$built" "$dest_dir/libtdjson.so.debug"
  "$STRIP" --strip-debug --strip-unneeded "$dest_dir/libtdjson.so.debug" -o "$dest_dir/libtdjson.so" \
    || fail "llvm-strip failed for $abi"
  rm -f "$dest_dir/libtdjson.so.debug"

  if [[ -e "$OPENSSL_DIR/$abi/lib/libcrypto.so" ]]; then
    cp -f "$OPENSSL_DIR/$abi/lib/libcrypto.so" "$OPENSSL_DIR/$abi/lib/libssl.so" "$dest_dir/"
    "$STRIP" "$dest_dir/libcrypto.so" "$dest_dir/libssl.so"
  fi
  info "Installed $dest_dir/libtdjson.so ($(du -h "$dest_dir/libtdjson.so" | cut -f1))"
done

# ---------------------------------------------------------------------------
# 6. Build manifest: what was built, from what, and its checksums.
# ---------------------------------------------------------------------------
MANIFEST="$JNI_OUTPUT/BUILD-INFO.txt"
{
  echo "TDLib native build manifest"
  echo "generated_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "tdlib_version=$TDLIB_VERSION"
  echo "tdlib_license=Boost Software License 1.0 (third_party/tdlib/LICENSE_1_0.txt)"
  echo "openssl_version=$OPENSSL_VERSION"
  echo "ndk_version=$NDK_VERSION"
  echo "android_platform=$ANDROID_PLATFORM"
  echo "android_stl=$ANDROID_STL"
  echo "interface=JSON (libtdjson.so, JNI bridge: tdjson_bridge)"
  echo "abis=${ABIS[*]}"
  echo ""
  echo "checksums:"
  for abi in "${ABIS[@]}"; do
    if command -v sha256sum >/dev/null 2>&1; then
      echo "  $abi/libtdjson.so $(sha256sum "$JNI_OUTPUT/$abi/libtdjson.so" | awk '{print $1}')"
    elif command -v shasum >/dev/null 2>&1; then
      echo "  $abi/libtdjson.so $(shasum -a 256 "$JNI_OUTPUT/$abi/libtdjson.so" | awk '{print $1}')"
    fi
  done
} > "$MANIFEST"

info "Wrote $MANIFEST"
info "Done. Run ./gradlew :telegram:assembleDebug to package the libraries."
