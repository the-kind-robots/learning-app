package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestRefreshManifestForEntries(t *testing.T) {
	dir := t.TempDir()
	entriesPath := filepath.Join(dir, "dictionary-entries.jsonl")
	manifestPath := filepath.Join(dir, "manifest.edn")

	if err := os.WriteFile(entriesPath, []byte("{\"_id\":\"a\"}\n{\"_id\":\"b\"}\n"), 0644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(manifestPath, []byte(`{:generated-at "2026-04-30T15:14:04Z", :files {"dictionary-entries.jsonl" {:count 1, :bytes 1, :sha256 "old"}, "surface-forms.jsonl" {:count 7, :bytes 8, :sha256 "keep"}}}`), 0644); err != nil {
		t.Fatal(err)
	}

	if err := refreshManifestForEntries(entriesPath); err != nil {
		t.Fatal(err)
	}

	raw, err := os.ReadFile(manifestPath)
	if err != nil {
		t.Fatal(err)
	}
	text := string(raw)

	for _, want := range []string{
		`:generated-at "2026-04-30T15:14:04Z"`,
		`:enriched-at "`,
		`"dictionary-entries.jsonl" {:count 2, :bytes 24, :sha256 "`,
		`"surface-forms.jsonl" {:count 7, :bytes 8, :sha256 "keep"}`,
	} {
		if !strings.Contains(text, want) {
			t.Fatalf("manifest missing %q:\n%s", want, text)
		}
	}
	if strings.Contains(text, `:sha256 "old"`) {
		t.Fatalf("manifest kept stale entry hash:\n%s", text)
	}
}

func TestRunRefreshesManifestWhenNothingToDo(t *testing.T) {
	dir := t.TempDir()
	entriesPath := filepath.Join(dir, "dictionary-entries.jsonl")
	manifestPath := filepath.Join(dir, "manifest.edn")
	checkpointPath := filepath.Join(dir, ".enrich_checkpoint.json")

	if err := os.WriteFile(entriesPath, []byte("{\"_id\":\"a\",\"translation\":[{\"lang\":\"ru\",\"value\":\"а\"}]}\n"), 0644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(manifestPath, []byte(`{:generated-at "2026-04-30T15:14:04Z", :files {"dictionary-entries.jsonl" {:count 9, :bytes 9, :sha256 "stale"}}}`), 0644); err != nil {
		t.Fatal(err)
	}

	updates := make(chan snapshot, 2)
	code, err := run(config{
		input:      entriesPath,
		output:     entriesPath,
		checkpoint: checkpointPath,
		batch:      1,
		priority:   "rank",
	}, updates)
	if err != nil {
		t.Fatal(err)
	}
	if code != 0 {
		t.Fatalf("run returned code %d", code)
	}

	raw, err := os.ReadFile(manifestPath)
	if err != nil {
		t.Fatal(err)
	}
	text := string(raw)
	for _, want := range []string{
		`"dictionary-entries.jsonl" {:count 1, :bytes 55, :sha256 "`,
		`:enriched-at "`,
	} {
		if !strings.Contains(text, want) {
			t.Fatalf("manifest missing %q:\n%s", want, text)
		}
	}
	if strings.Contains(text, `:sha256 "stale"`) {
		t.Fatalf("manifest kept stale hash:\n%s", text)
	}
}
