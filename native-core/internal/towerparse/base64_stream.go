package towerparse

import (
	"encoding/base64"
	"encoding/hex"
	"errors"
	"io"
)

// base64StreamReader removes wrapping whitespace/padding and decodes complete
// quartets as they arrive. It keeps at most a small quartet and percent escape.
type base64StreamReader struct {
	source    io.Reader
	raw       []byte
	decoded   []byte
	pending   []byte
	percent   [2]byte
	percentN  int
	sourceErr error
	terminal  error
}

func newBase64StreamReader(source io.Reader) io.Reader {
	return &base64StreamReader{source: source, raw: make([]byte, 32*1024)}
}

func (r *base64StreamReader) Read(out []byte) (int, error) {
	if len(out) == 0 {
		return 0, nil
	}
	for len(r.decoded) == 0 && r.terminal == nil {
		if r.sourceErr != nil {
			r.finish()
			break
		}
		n, err := r.source.Read(r.raw)
		for _, char := range r.raw[:n] {
			r.feed(char)
			if r.terminal != nil {
				break
			}
		}
		if err != nil {
			r.sourceErr = err
		}
		if n == 0 && err == nil {
			continue
		}
	}
	if len(r.decoded) > 0 {
		n := copy(out, r.decoded)
		r.decoded = r.decoded[n:]
		return n, nil
	}
	if r.terminal != nil {
		return 0, r.terminal
	}
	return 0, io.EOF
}

func (r *base64StreamReader) feed(char byte) {
	if r.percentN > 0 {
		r.percent[r.percentN-1] = char
		r.percentN++
		if r.percentN == 3 {
			var decoded [1]byte
			if _, err := hex.Decode(decoded[:], r.percent[:]); err != nil {
				r.terminal = errors.New("invalid base64 percent escape")
				return
			}
			r.percentN = 0
			r.feed(decoded[0])
		}
		return
	}
	if char == '%' {
		r.percentN = 1
		return
	}
	if char == ' ' || char == '\t' || char == '\r' || char == '\n' || char == '=' {
		return
	}
	if char == '-' {
		char = '+'
	} else if char == '_' {
		char = '/'
	}
	r.pending = append(r.pending, char)
	if len(r.pending) == 4 {
		var block [3]byte
		n, err := base64.RawStdEncoding.Decode(block[:], r.pending)
		if err != nil {
			r.terminal = errors.New("invalid base64 content")
			return
		}
		r.decoded = append(r.decoded, block[:n]...)
		r.pending = r.pending[:0]
	}
}

func (r *base64StreamReader) finish() {
	if r.terminal != nil {
		return
	}
	if r.percentN != 0 || len(r.pending) == 1 {
		r.terminal = errors.New("invalid base64 length")
		return
	}
	if len(r.pending) > 0 {
		var block [3]byte
		n, err := base64.RawStdEncoding.Decode(block[:], r.pending)
		if err != nil {
			r.terminal = errors.New("invalid base64 tail")
			return
		}
		r.decoded = append(r.decoded, block[:n]...)
		r.pending = r.pending[:0]
	}
	if r.sourceErr != nil && r.sourceErr != io.EOF {
		r.terminal = r.sourceErr
		return
	}
	r.terminal = io.EOF
}
