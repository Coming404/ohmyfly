package towerparse

import (
	"net/url"
	"testing"
)

// Flat aliases follow upstream mergeXhttpExtra: kebab-case, snake_case, or the extra key.
func TestXHTTPParityFlatAliasNames(t *testing.T) {
	cases := []struct {
		key, field, value string
		accepted          bool
	}{
		{"sessionIDLength", "session-length", "16", true},
		{"session_length", "session-length", "16", true},
		{"sessionLength", "session-length", "16", false},
		{"sessionIDPlacement", "session-placement", "header", true},
		{"sessionPlacement", "session-placement", "header", false},
		{"uplinkHTTPMethod", "uplink-http-method", "PUT", true},
		{"uplinkHttpMethod", "uplink-http-method", "PUT", false},
		{"uplink_chunk_size", "uplink-chunk-size", "1024", true},
	}
	for _, tc := range cases {
		node := map[string]any{}
		if err := applyXHTTPTransport(node, "/", "", url.Values{tc.key: {tc.value}}); err != nil {
			t.Fatalf("%s: %v", tc.key, err)
		}
		opts := node["xhttp-opts"].(map[string]any)
		got, ok := opts[tc.field]
		if ok != tc.accepted || (ok && got != tc.value) {
			t.Errorf("%s: got %v (present=%v), want accepted=%v value=%q", tc.key, got, ok, tc.accepted, tc.value)
		}
	}

	node := map[string]any{}
	if err := applyXHTTPTransport(node, "/", "", url.Values{"hKeepAlivePeriod": {"30"}}); err != nil {
		t.Fatal(err)
	}
	reuse, _ := node["xhttp-opts"].(map[string]any)["reuse-settings"].(map[string]any)
	if reuse == nil || reuse["h-keep-alive-period"] == nil {
		t.Errorf("hKeepAlivePeriod alias lost: %v", node["xhttp-opts"])
	}
}

// h-keep-alive-period follows upstream normalizeXhttpReuse: integer or /^-?\d+$/ string.
func TestXHTTPParityKeepAliveInteger(t *testing.T) {
	for _, raw := range []any{"+5", " 5", "5s", "", 1.5} {
		if _, err := mapXHTTPReuse(map[string]any{"hKeepAlivePeriod": raw}); err == nil {
			t.Errorf("hKeepAlivePeriod %#v: want error", raw)
		}
	}
	for raw, want := range map[any]int{"-1": -1, "30": 30, float64(15): 15} {
		got, err := mapXHTTPReuse(map[string]any{"hKeepAlivePeriod": raw, "maxConnections": "4-2"})
		if err != nil {
			t.Fatalf("hKeepAlivePeriod %#v: %v", raw, err)
		}
		if got["h-keep-alive-period"] != want || got["max-connections"] != "2-4" {
			t.Errorf("hKeepAlivePeriod %#v: got %v", raw, got)
		}
	}
}

// noGRPCHeader is assigned raw and validated by the final normalize, as upstream.
func TestXHTTPParityNoGRPCHeader(t *testing.T) {
	cases := []struct {
		query   url.Values
		want    any
		wantErr bool
	}{
		{url.Values{"noGRPCHeader": {"maybe"}}, nil, true},
		{url.Values{"noGRPCHeader": {"true"}}, true, false},
		{url.Values{"no-grpc-header": {"false"}, "noGRPCHeader": {"1"}}, true, false},
		{url.Values{"noGrpcHeader": {"true"}}, true, false},
		{url.Values{"no_grpc_header": {"0"}}, false, false},
	}
	for _, tc := range cases {
		node := map[string]any{}
		err := applyXHTTPTransport(node, "/", "", tc.query)
		if tc.wantErr {
			if err == nil {
				t.Errorf("%v: want error, got %v", tc.query, node["xhttp-opts"])
			}
			continue
		}
		if err != nil {
			t.Fatalf("%v: %v", tc.query, err)
		}
		if got := node["xhttp-opts"].(map[string]any)["no-grpc-header"]; got != tc.want {
			t.Errorf("%v: got %#v, want %#v", tc.query, got, tc.want)
		}
	}
}

// Upstream mapDownload: port is an integer or /^\d+$/ string; alpn null means [].
func TestXHTTPParityDownloadPortAlpn(t *testing.T) {
	cases := []struct {
		download string
		wantErr  bool
	}{
		{`{"address":"d.example.com","port":443}`, false},
		{`{"address":"d.example.com","port":"443"}`, false},
		{`{"address":"d.example.com","port":"-443"}`, true},
		{`{"address":"d.example.com","port":"+443"}`, true},
		{`{"address":"d.example.com","port":"4 43"}`, true},
		{`{"address":"d.example.com","port":443.5}`, true},
		{`{"address":"d.example.com","port":443,"security":"tls","tlsSettings":{"alpn":null}}`, false},
		{`{"address":"d.example.com","port":443,"security":"tls","tlsSettings":{"alpn":"h2"}}`, true},
	}
	for _, tc := range cases {
		node := map[string]any{}
		err := applyXHTTPTransport(node, "/", "", url.Values{"downloadSettings": {tc.download}})
		if (err != nil) != tc.wantErr {
			t.Errorf("%s: err=%v wantErr=%v", tc.download, err, tc.wantErr)
			continue
		}
		if err != nil {
			continue
		}
		dl, _ := node["xhttp-opts"].(map[string]any)["download-settings"].(map[string]any)
		if dl["port"] != 443 {
			t.Errorf("%s: port=%#v", tc.download, dl["port"])
		}
		if alpn, ok := dl["alpn"].([]any); !ok || len(alpn) != 0 {
			t.Errorf("%s: alpn=%#v", tc.download, dl["alpn"])
		}
	}
}

// Upstream normalizeRealityShortId: strings and numbers are kept, other types are dropped.
func TestXHTTPParityDownloadShortIDTypes(t *testing.T) {
	key := ""
	for len(key) < 43 {
		key += "A"
	}
	cases := []struct {
		shortID string
		want    any
	}{
		{`null`, nil},
		{`true`, nil},
		{`{}`, nil},
		{`"abcd"`, "abcd"},
		{`1234`, "1234"},
	}
	for _, tc := range cases {
		download := `{"address":"d.example.com","port":443,"security":"reality","realitySettings":{"publicKey":"` + key + `","shortId":` + tc.shortID + `}}`
		node := map[string]any{}
		if err := applyXHTTPTransport(node, "/", "", url.Values{"downloadSettings": {download}}); err != nil {
			t.Errorf("shortId %s: %v", tc.shortID, err)
			continue
		}
		dl, _ := node["xhttp-opts"].(map[string]any)["download-settings"].(map[string]any)
		reality, _ := dl["reality-opts"].(map[string]any)
		if got := reality["short-id"]; got != tc.want {
			t.Errorf("shortId %s: got %#v, want %#v", tc.shortID, got, tc.want)
		}
	}
}

// Upstream picks tls.password || tls.publicKey by JS truthiness, then trims a string-only key.
func TestXHTTPParityDownloadRealityKey(t *testing.T) {
	key := ""
	for len(key) < 43 {
		key += "A"
	}
	other := ""
	for len(other) < 43 {
		other += "B"
	}
	cases := []struct {
		settings string
		want     string
	}{
		{`{"password":null,"publicKey":"` + key + `"}`, key},
		{`{"password":false,"publicKey":"` + key + `"}`, key},
		{`{"password":0,"publicKey":"` + key + `"}`, key},
		{`{"password":"","publicKey":"` + key + `"}`, key},
		{`{"publicKey":" ` + key + ` "}`, key},
		{`{"password":"` + other + `","publicKey":"bad"}`, other},
		{`{"password":" ","publicKey":"` + key + `"}`, ""},
		{`{"password":123,"publicKey":"` + key + `"}`, ""},
	}
	for _, tc := range cases {
		download := `{"address":"d.example.com","port":443,"security":"reality","realitySettings":` + tc.settings + `}`
		node := map[string]any{}
		err := applyXHTTPTransport(node, "/", "", url.Values{"downloadSettings": {download}})
		if tc.want == "" {
			if err == nil {
				t.Errorf("%s: want error", tc.settings)
			}
			continue
		}
		if err != nil {
			t.Errorf("%s: %v", tc.settings, err)
			continue
		}
		dl, _ := node["xhttp-opts"].(map[string]any)["download-settings"].(map[string]any)
		reality, _ := dl["reality-opts"].(map[string]any)
		if got := reality["public-key"]; got != tc.want {
			t.Errorf("%s: got %#v", tc.settings, got)
		}
	}
}

// Upstream rejects a non-string uplinkHTTPMethod before uppercasing; "" becomes POST.
func TestXHTTPParityUplinkHTTPMethod(t *testing.T) {
	cases := []struct {
		value string
		want  string
	}{
		{`null`, ""},
		{`5`, ""},
		{`true`, ""},
		{`""`, "POST"},
		{`"put"`, "PUT"},
	}
	for _, tc := range cases {
		node := map[string]any{}
		err := applyXHTTPTransport(node, "/", "", url.Values{"extra": {`{"uplinkHTTPMethod":` + tc.value + `}`}})
		if tc.want == "" {
			if err == nil {
				t.Errorf("%s: want error", tc.value)
			}
			continue
		}
		if err != nil {
			t.Errorf("%s: %v", tc.value, err)
			continue
		}
		opts, _ := node["xhttp-opts"].(map[string]any)
		if got := opts["uplink-http-method"]; got != tc.want {
			t.Errorf("%s: got %#v, want %q", tc.value, got, tc.want)
		}
	}
}
