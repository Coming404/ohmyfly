# 去重审查证据

[dedup-results.txt](dedup-results.txt)是两个上游原始去重函数的独立Go标准库测试输出，不是整个项目的构建或网络测试。

来源固定版本见[资料入口](../references.md#go-核心与上游实现)。测试提取原函数并隔离依赖，未修改上游仓库。

共同条件：server=example.com、port=443。对照表明clash-speedtest会合并不同UUID、不同协议及不同WS路径；subs-check-pro在这三个例子中保留两个。

subs-check-pro误合并样本：

- Trojan密码SecretA与secreta。
- SOCKS5密码相同、用户名alice与bob。
- TUIC同UUID、密码one与two。
- WS路径/api?token=one与/api?token=two。
- Reality公钥相同、short-id为11与22。
- SS密码相同、cipher为aes-128-gcm与aes-256-gcm。

反例：WS路径/API与/api在该实现中保留两个。

输出中的PASS只表示测试按预期观察到该行为，不表示去重正确。后续实现必须把这些例子改成“不误删”的回归断言。
