#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]] && [[ ! -f local.properties ]]; then
  echo "Android SDK 未配置：请设置 ANDROID_HOME，或在 local.properties 写入 sdk.dir=/path/to/sdk" >&2
  exit 1
fi

gradle :composeApp:assembleDebug

apk_path="composeApp/build/outputs/apk/debug/composeApp-debug.apk"
if [[ -f "$apk_path" ]]; then
  echo "APK 已生成：$apk_path"
else
  echo "构建完成，但未在预期位置找到 APK：$apk_path" >&2
  exit 1
fi
