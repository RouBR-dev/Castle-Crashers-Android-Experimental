# Windows (PowerShell). Requisitos: Git, CMake>=3.25, Ninja, LLVM (clang) no PATH,
# Python 3, Android NDK 27.2.12479018 (variavel ANDROID_NDK_ROOT).
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
if (-not $env:ANDROID_NDK_ROOT) { throw "Defina ANDROID_NDK_ROOT (NDK 27.2.12479018)" }
if (-not (Test-Path game/default.xex)) { throw "Falta game/default.xex" }

git init 2>$null | Out-Null
if (-not (Test-Path third_party/rexglue-sdk/CMakeLists.txt)) {
  git submodule add -b android-arm64 https://github.com/Buku313/rexglue-skate3-android.git third_party/rexglue-sdk
  git submodule update --init --recursive
}

Write-Host "==> 1/3 Codegen no PC (PPC -> C++)"
cmake --preset host-codegen
cmake --build --preset host-codegen --target generate-castle

Write-Host "==> 2/3 Compilando para arm64-v8a"
cmake --preset android-release
cmake --build --preset android-release --parallel

Write-Host "==> 3/3 Copiando .so para o projeto Android"
$jni = "android/app/libs/arm64-v8a"
New-Item -ItemType Directory -Force $jni | Out-Null
Copy-Item out/build/android-release/libcastle.so $jni -Force
Copy-Item out/build/android-release/librexruntime.so $jni -Force
$cxx = Get-ChildItem -Recurse "$env:ANDROID_NDK_ROOT/toolchains/llvm/prebuilt" -Filter libc++_shared.so |
        Where-Object { $_.FullName -match "aarch64-linux-android" } | Select-Object -First 1
Copy-Item $cxx.FullName $jni -Force
Write-Host "Pronto. Abra a pasta android/ no Android Studio e rode Build > Build APK."
