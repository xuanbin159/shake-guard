#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_APK="${1:-$PROJECT_ROOT/dist/ShakeGuard-v1.0.0.apk}"

SDK_ROOT="$HOME/.local/android-sdk"
BUILD_TOOLS="$SDK_ROOT/build-tools/android-14"
ANDROID_JAR="$SDK_ROOT/platforms/android-34/android.jar"
KOTLIN_DIR="$SDK_ROOT/kotlin"

MAIN_DIR="$PROJECT_ROOT/android/app/src/main"
BUILD_DIR="$PROJECT_ROOT/android/app/build/sdk_out"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$(dirname "$OUT_APK")"

echo "[1/6] Compiling Android resources with aapt2..."
"$BUILD_TOOLS/aapt2" compile --dir "$MAIN_DIR/res" -o "$BUILD_DIR/res.zip"

echo "[2/6] Linking resources, AndroidManifest.xml and assets..."
"$BUILD_TOOLS/aapt2" link \
    -o "$BUILD_DIR/unaligned.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$MAIN_DIR/AndroidManifest.xml" \
    -A "$MAIN_DIR/assets" \
    --java "$BUILD_DIR/gen" \
    --auto-add-overlay \
    "$BUILD_DIR/res.zip"

echo "[3/6] Compiling R.java and Kotlin sources (Kotlin 1.9.24, JVM 17)..."
mapfile -t JAVA_FILES < <(find "$BUILD_DIR/gen" -name "*.java")
mapfile -t KT_FILES < <(find "$MAIN_DIR/java" -name "*.kt")

java -cp "$KOTLIN_DIR/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib \
    -jvm-target 17 \
    -cp "$ANDROID_JAR:$KOTLIN_DIR/kotlin-stdlib.jar" \
    "${JAVA_FILES[@]}" "${KT_FILES[@]}" \
    -d "$BUILD_DIR/classes"

javac --release 17 -cp "$ANDROID_JAR:$BUILD_DIR/classes" -d "$BUILD_DIR/classes" "${JAVA_FILES[@]}"

echo "[4/6] Converting bytecode + kotlin-stdlib to DEX with d8 (minSdk 26)..."
mapfile -t CLASS_FILES < <(find "$BUILD_DIR/classes" -name "*.class")
"$BUILD_TOOLS/d8" \
    --lib "$ANDROID_JAR" \
    --min-api 26 \
    --output "$BUILD_DIR/dex" \
    "${CLASS_FILES[@]}" \
    "$KOTLIN_DIR/kotlin-stdlib.jar"

echo "[5/6] Packaging DEX and aligning with zipalign..."
(cd "$BUILD_DIR/dex" && zip -q -u "$BUILD_DIR/unaligned.apk" classes*.dex)
"$BUILD_TOOLS/zipalign" -f -p 4 "$BUILD_DIR/unaligned.apk" "$BUILD_DIR/aligned.apk"

echo "[6/6] Signing APK (v1 + v2 + v3 schemes) and verifying signature..."
KEYSTORE="$SDK_ROOT/shakeguard-release.jks"
if [[ ! -f "$KEYSTORE" ]]; then
    keytool -genkeypair \
        -keystore "$KEYSTORE" \
        -alias shakeguard \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass shakeguard123 \
        -keypass shakeguard123 \
        -dname "CN=ShakeGuard, OU=MobileSecurity, O=ShakeGuard, L=Beijing, ST=Beijing, C=CN" >/dev/null 2>&1
fi

"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias shakeguard \
    --ks-pass pass:shakeguard123 \
    --key-pass pass:shakeguard123 \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out "$OUT_APK" \
    "$BUILD_DIR/aligned.apk"

"$BUILD_TOOLS/apksigner" verify --verbose "$OUT_APK" | head -n 8
echo "APK_BUILD_SUCCESS: $OUT_APK"
