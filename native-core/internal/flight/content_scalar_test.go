// 内容哈希的协议速率适配不能合并不同数量级、比特/字节速率。
package flight

import "testing"

func TestContentHashBandwidthUnits(t *testing.T) {
	hash := func(value string) string {
		return ContentHash(Flight{Fields: map[string]any{"type": "hysteria", "server": "review.example", "port": 443, "up": value}})
	}
	for _, pair := range [][2]string{{"1 bps", "1 Mbps"}, {"1 MBps", "1 Mbps"}, {"1 Gbps", "1 Mbps"}} {
		t.Run(pair[0], func(t *testing.T) {
			if hash(pair[0]) == hash(pair[1]) {
				t.Fatal("不同带宽被合并")
			}
		})
	}
	if hash("1 Gbps") != hash("1000 Mbps") {
		t.Fatal("等价带宽未规范化")
	}
}
