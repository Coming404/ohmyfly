# Agent Note: 私有仓库与双端真机 Release 构建

Status: proposed

## Problem

现有工作流只构建 Android Debug APK 和 iOS 模拟器 App，不能满足用户当前要求的双端 Release 真机测试。项目尚无远程仓库，原文档不应再把“没有授权推送”作为本次任务边界。

## Proposal

用户明确授权创建 `Coming404/ohmydrift` 私有仓库、推送并触发双端 Actions。Android 生成独立测试签名，保存在本地排除目录和仓库 Actions Secrets，生成已签名 Release APK/AAB。用户选择 iOS 自行重签：使用 iphoneos/arm64/Release archive，产出待重签 IPA、xcarchive 与存在时的 dSYM，不混用模拟器或 Debug 产物，也不声称 unsigned IPA 可直接安装。

固定工具链先核实后用云端构建验证；不在本地下载 Gradle、Android SDK 或 Kotlin/Native。签名密码和令牌不写入源码、日志、Git remote URL 或公共工件。工作流只上传明确收集的分发文件，私有仓库工件保留七天，不创建公开 Release。

文档补充官方中文快速开始与准确查询原则：官方入门文档继续作为入口；涉及具体组件时，用项目锁定版本的源码核对，发现个别示例不同步就按源码调整。

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
