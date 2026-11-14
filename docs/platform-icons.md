# 平台 SVG 图标

## 来源与平台标识

依据本地 sublinkPro `666bc19` 的 `models/unlock.go` 和 `services/unlock/checker_*.go` 注册的七个平台，统一使用其规范标识：

| 标识 | 平台 | 默认颜色 |
| --- | --- | --- |
| `netflix` | Netflix | `#E50914` |
| `disney` | Disney+ | `#02D6E8` |
| `youtube_premium` | YouTube Premium | `#FF0000` |
| `openai` | OpenAI / ChatGPT | `#10A37F` |
| `gemini` | Google Gemini | `#4285F4` |
| `claude` | Claude | `#D97757` |
| `bahamut` | 巴哈姆特动画疯 | `#117E96` |

流媒体为 Netflix、Disney+、YouTube Premium、巴哈姆特；其余三项是 AI 服务可访问性查询。这里准备展示资源，不执行平台检测。

## 使用

路径、平台映射、标签、颜色和默认渲染入口全部集中在 `shared/src/commonMain/kotlin/com/ohmyfly/ui/PlatformIcons.kt`。保留 SVG 的 `d` 路径字符串，通过 Compose `addPathNodes` 构造 `ImageVector`，按需懒加载，无位图、网络资源或额外依赖。

```kotlin
// 摘要中的平台 ID 与上游注册 ID 一致，未知平台保留文字信息。
PlatformIcons.forPlatform(result.platform)?.let { brand ->
    PlatformIcon(brand, modifier = Modifier.size(20.dp))
}
```

`PlatformIcon` 显式使用 `brand.color`，不继承 Miuix `Icon` 的默认文字颜色。品牌色标识平台，不表示检测成功；解锁、受限、失败等状态由调用方独立显示。未知标识返回 null，不能冒用其他平台图标。页面接线在航班 UI 阶段完成。

SVG 路径内部使用黑色作为单色遮罩，最终显示色来自显式 tint。Disney+ 使用青色单色版本，移除源资源的深蓝填充和渐变；非方形原始坐标居中到方形画布，不拉伸字标。巴哈姆特保持原线稿路径。OpenAI 使用 ChatGPT 常见绿色、Gemini 使用 Google 蓝色，均为本应用选定的单色展示方案，并非声称这些品牌仅有一个官方配色。Netflix、YouTube、Claude 使用 Simple Icons 固定版本元数据的颜色。

## 图形署名与许可

- Netflix、YouTube、OpenAI、Google Gemini、Claude：来自 [Simple Icons 14.15.0](https://github.com/simple-icons/simple-icons/tree/14.15.0/icons)，标签对象 `922277d0128f0f6d95d13dc93100a6b94cc62202`；保留原始路径，改变展示颜色与封装形式。[CC0 完整文本](../third-party/licenses/simple-icons-CC0.md)。
- Disney+：来自 [theSVG](https://github.com/glincker/thesvg)，经 [Iconify thesvg-color:disney-plus](https://api.iconify.design/thesvg-color/disney-plus.svg) 获取，2026-11-12。Copyright (c) 2025 thesvg.org；保留可见路径，删除不可见路径，改为单色并居中。[MIT 完整文本](../third-party/licenses/thesvg-MIT.txt)。
- 巴哈姆特：来自 Donnnno 与 Arcticons 贡献者的 [Arcticons](https://github.com/Arcticons-Team/Arcticons)，经 [Iconify arcticons:bahamut](https://api.iconify.design/arcticons/bahamut.svg) 获取，2026-11-12。保留原始路径和线宽，增加青色展示及 Compose 包装。**该图标图形及其改编继续使用 CC BY-SA 4.0**；图形署名不改变其余文件中独立代码与其他图形的许可。[CC BY-SA 4.0 完整文本](../third-party/licenses/arcticons-CC-BY-SA-4.0.txt)。Arcticons README 单独规定图标为 CC BY-SA 4.0，其应用代码 GPL 许可不是这里所用图形的许可。

品牌名称与标识的商标权归各品牌所有。

地区旗帜使用 [kmp-flagkit 1.1.0](https://github.com/acarlsen/kmp-flagkit/tree/1.1.0)，通过 `FlagKit.getFlag(countryCode)` 按节点名称识别所得的地区代码或平台结果自身的地区代码查找；两类地区独立展示，未知或空代码不显示旗帜。该库覆盖 Android、iOS、macOS、桌面和 Web 目标，使用 MIT 许可。[MIT 原文](https://github.com/acarlsen/kmp-flagkit/blob/1.1.0/LICENSE)。

CloudFlare 地区标签的国旗槽使用用户指定的 [theSVG 原始资源](https://cdn.jsdelivr.net/gh/glincker/thesvg@main/public/icons/cloudflare/default.svg)，获取日期为 2026-11-14。`CloudFlareIcon.kt` 保留三个 SVG 路径及白色、橙色填充，按原始 256×116 比例居中到方形画布，通过 `Image` 原色渲染；资源内置，不在运行时下载。Copyright (c) 2025 thesvg.org；[MIT 完整文本](../third-party/licenses/thesvg-MIT.txt)。
