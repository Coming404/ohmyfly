# 双端真机 Release 构建

远程仓库：[Coming404/ohmyfly](https://github.com/Coming404/ohmyfly)，私有。工作流入口为 [Release device builds](https://github.com/Coming404/ohmyfly/actions/workflows/build.yml)，选择 `both` 构建双端，也可单独选平台。只生成 Release 分发产物，不保留旧的 Debug/模拟器分发入口。

## 产物与安装

| 产物 | 用途 |
|---|---|
| `ohmyfly-release.apk` | 专用测试证书签名的 Android Release，供真机安装 |
| `ohmyfly-release.aab` | 同一证书签名的 Android App Bundle，不能当 APK 直接安装 |
| `ohmyfly-release-unsigned.ipa` | iPhoneOS / arm64 / Release，用户使用自己的 Apple 签名工具重签后安装 |

校验摘要写入 Actions Job Summary；不再把元数据和安装包一起压缩分发。

**iOS IPA 未签名，不能直接安装。** 本次用户选择自行重签，未提供 Apple 证书、描述文件或 Team ID。重签工具、证书权限、设备登记及系统要求由所选安装方式决定；不要把构建成功当成已完成签名或真机验收。Android 若已安装相同包名但不同证书的版本，不能直接覆盖安装；卸载会删除该版本的本地数据，应先确认。

工件只保留在私有仓库的对应 Actions run 中，默认保留七天；使用官方 `upload-artifact` 的 `archive: false` 单独交付原格式 IPA/APK/AAB，不需要解开外层 ZIP。不自动下载安装包到本地设备；已下载的一次性副本已清理。归档与 dSYM 仅作为 CI 中间结果，不再额外生成 ZIP 分发。不创建公开 GitHub Release，也不上传商店。当前开发包名 `com.ohmyfly.app` 和图标仍是测试用途，目录初始为空，塔台来源和Go起飞待接通。

## Android 测试签名

本次生成独立测试 Release 签名，不使用 Android debug keystore，也不把它视为最终上架签名。相同签名用于后续测试包，避免每次构建都换证书。

本地备份位于项目的 `.signing/`：`android-test-release.p12` 与对应 JSON 信息，目录/文件权限受限，整个目录已加入 `.gitignore`。**请安全备份二者，不要提交、公开分享或作为 Actions 工件上传；丢失密钥会影响后续同包名覆盖升级。** 本地密码不写入日志、文档或聊天。

仓库配置以下 Actions Secrets，工作流只在临时目录还原 keystore，结束后删除：

- `ANDROID_RELEASE_STORE_BASE64`
- `ANDROID_RELEASE_STORE_PASSWORD`
- `ANDROID_RELEASE_KEY_ALIAS`
- `ANDROID_RELEASE_KEY_PASSWORD`

Gradle 通过上述密码/别名和构建时的 `ANDROID_KEYSTORE_PATH` 读取签名，缺少配置时不应悄悄交付无签名 APK。

## 构建与验证边界

- Android：`testAndroidHostTest` + `assembleRelease` + `bundleRelease`；校验证书签名，拒绝带 `application-debuggable` 标记的 APK。`tools/normalize_aab.py` 只规范化 AAB 的签名条目顺序与非签名 ZIP 属性，逐项核对内容 SHA-256，再用原测试签名执行 `jarsigner -verify -strict`；绝不处理 APK。这个标准库工具是 CI 的格式校验工具，不是 App 依赖。
- iOS：Xcode `Release`、`iphoneos`、`arm64`、`archive`，关闭代码签名。`tools/package_ios_release.py` 在打包前检查 App 元数据、Mach-O CPU 与 iOS device 平台，拒绝 arm64 模拟器冒充真机包。
- `tools/package_ios_release.py` 是 CI 所需的保留工具：校验部分只用 Python 标准库，归档打包使用 macOS 自带 `ditto`。不是 App 运行依赖。
- 本地只运行 `python3 tools/check_project.py` 和 `python3 -m unittest discover -s tools/tests -v`，不下载完整移动端工具链。打包验证测试可以在 Linux 运行，不声称本地运行了 Xcode。
- Android host tests 已通过 `withHostTest {}` 与 `:shared:testAndroidHostTest` 接入 Release。当前已验证代码基线为 `78d80bd`：[Release run 39426815748](https://github.com/Coming404/ohmyfly/actions/runs/39426815748) 双端成功，[Project checks 39426798576](https://github.com/Coming404/ohmyfly/actions/runs/39426798576) 成功；JUnit确认146项Kotlin共享测试全部通过，无失败或跳过。构建前已按用户要求清空旧Actions及产物；重试修复了生命周期目录别名、iOS常量导入和宿主几何测试边界。三份原格式产物保留至2026-10-27，未下载到本地。最低iOS16/Android13真机、玻璃像素/传感器、读屏、大字体、运行中减少动态和帧耗仍待验证；应用开关未覆盖所有Miuix内置动画。

声明的版本与工具链仍由版本目录和 Wrapper 管理；SDK Manager 平台包使用官方仓库实际存在的 `platforms;android-37.0`，不是不存在的裸 `android-37` 包名。
