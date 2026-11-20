// 校验目录全量三种命名、重叠名称与缩写边界。
package catalog

import (
	"strings"
	"testing"
)

// TestRegionCatalogAliases 覆盖每个目录项的中英文名称及代码。
func TestRegionCatalogAliases(t *testing.T) {
	for _, row := range regionCatalogRows {
		t.Run(row.Code, func(t *testing.T) {
			for _, name := range []string{row.English, row.Name, strings.ToUpper(row.English)} {
				if got := regionFromName(name); got != row.Code {
					t.Errorf("%q: got %q, want %q", name, got, row.Code)
				}
			}
			want := row.Code
			if want == "CF" {
				want = "cloudflare"
			}
			for _, code := range []string{row.Code, strings.ToLower(row.Code)} {
				if got := regionFromName("[" + code + "]01"); got != want {
					t.Errorf("%q: got %q, want %q", code, got, want)
				}
			}
		})
	}
}

// TestRegionNameBoundaries 防止短名称、代码和 CloudFlare 抢占其他地区。
func TestRegionNameBoundaries(t *testing.T) {
	for _, item := range []struct{ name, want string }{
		{"Hong Kong 01", "HK"}, {"hOnG kOnG", "HK"}, {"HK01", "HK"}, {"香港专线", "HK"},
		{"hongkong", "HK"}, {"HoNgKoNg01", "HK"}, {"hongkonger", ""},
		{"cloudflare", "cloudflare"}, {"CF优选", "cloudflare"}, {"cF-01", "cloudflare"},
		{"Central African Republic", "CF"}, {"中非共和国", "CF"},
		{"Papua New Guinea", "PG"}, {"Equatorial Guinea", "GQ"}, {"Guinea-Bissau", "GW"},
		{"赤道几内亚", "GQ"}, {"几内亚比绍", "GW"}, {"South Sudan", "SS"},
		{"HK 香港", "HK"}, {"Hong Kong (China)", "HK"}, {"Macau (China)", "MO"},
		{"brush", ""}, {"shk", ""}, {"trafficflow", ""}, {"未知节点", ""},
		{"东京专线01", "JP"}, {"Osaka-02", "JP"}, {"狮城03", "SG"},
		{"Los  Angeles 01", "US"}, {"纽约", "US"}, {"NYC-01", "US"}, {"USA01", "US"},
		{"UK-01", "GB"}, {"Dubai", "AE"}, {"臺灣", "TW"},
		{"印度尼西亚", "ID"}, {"印尼", "ID"}, {"North Korea", "KP"},
		{"LA01", "LA"}, {"la01", "LA"}, {"马里", "ML"}, {"索马里", "SO"},
		{"🇯🇵节点01", "JP"}, {"🇭🇰", "HK"}, {"🇹🇼", "TW"}, {"🇨🇫", "CF"},
		{"🇧🇹01", "BT"}, {"🇿🇿", ""}, {"🇯", ""}, {"🇯-🇵", ""},
		{"🇿🇿🇯🇵", "JP"}, {"🇯🇵 US01", "JP"}, {"香港 🇯🇵", "HK"},
		{"Tokyotower", ""}, {"brooklyn", ""}, {"usability", ""}, {"cloudflared", ""},
	} {
		if got := regionFromName(item.name); got != item.want {
			t.Errorf("%q: got %q, want %q", item.name, got, item.want)
		}
	}
}
