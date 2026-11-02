package towerparse

import (
	"errors"
	"io"
	"reflect"
	"sort"
	"strings"
	"testing"

	"github.com/Coming404/ohmyfly/native-core/internal/flight"
	"go.yaml.in/yaml/v3"
)

const yamlTestNode = "{name: a, type: ss, server: a.example, port: 443}"

// scanYAMLTest exercises the fallback scanner on its own; parseYAML only reaches it when whole-document loading fails.
func scanYAMLTest(t *testing.T, input string) ([]flight.Flight, Stats) {
	t.Helper()
	return collectYAMLTest(t, scanYAML, input)
}

func parseYAMLTest(t *testing.T, input string) ([]flight.Flight, Stats) {
	t.Helper()
	return collectYAMLTest(t, parseYAML, input)
}

func collectYAMLTest(t *testing.T, parse func(io.Reader, func(flight.Flight) error, Stats) (Stats, error), input string) ([]flight.Flight, Stats) {
	t.Helper()
	var nodes []flight.Flight
	stats, err := parse(strings.NewReader(input), func(node flight.Flight) error {
		nodes = append(nodes, node)
		return nil
	}, Stats{})
	if err != nil {
		t.Fatalf("scan: %v", err)
	}
	return nodes, stats
}

func assertYAMLMatchesDocument(t *testing.T, input string) {
	t.Helper()
	var document struct {
		Proxies   []map[string]any          `yaml:"proxies"`
		Providers map[string]map[string]any `yaml:"proxy-providers"`
	}
	// Upstream turns every tab into two spaces before loading, block scalars included.
	normalized := []byte(strings.ReplaceAll(input, "\t", "  "))
	var root map[string]any
	if err := yaml.Unmarshal(normalized, &document); err != nil {
		t.Fatalf("invalid legal fixture: %v", err)
	}
	if err := yaml.Unmarshal(normalized, &root); err != nil {
		t.Fatalf("invalid legal fixture root: %v", err)
	}
	// Without a proxies list upstream treats the whole root as one node, which fails validation.
	_, hasList := root["proxies"].([]any)
	var want []flight.Flight
	for _, record := range document.Proxies {
		node, err := flight.Normalize(record)
		if err != nil {
			t.Fatal(err)
		}
		node.SourceFormat = string(FormatYAML)
		want = append(want, node)
	}
	candidates, skipped := len(want), 0
	if !hasList {
		candidates, skipped = 1, 1
	}
	got, stats := parseYAMLTest(t, input)
	if !reflect.DeepEqual(got, want) || stats.Parsed != len(want) || stats.Skipped != skipped || stats.Candidates != candidates {
		t.Errorf("nodes=%+v want=%+v stats=%+v", got, want, stats)
	}
	var providers []ProxyProvider
	for name, fields := range document.Providers {
		url, _ := fields["url"].(string)
		providers = append(providers, ProxyProvider{Name: name, URL: url, Fields: fields})
	}
	sort.Slice(providers, func(i, j int) bool { return providers[i].Name < providers[j].Name })
	sort.Slice(stats.ProxyProviders, func(i, j int) bool { return stats.ProxyProviders[i].Name < stats.ProxyProviders[j].Name })
	if !reflect.DeepEqual(stats.ProxyProviders, providers) {
		t.Errorf("providers=%+v want=%+v", stats.ProxyProviders, providers)
	}
}

func assertYAMLRecovery(t *testing.T, input, names, reason string, skips int) {
	t.Helper()
	var document any
	if err := yaml.Unmarshal([]byte(input), &document); err == nil {
		t.Fatal("malformed fixture unexpectedly valid")
	}
	nodes, stats := scanYAMLTest(t, input)
	var got []string
	for _, node := range nodes {
		got = append(got, node.Name)
	}
	if strings.Join(got, ",") != names || stats.Skipped != skips || stats.SkippedByReason[reason] != skips {
		t.Errorf("names=%v stats=%+v; want names=%s skips=%d reason=%q", got, stats, names, skips, reason)
	}
}

func TestYAMLTabIndentRecovery(t *testing.T) {
	for name, bad := range map[string]string{
		"item":  "\t- {name: bad, type: ss, server: bad.example, port: 1}\n",
		"field": "  - name: bad\n\ttype: ss\n    server: bad.example\n    port: 1\n",
	} {
		t.Run(name, func(t *testing.T) {
			assertYAMLRecovery(t, "proxies:\n  - "+yamlTestNode+"\n"+bad+"  - {name: b, type: trojan, server: b.example, port: 2}\n", "a,b", "yaml tab indentation", 1)
		})
	}
}

func TestYAMLRootKeys(t *testing.T) {
	for name, input := range map[string]string{
		"indented": "  proxies:\n    - " + yamlTestNode + "\n  rules: []\n",
		"quoted":   "\"proxies\":\n  - " + yamlTestNode + "\n'proxy-providers':\n  'airport: one': {url: https://example.invalid/list}\n",
		"bom":      "\ufeffproxies:\n  - " + yamlTestNode + "\n",
	} {
		t.Run(name, func(t *testing.T) { assertYAMLMatchesDocument(t, input) })
	}
	// Detection strips the BOM only from its peek, not from the input reader.
	stats, err := Parse(strings.NewReader("\ufeffproxies:\n  - "+yamlTestNode), func(flight.Flight) error { return nil })
	if err != nil || stats.Parsed != 1 {
		t.Fatalf("public BOM parse: %+v %v", stats, err)
	}
}

func TestYAMLDocumentMarkers(t *testing.T) {
	for _, ending := range []string{"---", "...", "... # done"} {
		t.Run(ending, func(t *testing.T) {
			assertYAMLMatchesDocument(t, "---\nproxies:\n- "+yamlTestNode+"\n"+ending+"\n")
		})
	}
}

func TestYAMLListIndentation(t *testing.T) {
	assertYAMLRecovery(t, "proxies:\n  - "+yamlTestNode+"\n    - {name: b, type: ss, server: b.example, port: 2}\n  - {name: c, type: ss, server: c.example, port: 3}\n", "a,b,c", "yaml inconsistent list indentation", 1)
	assertYAMLRecovery(t, "proxies:\n  - "+yamlTestNode+"\n    - ambiguous\n  - {name: b, type: ss, server: b.example, port: 2}\n", "a,b", "yaml ambiguous list indentation", 1)
	for name, input := range map[string]string{
		"nested":      "proxies:\n- name: a\n  type: ss\n  server: a.example\n  port: 443\n  alpn:\n    - h2\n    - http/1.1\n",
		"indentless":  "proxies:\n- name: a\n  type: ss\n  server: a.example\n  port: 443\n  alpn:\n  - h2\n  - http/1.1\n",
		"nested_maps": "proxies:\n- name: a\n  type: ss\n  server: a.example\n  port: 443\n  extra:\n    - {name: child}\n    - name: second\n",
	} {
		t.Run(name, func(t *testing.T) { assertYAMLMatchesDocument(t, input) })
	}
}

func TestYAMLFlowCollections(t *testing.T) {
	for name, input := range map[string]string{
		"inline":    "proxies: [" + yamlTestNode + ", {name: b, type: vmess, server: b.example, port: 2}]\n",
		"multiline": "proxies: [\n" + yamlTestNode + ", # ], } ignored\n{name: 'b]#{,', type: vmess, server: b.example, port: 2, extra: [\"a\\\"],\", c]}\n]\n",
		"empty":     "proxies: [] # empty\nproxy-providers: {}\n",
		"providers": "proxy-providers: {\n'a: one': {url: 'https://example.invalid/a#x', health-check: {enable: true}},\nb: {url: https://example.invalid/b}\n}\nproxies: [" + yamlTestNode + "]\n",
		"flow_item": "proxies:\n  - {name: a,\n     type: ss, server: a.example,\n     port: 443}\n",
	} {
		t.Run(name, func(t *testing.T) { assertYAMLMatchesDocument(t, input) })
	}
}

func TestYAMLScalarAndLineEndings(t *testing.T) {
	base := "proxies:\n- name: a\n  type: ss\n  server: a.example\n  port: 443\n  note: |\n    proxies:\n    - {name: not-a-node}\n  folded: >-\n    - name: still-text\n    proxies: []\n"
	for name, input := range map[string]string{
		"scalar":       base,
		"crlf":         strings.ReplaceAll(base, "\n", "\r\n"),
		"cr":           strings.ReplaceAll(base, "\n", "\r"),
		"eof":          "proxies:\n  - " + yamlTestNode,
		"comments":     "proxies: # empty\n  # comment\n",
		"other_scalar": "description: |\n  proxies:\n  - fake\nproxies:\n- " + yamlTestNode + "\n",
	} {
		t.Run(name, func(t *testing.T) { assertYAMLMatchesDocument(t, input) })
	}
}

func TestYAMLNestedNamesAreNotRoot(t *testing.T) {
	input := "proxy-groups:\n- name: group\n  type: select\n  proxies:\n  - not-a-node\n  - {name: fake, type: ss, server: fake.example, port: 1}\nproxies:\n- " + yamlTestNode + "\n"
	assertYAMLMatchesDocument(t, input)
	var indented strings.Builder
	for _, line := range strings.Split(strings.TrimSuffix(input, "\n"), "\n") {
		indented.WriteString("  " + line + "\n")
	}
	assertYAMLMatchesDocument(t, indented.String())
	assertYAMLMatchesDocument(t, "  proxy-groups:\n    - name: group\n      proxies:\n        - {name: fake, type: ss, server: fake.example, port: 1}\n")
}

func TestYAMLDuplicateRoots(t *testing.T) {
	input := "proxies:\n- " + yamlTestNode + "\nproxies:\n- {name: b, type: ss, server: b.example, port: 2}\n"
	assertYAMLRecovery(t, input, "a,b", "yaml duplicate root key: proxies", 1)
}

func TestYAMLCrossItemAliases(t *testing.T) {
	input := "base: &base {type: ss, server: a.example, port: 443}\nproxies:\n- name: alias\n  <<: *base\n- " + yamlTestNode + "\n"
	var document any
	if err := yaml.Unmarshal([]byte(input), &document); err != nil {
		t.Fatal(err)
	}
	nodes, stats := scanYAMLTest(t, input)
	if len(nodes) != 1 || nodes[0].Name != "a" || stats.SkippedByReason["yaml cross-item alias unsupported"] != 1 {
		t.Fatalf("nodes=%+v stats=%+v", nodes, stats)
	}
	assertYAMLMatchesDocument(t, "proxies:\n- name: a\n  type: ss\n  server: a.example\n  port: 443\n  extra: &local {x: one}\n  copy: *local\n")
}

func TestYAMLProviderIndentation(t *testing.T) {
	assertYAMLMatchesDocument(t, "  'proxy-providers':\n    'airport: a':\n      url: https://example.invalid/a\n      health-check:\n        enable: true\n    b:\n      url: https://example.invalid/b\n")
	input := "proxy-providers:\n  a: {url: https://example.invalid/a}\n\tbad: {url: https://example.invalid/bad}\n  b: {url: https://example.invalid/b}\n"
	_, stats := scanYAMLTest(t, input)
	if len(stats.ProxyProviders) != 2 || stats.SkippedByReason["yaml tab indentation"] != 1 {
		t.Fatalf("%+v", stats)
	}
}

func TestYAMLBoundedBuffers(t *testing.T) {
	input := "proxies:\n- name: too-big\n  type: ss\n  server: a.example\n  port: 443\n  note: |\n" + strings.Repeat("    "+strings.Repeat("x", 1020)+"\n", maxLineLength/1024+1) + "- " + yamlTestNode + "\n"
	nodes, stats := scanYAMLTest(t, input)
	if len(nodes) != 1 || stats.SkippedByReason["yaml item too large"] != 1 {
		t.Fatalf("nodes=%d stats=%+v", len(nodes), stats)
	}
}

func TestYAMLReadAndEmitErrors(t *testing.T) {
	want := errors.New("stop")
	stats, err := parseYAML(strings.NewReader("proxies: ["+yamlTestNode+"]"), func(flight.Flight) error { return want }, Stats{})
	if !errors.Is(err, want) || stats.Parsed != 0 {
		t.Fatalf("emit error: %v %+v", err, stats)
	}
	_, err = parseYAML(io.MultiReader(strings.NewReader("proxies:\n"), yamlErrorReader{want}), func(flight.Flight) error { return nil }, Stats{})
	if err == nil {
		t.Fatal("lost read error")
	}
}

type yamlErrorReader struct{ err error }

func (r yamlErrorReader) Read([]byte) (int, error) { return 0, r.err }

func TestYAMLAdditionalLegalBoundaries(t *testing.T) {
	for name, input := range map[string]string{
		"first_document":        "---\nproxies: [" + yamlTestNode + "]\n---\nproxies: [{name: second, type: ss, server: second.example, port: 2}]\n",
		"flow_nested_root_name": "proxies: [{name: a, type: ss, server: a.example, port: 443,\nproxies: [not-a-node]}]\n",
		"scalar_tab_content":    "proxies:\n- name: a\n  type: ss\n  server: a.example\n  port: 443\n  note: |2\n    \tfirst\n    - literal\n",
	} {
		t.Run(name, func(t *testing.T) { assertYAMLMatchesDocument(t, input) })
	}
}

func TestYAMLFlowBufferLimit(t *testing.T) {
	input := "proxy-providers: {\n" + strings.Repeat("# "+strings.Repeat("x", 1021)+"\n", maxLineLength/1024+1) + "}\nproxies:\n- " + yamlTestNode + "\n"
	nodes, stats := scanYAMLTest(t, input)
	if len(nodes) != 1 || stats.SkippedByReason["yaml item too large"] != 1 {
		t.Fatalf("nodes=%d stats=%+v", len(nodes), stats)
	}
}

func TestDetectQuotedYAMLRootKeys(t *testing.T) {
	for _, input := range []string{
		"\"proxies\": [" + yamlTestNode + "]\n",
		"'proxies':\n  - " + yamlTestNode + "\n",
		"  \"proxy-providers\":\n    p: {url: https://example.invalid/list}\n",
		"'proxy-providers': {p: {url: https://example.invalid/list}}\n",
	} {
		if got := DetectFormat([]byte(input)); got != FormatYAML {
			t.Errorf("DetectFormat(%q) = %q, want %q", input, got, FormatYAML)
		}
	}
	input := "\"proxies\": [" + yamlTestNode + "]\n'proxy-providers':\n  p: {url: https://example.invalid/list}\n"
	stats, err := Parse(strings.NewReader(input), func(flight.Flight) error { return nil })
	if err != nil || stats.Parsed != 1 || len(stats.ProxyProviders) != 1 {
		t.Fatalf("public quoted-root parse: %+v %v", stats, err)
	}
}
