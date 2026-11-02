package sqlite_test

import (
	"context"
	"errors"
	"path/filepath"
	"strings"
	"testing"

	"github.com/Coming404/ohmyfly/native-core/internal/sqlite"
)

func TestOpen(t *testing.T) {
	t.Parallel()
	cases := []struct {
		name string
		dsn  string
	}{
		{name: "memory", dsn: ":memory:"},
		{name: "path", dsn: filepath.Join(t.TempDir(), "test.sqlite")},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			t.Parallel()
			ctx := t.Context()
			db, err := sqlite.Open(ctx, tc.dsn)
			if err != nil {
				t.Fatalf("open database: %v", err)
			}
			t.Cleanup(func() {
				if err := db.Close(); err != nil {
					t.Errorf("close database: %v", err)
				}
			})
			conn, err := db.Conn(ctx)
			if err != nil {
				t.Fatalf("acquire connection: %v", err)
			}
			t.Cleanup(func() {
				if err := conn.Close(); err != nil {
					t.Errorf("close connection: %v", err)
				}
			})
			if _, err := conn.ExecContext(ctx, "CREATE TEMP TABLE sample (id INTEGER PRIMARY KEY, value TEXT NOT NULL)"); err != nil {
				t.Fatalf("create temporary table: %v", err)
			}
			const want = "O'Hare'); DROP TABLE sample; --"
			if _, err := conn.ExecContext(ctx, "INSERT INTO sample (id, value) VALUES (?, ?)", 42, want); err != nil {
				t.Fatalf("insert value: %v", err)
			}
			var got string
			if err := conn.QueryRowContext(ctx, "SELECT value FROM sample WHERE id = ?", 42).Scan(&got); err != nil {
				t.Fatalf("query value: %v", err)
			}
			if got != want {
				t.Errorf("value = %q, want %q", got, want)
			}
		})
	}
}

func TestOpenClose(t *testing.T) {
	t.Parallel()
	ctx := t.Context()
	db, err := sqlite.Open(ctx, ":memory:")
	if err != nil {
		t.Fatalf("open database: %v", err)
	}
	if err := db.Close(); err != nil {
		t.Fatalf("close database: %v", err)
	}
	if err := db.PingContext(ctx); err == nil {
		t.Error("ping succeeded after close")
	}
}

func TestOpenCanceledContext(t *testing.T) {
	t.Parallel()
	ctx, cancel := context.WithCancel(t.Context())
	cancel()
	db, err := sqlite.Open(ctx, ":memory:")
	if db != nil {
		if closeErr := db.Close(); closeErr != nil {
			t.Errorf("close unexpected database: %v", closeErr)
		}
		t.Error("Open returned a database for a canceled context")
	}
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("Open error = %v, want context.Canceled", err)
	}
	if !strings.Contains(err.Error(), "ping sqlite database") {
		t.Errorf("error lacks operation context: %v", err)
	}
}

func TestOpenError(t *testing.T) {
	t.Parallel()
	cases := []struct {
		name string
		dsn  string
	}{
		{name: "invalid mode", dsn: "file::memory:?mode=invalid"},
		{name: "directory path", dsn: t.TempDir()},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			t.Parallel()
			db, err := sqlite.Open(t.Context(), tc.dsn)
			if db != nil {
				if closeErr := db.Close(); closeErr != nil {
					t.Errorf("close unexpected database: %v", closeErr)
				}
				t.Error("Open returned a database for an invalid DSN/path")
			}
			if err == nil {
				t.Fatal("Open succeeded for an invalid DSN/path")
			}
			if errors.Unwrap(err) == nil {
				t.Errorf("error does not wrap its cause: %v", err)
			}
			if !strings.Contains(err.Error(), "sqlite database") {
				t.Errorf("error lacks operation context: %v", err)
			}
		})
	}
}
