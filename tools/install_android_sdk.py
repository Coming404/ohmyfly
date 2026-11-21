#!/usr/bin/env python3
"""从统一版本目录安装云端编译所需的 Android 平台。"""
from pathlib import Path
import re
import subprocess


def main():
    """Gradle 与 SDK 安装使用相同主次版本，不改设备最低版本。"""
    catalog = (Path(__file__).resolve().parents[1] / 'gradle/libs.versions.toml').read_text()
    major = re.search(r'^android-compileSdk = "(\d+)"$', catalog, re.M).group(1)
    minor = re.search(r'^android-compileSdkMinor = "(\d+)"$', catalog, re.M).group(1)
    subprocess.run(['sdkmanager', f'platforms;android-{major}.{minor}', f'build-tools;{major}.0.0'], check=True)


if __name__ == '__main__':
    main()
