#!/bin/bash
# Build SmallSF-TV.apk on the Mac mini (manual aapt2/d8 pipeline, no Gradle).
set -euo pipefail
export PATH="/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"
PROJ="$HOME/smallsf-tv"
SDK="$HOME/Library/Android/sdk"
# NOTE: non-interactive ssh does not source ~/.zshrc, so sdkmanager's default
# root is the Homebrew cmdline-tools dir — that is where build-tools landed.
BT="/opt/homebrew/share/android-commandlinetools/build-tools/35.0.0"
AJAR="$SDK/platforms/android-30/android.jar"
cd "$PROJ"

if [ ! -x "$BT/aapt2" ]; then
  echo "== installing build-tools 35.0.0 =="
  yes | sdkmanager --licenses >/dev/null 2>&1 || true
  sdkmanager --sdk_root="/opt/homebrew/share/android-commandlinetools" "build-tools;35.0.0"
fi

KS="$PROJ/signing.keystore"
if [ ! -f "$KS" ]; then
  echo "== generating sideload signing key =="
  keytool -genkeypair -keystore "$KS" -alias smallsf -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass smallsftv -keypass smallsftv \
    -dname "CN=Small SF TV, O=Barklee Personal, C=US" >/dev/null 2>&1
fi

rm -rf build && mkdir -p build/gen build/classes build/dex
MAIN=app/src/main

echo "== aapt2 compile + link =="
"$BT/aapt2" compile --dir "$MAIN/res" -o build/compiled.zip
"$BT/aapt2" link -o build/app-unsigned.apk -I "$AJAR" \
  --manifest "$MAIN/AndroidManifest.xml" build/compiled.zip \
  --java build/gen --min-sdk-version 24 --target-sdk-version 30 \
  --version-code 3 --version-name 1.2

echo "== javac =="
javac --release 8 -cp "$AJAR" -d build/classes \
  $(find build/gen "$MAIN/java" -name '*.java')

echo "== d8 =="
"$BT/d8" --min-api 24 --lib "$AJAR" --output build/dex \
  $(find build/classes -name '*.class')

echo "== package + align + sign =="
cp build/app-unsigned.apk build/app-with-dex.apk
(cd build/dex && zip -q ../app-with-dex.apk classes.dex)
"$BT/zipalign" -f 4 build/app-with-dex.apk build/app-aligned.apk
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:smallsftv --key-pass pass:smallsftv \
  --out "$PROJ/SmallSF-TV.apk" build/app-aligned.apk

echo "== verify =="
"$BT/apksigner" verify --print-certs "$PROJ/SmallSF-TV.apk" | head -5
"$BT/aapt2" dump badging "$PROJ/SmallSF-TV.apk" | head -8
ls -la "$PROJ/SmallSF-TV.apk"
echo "BUILD_OK"
