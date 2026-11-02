package flight

// FingerprintVersion identifies the canonical content representation used for deduplication.
const FingerprintVersion = 1

// Flight is one normalized Mihomo proxy entry. Name and source metadata are kept
// outside Fields so neither is part of the content fingerprint.
type Flight struct {
	Name         string
	Type         string
	Server       string
	Port         int
	Fields       map[string]any
	SourceFormat string
	FromBase64   bool
}
