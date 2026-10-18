# Agent Note: 私有仓库与双端真机 Release 构建

Status: proposed

## Problem

现有工作流只构建 Android Debug APK 和 iOS 模拟器 App，不能满足用户当前要求的双端 Release 真机测试。项目尚无远程仓库，原文档不应再把“没有授权推送”作为本次任务边界。

## Proposal

用户明确授权创建 `Coming404/ohmydrift` 私有仓库、推送并触发双端 Actions。Android 生成独立测试签名，保存在本地排除目录和仓库 Actions Secrets，生成已签名 Release APK/AAB。用户选择 iOS 自行重签，并进一步要求直接交付 IPA 而不是 ZIP：使用 iphoneos/arm64/Release archive，只上传待重签 IPA 原文件，xcarchive/dSYM 只作为构建中间结果，不再额外分发 ZIP；不混用模拟器或 Debug 产物，也不声称 unsigned IPA 可直接安装。

固定工具链先核实后用云端构建验证；不在本地下载 Gradle、Android SDK 或 Kotlin/Native。签名密码和令牌不写入源码、日志、Git remote URL 或公共工件。工作流只上传明确收集的分发文件，私有仓库工件保留七天，不创建公开 Release。

文档补充官方中文快速开始与准确查询原则：官方入门文档继续作为入口；涉及具体组件时，用项目锁定版本的源码核对，发现个别示例不同步就按源码调整。

## Warning cleanup

已实际定位 SDK Action 默认安装退役 tools 包、CI 缺少 ripgrep、Git 中 Wrapper 执行位遗漏和 Xcode `-arch`/destination 冲突，并分别按失败日志修复。Android Release 已在 run 39121397470 的 android job 成功，iOS Release 已在 run 39121618951 成功，后续警告修复仍需新 run 复验。

将 Actions 更新为逐一核实过 Node24 运行时的稳定版本，不设置 NODE_NO_WARNINGS。启用 Android host tests 并在 CI 运行共享测试；Apple target 仅在可构建它的 macOS 主机创建，不加忽略禁用目标的属性。Kotlin Xcode 脚本显式声明每次交给 Gradle 做增量判断，避免假输出导致代码变更漏编译。AppIntents 未使用时采用 Swift 官方 llbuild 工程的 `LM_SKIP_METADATA_EXTRACTION=YES` 跳过无用阶段，未来引入 AppIntents 时撤销该配置。测试证书与无签名分发的真实边界保留，不关闭签名验证。

官方 upload-artifact v7.0.2 的 `archive: false` 支持单文件原格式工件；IPA、APK、AAB 分别上传，不再要求用户下载外层 ZIP。来源：[Action 定义](https://github.com/actions/upload-artifact/blob/v7.0.2/action.yml)、[Swift 官方对应变更](https://github.com/swiftlang/swift-llbuild/pull/963)。

run 39122981430 已双端成功且真实执行 host tests，但 JDK 校验 AGP 产出的 AAB 时仍报告 ZIP 权限属性及 JarFile/JarInputStream 读取差异。按 [OpenJDK 的签名条目顺序说明](https://bugs.openjdk.org/browse/JDK-8031748)，仅规范化 AAB 的 ZIP 容器：Manifest/SF/签名块置前，拒绝重复项/符号链接，保留每个条目的原始字节并核对 SHA-256，再用原测试证书执行 jarsigner strict 验证。绝不对 APK 做这种处理（APK v2/v3 会保护 ZIP 结构），不修改已签名的条目内容，也不关闭警告或签名检查。

用户进一步明确不需要下载安装包到本地：安装资源只保留在 GitHub，已下载的三份本地副本已清理；`.signing` 备份保留。

## Alternatives considered

- 保留 Debug/模拟器构建：启动验证更直接，但不符合用户指定 Release 和真机的要求，替换而不双轨保留旧分发入口。
- iOS 自动签名 IPA：可以直接供已登记设备安装，但当前没有 Apple 签名材料，用户明确选择自行重签，不虚构签名能力。
- Android 临时丢弃签名：初次构建简单，但后续无法原位升级，因此保留同一测试签名；这不是最终商店签名。

## Acceptance criteria

- 私有仓库创建成功，推送的提交不含签名材料或令牌；双端 workflow 使用同一提交构建。
- Android Release APK/AAB 通过签名检查；iOS IPA 的 Mach-O 为 arm64、平台 iOS 而非模拟器，归档配置为 Release。
- 本地检查与包验证测试通过；实际 Actions 状态、产物链接和未完成项如实报告。

## Risks

私有仓库 Actions 会使用账户构建额度；首次云端构建可能暴露既有工具链/API问题。iOS IPA 需用户重签，Apple 账号、证书权限和设备系统限制仍适用。最低系统真机运行、上架许可和发布签名不由编译成功证明。
