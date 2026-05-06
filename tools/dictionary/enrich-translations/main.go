package main

import (
	"bufio"
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"math"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"
	"unicode"

	"github.com/charmbracelet/bubbles/viewport"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

const (
	defaultAPIURL = "https://openrouter.ai/api/v1/chat/completions"
	defaultModel  = "google/gemini-2.5-flash-lite"
	stateVersion  = 2
)

var transientStatus = map[int]bool{
	408: true, 409: true, 425: true, 429: true,
	500: true, 502: true, 503: true, 504: true,
}

var posLabels = map[string]string{
	"noun": "сущ.", "verb": "гл.", "adj": "прил.", "adv": "нареч.",
	"phrase": "фраза", "prep": "предл.", "conj": "союз", "pron": "мест.",
	"num": "числ.", "intj": "межд.", "particle": "частица",
}

var labelMap = map[string]string{
	"разг.": "colloquial", "разг": "colloquial",
	"книжн.": "bookish", "книжн": "bookish",
	"перен.": "figurative", "перен": "figurative",
	"устар.": "archaic", "устар": "archaic",
	"тех.": "technical", "тех": "technical", "техническое": "technical", "технический": "technical",
	"юр.": "legal", "юр": "legal", "юрид.": "legal", "юрид": "legal", "юридическое": "legal",
	"офиц.": "formal", "офиц": "formal",
	"поэт.": "poetic", "поэт": "poetic",
	"спец.": "specialized", "спец": "specialized",
	"мед.": "medical", "мед": "medical",
}

type config struct {
	input                    string
	output                   string
	limit                    int
	batch                    int
	minRank                  int
	priority                 string
	checkpoint               string
	failLog                  string
	crashLog                 string
	recentLog                string
	recentLimit              int
	dryRun                   bool
	delay                    time.Duration
	maxRetries               int
	requestTimeout           time.Duration
	maxBackoff               time.Duration
	maxDelay                 time.Duration
	maxTokensPerEntry        int
	rateLimitBuffer          time.Duration
	allowFreeTraining        bool
	providerOnly             []string
	disableProviderFallbacks bool
	apiURL                   string
	apiKey                   string
	model                    string
	models                   []string
}

type entry map[string]any

type state struct {
	Version             int      `json:"version"`
	InputPath           string   `json:"input_path"`
	OutputPath          string   `json:"output_path"`
	StartedAt           string   `json:"started_at"`
	UpdatedAt           string   `json:"updated_at"`
	Requests            int      `json:"requests"`
	BatchesCompleted    int      `json:"batches_completed"`
	InitialMissing      int      `json:"initial_missing,omitempty"`
	EntriesEnriched     int      `json:"entries_enriched"`
	EntriesFailed       int      `json:"entries_failed"`
	Remaining           int      `json:"remaining"`
	LastBatchIDs        []string `json:"last_batch_ids"`
	LastBatchWords      []string `json:"last_batch_words"`
	LastError           string   `json:"last_error,omitempty"`
	LastResponseSnippet string   `json:"last_response_snippet,omitempty"`
	LastModel           string   `json:"last_model,omitempty"`
	LegacyDoneCount     int      `json:"legacy_done_count,omitempty"`
}

type modelSpec struct {
	Model          string
	ProviderOnly   []string
	AllowFallbacks *bool
	Label          string
}

type apiResult struct {
	OK               bool
	Body             string
	Status           int
	RetryAfter       time.Duration
	RateLimitResetAt time.Time
	Error            string
	ModelLabel       string
	Retryable        bool
	RawSnippet       string
}

type openRouterPayloadError struct {
	Status int
	Body   string
}

func (err *openRouterPayloadError) Error() string {
	return err.Body
}

type recentRow struct {
	Ts           string `json:"ts,omitempty"`
	BatchIndex   int    `json:"batch_index,omitempty"`
	Status       string `json:"status"`
	Word         string `json:"word"`
	POS          string `json:"pos"`
	Senses       string `json:"senses"`
	Model        string `json:"model"`
	Translations string `json:"translations"`
}

type snapshot struct {
	Mode           string
	Total          int
	Missing        int
	InitialMissing int
	Selected       int
	Output         string
	Remaining      int
	Requests       int
	Enriched       int
	Failed         int
	Batches        int
	Batch          string
	Model          string
	Delay          string
	Backoff        string
	WaitUntil      string
	WaitFor        string
	Status         string
	Words          string
	LastError      string
	LastResponse   string
	Priority       string
	Privacy        string
	Elapsed        string
	ETA            string
	Rate           string
	Recent         []recentRow
	Done           bool
	ExitCode       int
	Err            string
}

type updateMsg snapshot
type doneMsg snapshot
type tickMsg struct{}

type tuiModel struct {
	ch       <-chan snapshot
	vp       viewport.Model
	snap     snapshot
	width    int
	height   int
	ready    bool
	finished bool
}

var (
	titleStyle = lipgloss.NewStyle().Foreground(lipgloss.Color("39")).Bold(true)
	okStyle    = lipgloss.NewStyle().Foreground(lipgloss.Color("42"))
	warnStyle  = lipgloss.NewStyle().Foreground(lipgloss.Color("214"))
	errStyle   = lipgloss.NewStyle().Foreground(lipgloss.Color("196"))
	mutedStyle = lipgloss.NewStyle().Foreground(lipgloss.Color("244"))
)

func main() {
	cfg := parseFlags()
	if cfg.input == "" {
		fmt.Fprintln(os.Stderr, "--input required")
		os.Exit(2)
	}
	if cfg.output == "" {
		cfg.output = cfg.input
	}
	if !cfg.dryRun && cfg.apiKey == "" {
		fmt.Fprintln(os.Stderr, "OPENROUTER_API_KEY missing")
		os.Exit(2)
	}

	updates := make(chan snapshot, 8)
	go func() {
		defer close(updates)
		code, err := run(cfg, updates)
		if err != nil {
			_ = logCrash(cfg.crashLog, err)
			updates <- snapshot{Done: true, ExitCode: code, Err: err.Error(), Status: "crashed"}
			return
		}
		updates <- snapshot{Done: true, ExitCode: code, Status: "done"}
	}()

	prog := tea.NewProgram(newTUI(updates), tea.WithAltScreen())
	result, err := prog.Run()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(2)
	}
	if model, ok := result.(tuiModel); ok && model.snap.ExitCode != 0 {
		os.Exit(model.snap.ExitCode)
	}
}

func executableDir() string {
	exe, err := os.Executable()
	if err != nil {
		return "."
	}
	resolved, err := filepath.EvalSymlinks(exe)
	if err != nil {
		return filepath.Dir(exe)
	}
	return filepath.Dir(resolved)
}

func parseFlags() config {
	var cfg config
	var delay, requestTimeout, maxBackoff, maxDelay, rateLimitBuffer float64
	var providerOnly string
	binDir := executableDir()
	flag.StringVar(&cfg.input, "input", "", "")
	flag.StringVar(&cfg.output, "output", "", "")
	flag.IntVar(&cfg.limit, "limit", 0, "")
	flag.IntVar(&cfg.batch, "batch", 8, "")
	flag.IntVar(&cfg.minRank, "min-rank", 0, "")
	flag.StringVar(&cfg.priority, "priority", "rank", "")
	flag.StringVar(&cfg.checkpoint, "checkpoint", filepath.Join(binDir, ".enrich_checkpoint.json"), "")
	flag.StringVar(&cfg.failLog, "fail-log", filepath.Join(binDir, ".enrich_failures.jsonl"), "")
	flag.StringVar(&cfg.crashLog, "crash-log", filepath.Join(binDir, ".enrich_crashes.jsonl"), "")
	flag.StringVar(&cfg.recentLog, "recent-log", filepath.Join(binDir, ".enrich_recent.jsonl"), "")
	flag.IntVar(&cfg.recentLimit, "recent-limit", 64, "")
	flag.BoolVar(&cfg.dryRun, "dry-run", false, "")
	flag.Float64Var(&delay, "delay", 3, "")
	flag.IntVar(&cfg.maxRetries, "max-retries", 6, "")
	flag.Float64Var(&requestTimeout, "request-timeout", 600, "")
	flag.Float64Var(&maxBackoff, "max-backoff", 90, "")
	flag.Float64Var(&maxDelay, "max-delay", 60, "")
	flag.IntVar(&cfg.maxTokensPerEntry, "max-tokens-per-entry", 220, "")
	flag.Float64Var(&rateLimitBuffer, "rate-limit-buffer", 10, "")
	flag.BoolVar(&cfg.allowFreeTraining, "allow-free-training", false, "")
	flag.StringVar(&providerOnly, "provider-only", "", "")
	flag.BoolVar(&cfg.disableProviderFallbacks, "disable-provider-fallbacks", false, "")
	flag.Parse()

	cfg.delay = seconds(delay)
	cfg.requestTimeout = seconds(requestTimeout)
	cfg.maxBackoff = seconds(maxBackoff)
	cfg.maxDelay = seconds(maxDelay)
	cfg.rateLimitBuffer = seconds(rateLimitBuffer)
	cfg.apiURL = env("OPENROUTER_API_URL", defaultAPIURL)
	cfg.apiKey = env("OPENROUTER_API_KEY", "")
	cfg.model = env("OPENROUTER_MODEL", defaultModel)
	cfg.models = splitCSV(os.Getenv("OPENROUTER_MODELS"))
	cfg.providerOnly = splitCSV(providerOnly)
	if cfg.batch <= 0 {
		cfg.batch = 1
	}
	if cfg.maxRetries <= 0 {
		cfg.maxRetries = 1
	}
	if cfg.recentLimit <= 0 {
		cfg.recentLimit = 64
	}
	return cfg
}

func seconds(v float64) time.Duration {
	return time.Duration(v * float64(time.Second))
}

func env(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}

func splitCSV(raw string) []string {
	if raw == "" {
		return nil
	}
	var out []string
	for _, item := range strings.Split(raw, ",") {
		item = strings.TrimSpace(item)
		if item != "" {
			out = append(out, item)
		}
	}
	return out
}

func run(cfg config, updates chan<- snapshot) (int, error) {
	source := cfg.input
	if cfg.output != cfg.input && fileExists(cfg.output) {
		source = cfg.output
	}
	entries, err := loadEntries(source)
	if err != nil {
		return 2, err
	}
	st, err := loadState(cfg.checkpoint, cfg.input, cfg.output)
	if err != nil {
		return 2, err
	}

	totalMissing := 0
	for _, item := range entries {
		if needsTranslation(item) {
			totalMissing++
		}
	}
	if st.InitialMissing == 0 {
		st.InitialMissing = totalMissing + st.EntriesEnriched
	}
	candidates := selectCandidates(entries, cfg)
	if cfg.limit > 0 && len(candidates) > cfg.limit {
		candidates = candidates[:cfg.limit]
	}
	if len(candidates) == 0 {
		if !cfg.dryRun {
			if err := refreshManifestForEntries(manifestRefreshTarget(cfg, source)); err != nil {
				return 2, err
			}
		}
		updates <- snapshot{Status: "nothing to do", Done: true}
		return 0, nil
	}

	entryByID := map[string]entry{}
	for _, item := range entries {
		entryByID[stringField(item, "_id")] = item
	}
	batches := makeBatches(candidates, cfg.batch)
	started := time.Now()
	delay := cfg.delay
	recent := make([]recentRow, 0, cfg.recentLimit)
	lastRequest := time.Time{}

	snap := snapshot{
		Mode: "live", Total: len(entries), Missing: totalMissing, InitialMissing: st.InitialMissing, Selected: len(candidates),
		Output: cfg.output, Remaining: len(candidates), Requests: st.Requests,
		Enriched: st.EntriesEnriched, Failed: st.EntriesFailed, Batches: st.BatchesCompleted,
		Batch: fmt.Sprintf("0/%d", len(batches)), Model: cfg.model, Delay: formatSeconds(delay),
		Backoff: "-", WaitUntil: "-", WaitFor: "-", Priority: cfg.priority, Privacy: privacyLabel(cfg),
		Status: "starting", Words: "-", LastError: st.LastError, LastResponse: st.LastResponseSnippet,
	}
	if cfg.dryRun {
		snap.Mode = "dry-run"
	}
	updates <- enrichEstimate(snap, started)

	for batchIndex, batch := range batches {
		if !lastRequest.IsZero() {
			sleepFor := delay - time.Since(lastRequest)
			if sleepFor > 0 {
				time.Sleep(sleepFor)
			}
		}
		snap.Batch = fmt.Sprintf("%d/%d", batchIndex+1, len(batches))
		snap.Words = batchWords(batch)
		snap.Status = "processing batch"
		snap.Backoff = "-"
		snap.Delay = formatSeconds(delay)
		updates <- enrichEstimate(snap, started)

		parsed, result := runBatch(cfg, batch, &snap, &delay, updates, started)
		if !cfg.dryRun {
			lastRequest = time.Now()
		}

		enrichedNow, failedNow := 0, 0
		failedEntries := batch
		if parsed != nil {
			failedEntries = nil
			for _, item := range batch {
				found := parsed[stringField(item, "_id")]
				if len(found) > 0 {
					entryByID[stringField(item, "_id")]["translation"] = found
					enrichedNow++
				} else {
					failedEntries = append(failedEntries, item)
					failedNow++
				}
			}
			delay = relaxDelay(delay, cfg.delay)
			snap.Delay = formatSeconds(delay)
		} else {
			failedNow = len(batch)
		}

		rows := recentRows(batch, parsed, snap.Model, batchIndex+1)
		recent = append(recent, rows...)
		if len(recent) > cfg.recentLimit {
			recent = recent[len(recent)-cfg.recentLimit:]
		}
		snap.Recent = append([]recentRow(nil), recent...)

		if !cfg.dryRun {
			for _, row := range rows {
				_ = appendJSONL(cfg.recentLog, row)
			}
			st.Requests = snap.Requests
			st.BatchesCompleted++
			st.EntriesEnriched += enrichedNow
			st.EntriesFailed += failedNow
			st.LastBatchIDs = entryIDs(batch)
			st.LastBatchWords = entryWords(batch)
			st.LastError = snap.LastError
			st.LastResponseSnippet = snap.LastResponse
			st.LastModel = snap.Model
			st.Remaining = max(0, snap.Remaining-len(batch))
			snap.Missing = max(0, snap.Missing-enrichedNow)
			if enrichedNow > 0 {
				if err := writeEntries(cfg.output, entries); err != nil {
					return 2, err
				}
				if err := refreshManifestForEntries(cfg.output); err != nil {
					return 2, err
				}
			}
			for _, item := range failedEntries {
				_ = appendJSONL(cfg.failLog, failPayload(item, batchIndex+1, batch, snap))
			}
			if err := saveState(cfg.checkpoint, st); err != nil {
				return 2, err
			}
			snap.Remaining = st.Remaining
			snap.Enriched = st.EntriesEnriched
			snap.Failed = st.EntriesFailed
			snap.Batches = st.BatchesCompleted
		} else {
			snap.Remaining = max(0, snap.Remaining-len(batch))
		}

		snap.Status = "batch done"
		updates <- enrichEstimate(snap, started)
		if parsed == nil && result != nil && !result.Retryable {
			return 2, nil
		}
	}
	if !cfg.dryRun {
		if err := refreshManifestForEntries(manifestRefreshTarget(cfg, source)); err != nil {
			return 2, err
		}
	}
	return 0, nil
}

func manifestRefreshTarget(cfg config, source string) string {
	if fileExists(cfg.output) {
		return cfg.output
	}
	return source
}

func runBatch(cfg config, batch []entry, snap *snapshot, delay *time.Duration, updates chan<- snapshot, started time.Time) (map[string][]map[string]any, *apiResult) {
	if cfg.dryRun {
		snap.LastResponse = buildPrompt(batch)
		return map[string][]map[string]any{}, nil
	}
	specs := orderedModelSpecs(cfg)
	attempt := 1
	for attempt <= cfg.maxRetries {
		var last *apiResult
		var retryAfter time.Duration
		var resetAt time.Time
		sawRetryable := false
		for modelIndex, spec := range specs {
			snap.Status = fmt.Sprintf("request attempt %d/%d, model %d/%d", attempt, cfg.maxRetries, modelIndex+1, len(specs))
			snap.Model = spec.Label
			updates <- enrichEstimate(*snap, started)
			result := callOpenRouter(cfg, batch, spec)
			snap.Requests++
			snap.Model = result.ModelLabel
			last = &result
			if result.OK && result.Body != "" {
				parsed, err := parseResponse(result.Body, batch)
				if err == nil {
					snap.Status = "batch parsed"
					snap.LastError = ""
					snap.LastResponse = ""
					updates <- enrichEstimate(*snap, started)
					return parsed, &result
				}
				result.OK = false
				result.Error = "parse error: " + err.Error()
				result.Retryable = true
				result.RawSnippet = shorten(result.Body, 240)
				last = &result
			}
			snap.LastResponse = result.RawSnippet
			if result.RetryAfter > retryAfter {
				retryAfter = result.RetryAfter
			}
			if !result.RateLimitResetAt.IsZero() && (resetAt.IsZero() || result.RateLimitResetAt.After(resetAt)) {
				resetAt = result.RateLimitResetAt
			}
			sawRetryable = sawRetryable || result.Retryable
			label := "request"
			if result.Status != 0 {
				label = fmt.Sprintf("HTTP %d", result.Status)
			}
			snap.LastError = fmt.Sprintf("%s: %s: %s", result.ModelLabel, label, result.Error)
			if result.Status == 429 {
				*delay = increaseDelay(*delay, cfg)
				snap.Delay = formatSeconds(*delay)
			}
			if result.Status == 401 {
				snap.Status = "hard failure"
				updates <- enrichEstimate(*snap, started)
				return nil, &result
			}
			if modelIndex+1 < len(specs) {
				snap.Status = "model failed, trying fallback"
				updates <- enrichEstimate(*snap, started)
			}
		}
		if last == nil {
			result := apiResult{Error: "no models configured", Retryable: false}
			return nil, &result
		}
		if !last.Retryable || !sawRetryable {
			snap.Status = "hard failure"
			updates <- enrichEstimate(*snap, started)
			return nil, last
		}
		if !resetAt.IsZero() {
			cooldown := time.Until(resetAt.Add(cfg.rateLimitBuffer))
			if cooldown > 0 {
				*delay = increaseDelay(*delay, cfg)
				snap.Delay = formatSeconds(*delay)
				waitWithTUI(cooldown, resetAt, "rate limit cooldown", snap, updates, started)
				continue
			}
		}
		if attempt == cfg.maxRetries {
			snap.Status = "retry budget exhausted"
			if last.Error != "" {
				snap.LastError = last.Error
			}
			updates <- enrichEstimate(*snap, started)
			return nil, last
		}
		backoff := backoffDelay(attempt, retryAfter, cfg)
		snap.Backoff = formatSeconds(backoff)
		snap.Status = fmt.Sprintf("backing off before retry %d", attempt+1)
		updates <- enrichEstimate(*snap, started)
		time.Sleep(backoff)
		attempt++
	}
	return nil, nil
}

func callOpenRouter(cfg config, batch []entry, spec modelSpec) apiResult {
	payload := requestPayload(cfg, batch, spec)
	body, _ := json.Marshal(payload)
	ctx, cancel := context.WithTimeout(context.Background(), cfg.requestTimeout)
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, cfg.apiURL, bytes.NewReader(body))
	if err != nil {
		return apiResult{Error: err.Error(), ModelLabel: spec.Label, Retryable: true}
	}
	req.Header.Set("Authorization", "Bearer "+cfg.apiKey)
	req.Header.Set("Content-Type", "application/json")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return apiResult{Error: "transport error: " + err.Error(), ModelLabel: spec.Label, Retryable: true}
	}
	defer resp.Body.Close()
	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return apiResult{Status: resp.StatusCode, Error: "transport read error: " + err.Error(), ModelLabel: spec.Label, Retryable: true}
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		text := string(raw)
		return apiResult{
			Status: resp.StatusCode, Error: or(text, resp.Status), ModelLabel: spec.Label,
			Retryable: transientStatus[resp.StatusCode], RawSnippet: shorten(text, 240),
			RetryAfter:       parseRetryAfter(resp.Header.Get("Retry-After")),
			RateLimitResetAt: rateLimitResetAt(resp.Header, text),
		}
	}
	content, err := decodeResponse(raw)
	if err != nil {
		var payloadErr *openRouterPayloadError
		if errors.As(err, &payloadErr) {
			status := payloadErr.Status
			if status == 0 {
				status = resp.StatusCode
			}
			text := payloadErr.Body
			return apiResult{
				Status: status, Error: text, ModelLabel: spec.Label,
				Retryable:        transientStatus[status] || transientStatus[resp.StatusCode],
				RawSnippet:       shorten(text, 240),
				RetryAfter:       parseRetryAfter(resp.Header.Get("Retry-After")),
				RateLimitResetAt: rateLimitResetAt(resp.Header, text),
			}
		}
		return apiResult{Status: resp.StatusCode, Error: "decode error: " + err.Error(), ModelLabel: spec.Label, Retryable: true, RawSnippet: shorten(string(raw), 240)}
	}
	return apiResult{OK: true, Body: content, Status: resp.StatusCode, ModelLabel: spec.Label, Retryable: true}
}

func requestPayload(cfg config, batch []entry, spec modelSpec) map[string]any {
	return map[string]any{
		"model": spec.Model,
		"messages": []map[string]string{
			{"role": "system", "content": "Ты составляешь немецко-русский словарь. Отвечай только JSON-объектом без markdown и без пояснений."},
			{"role": "user", "content": buildPrompt(batch)},
		},
		"temperature":     0.2,
		"max_tokens":      max(400, cfg.maxTokensPerEntry*len(batch)),
		"response_format": map[string]any{"type": "json_object"},
		"provider":        providerForSpec(cfg, spec),
	}
}

func providerForSpec(cfg config, spec modelSpec) map[string]any {
	provider := map[string]any{"require_parameters": true}
	if len(cfg.providerOnly) > 0 {
		provider["only"] = cfg.providerOnly
	} else {
		provider["sort"] = map[string]string{"by": "price", "partition": "none"}
	}
	if cfg.disableProviderFallbacks {
		provider["allow_fallbacks"] = false
	}
	if !cfg.allowFreeTraining {
		provider["data_collection"] = "deny"
	}
	if len(spec.ProviderOnly) > 0 {
		provider["only"] = spec.ProviderOnly
		delete(provider, "sort")
		delete(provider, "data_collection")
	}
	if spec.AllowFallbacks != nil {
		provider["allow_fallbacks"] = *spec.AllowFallbacks
	}
	return provider
}

func decodeResponse(raw []byte) (string, error) {
	var payload map[string]any
	if err := json.Unmarshal(raw, &payload); err != nil {
		return "", err
	}
	if rawError, ok := payload["error"].(map[string]any); ok {
		return "", &openRouterPayloadError{
			Status: intFromAny(rawError["code"]),
			Body:   string(raw),
		}
	}
	choices, _ := payload["choices"].([]any)
	if len(choices) == 0 {
		return "", errors.New("missing choices")
	}
	first, _ := choices[0].(map[string]any)
	message, _ := first["message"].(map[string]any)
	content := message["content"]
	switch value := content.(type) {
	case string:
		return value, nil
	case []any:
		var b strings.Builder
		for _, item := range value {
			m, _ := item.(map[string]any)
			if text, _ := m["text"].(string); text != "" {
				b.WriteString(text)
			}
		}
		return b.String(), nil
	case nil:
		return "", errors.New("unexpected response content format: nil")
	default:
		return "", fmt.Errorf("unexpected response content format: %T", content)
	}
}

func buildPrompt(batch []entry) string {
	var lines []string
	for i, item := range batch {
		pos := stringField(item, "pos")
		label := posLabels[pos]
		if label == "" {
			label = pos
		}
		lines = append(lines, fmt.Sprintf("%d. %s [%s]%s", i+1, stringField(item, "value"), label, sensePrompt(item)))
	}
	return fmt.Sprintf(`Верни JSON-объект вида:
{
  "items": [
    {
      "index": 1,
      "word": "das Haus",
      "translations": ["дом", "здание", "жилище"]
    }
  ]
}

Для каждого немецкого слова дай 3-5 русских переводов.

Требования:
- Строго учитывай часть речи в квадратных скобках.
- Все переводы должны быть на русском языке, кириллицей. Не возвращай немецкие или английские слова как перевод.
- Переводы должны быть той же части речи: глагол -> глагол, прилагательное -> прилагательное, существительное -> существительное.
- Если у слова перечислены glosses/tags, переводи именно эти немецкие значения, а не похожее слово или омоним.
- Tags вроде archaic, colloquial, technical учитывай в выборе перевода и добавляй русскую пометку только когда это важно.
- Порядок: от самого частотного в повседневной речи к более редкому.
- Глаголы: строго русские инфинитивы ("расширять", "увеличивать", "прогрызть"), не прилагательные и не существительные.
- Существительные: именительный падеж, единственное число.
- Прилагательные: мужской род, именительный падеж.
- Покрывай разные значения, если они реально различаются.
- Пометки вроде "(разг.)", "(книжн.)", "(перен.)", "(устар.)", "(тех.)", "(юр.)" добавляй только когда это важно.
- Если добавляешь пометку, ставь ее строго в конце перевода в круглых скобках: "мандат (юр.)", "манубрий (тех.)".
- Если пометок несколько, пиши их в одних скобках через запятую: "акт (книжн., юр.)".
- Не дублируй почти одинаковые синонимы.
- Не добавляй пояснения, примеры, markdown, code fences.
- Ответ должен содержать ровно по одному item на каждую строку списка.
- Сохраняй тот же `+"`index`"+` и тот же `+"`word`"+`.

Слова:
%s`, strings.Join(lines, "\n"))
}

func parseResponse(body string, batch []entry) (map[string][]map[string]any, error) {
	var payload map[string]any
	if err := json.Unmarshal([]byte(extractJSONObject(body)), &payload); err != nil {
		return nil, err
	}
	items, ok := payload["items"].([]any)
	if !ok {
		return nil, errors.New("response missing items array")
	}
	byIndex := map[int]entry{}
	for i, item := range batch {
		byIndex[i+1] = item
	}
	parsed := map[string][]map[string]any{}
	for _, rawItem := range items {
		item, ok := rawItem.(map[string]any)
		if !ok {
			continue
		}
		index := intFromAny(item["index"])
		rawTranslations, ok := item["translations"].([]any)
		target, exists := byIndex[index]
		if !ok || !exists {
			continue
		}
		var cleaned []map[string]any
		seen := map[string]bool{}
		for _, raw := range rawTranslations {
			text, ok := raw.(string)
			if !ok {
				continue
			}
			tr := parseTranslationItem(text)
			value, _ := tr["value"].(string)
			keyBytes, _ := json.Marshal(tr)
			key := string(keyBytes)
			if value == "" || seen[key] || !validTranslationForPOS(target, tr) {
				continue
			}
			seen[key] = true
			cleaned = append(cleaned, tr)
		}
		if len(cleaned) > 0 {
			parsed[stringField(target, "_id")] = cleaned
		}
	}
	return parsed, nil
}

func extractJSONObject(text string) string {
	text = strings.TrimSpace(text)
	if strings.HasPrefix(text, "```") {
		parts := strings.Split(text, "```")
		for _, part := range parts {
			part = strings.TrimSpace(part)
			part = strings.TrimPrefix(part, "json")
			part = strings.TrimSpace(part)
			if part != "" {
				text = part
				break
			}
		}
	}
	start := strings.Index(text, "{")
	end := strings.LastIndex(text, "}")
	if start == -1 || end < start {
		return text
	}
	return text[start : end+1]
}

func parseTranslationItem(raw string) map[string]any {
	value := strings.TrimSpace(raw)
	var labels []string
	if strings.HasSuffix(value, ")") && strings.Contains(value, "(") {
		start := strings.LastIndex(value, "(")
		base := strings.TrimRight(strings.TrimSpace(value[:start]), " ,;:-")
		labelPart := value[start+1 : len(value)-1]
		var parsed []string
		ok := true
		for _, chunk := range strings.Split(labelPart, ",") {
			normalized := labelMap[strings.ToLower(strings.TrimSpace(chunk))]
			if normalized == "" {
				ok = false
				break
			}
			if !contains(parsed, normalized) {
				parsed = append(parsed, normalized)
			}
		}
		if ok && base != "" {
			value = base
			labels = parsed
		}
	}
	result := map[string]any{"lang": "ru", "value": value}
	if len(labels) > 0 {
		result["meta"] = map[string]any{"labels": labels}
	}
	return result
}

func validTranslationForPOS(item entry, tr map[string]any) bool {
	value, _ := tr["value"].(string)
	if strings.TrimSpace(value) == "" {
		return false
	}
	if !looksLikeRussianTranslation(value) {
		return false
	}
	if stringField(item, "pos") == "verb" {
		return looksLikeRUInfinitive(value)
	}
	return true
}

func looksLikeRussianTranslation(value string) bool {
	hasCyrillic := false
	hasLatin := false
	for _, char := range value {
		if unicode.Is(unicode.Cyrillic, char) {
			hasCyrillic = true
		}
		if unicode.Is(unicode.Latin, char) {
			hasLatin = true
		}
	}
	return hasCyrillic && !hasLatin
}

func looksLikeRUInfinitive(value string) bool {
	fields := strings.Fields(strings.ToLower(strings.TrimSpace(value)))
	if len(fields) == 0 {
		return false
	}
	word := fields[0]
	return strings.HasSuffix(word, "ть") || strings.HasSuffix(word, "ти") || strings.HasSuffix(word, "чь") || strings.HasSuffix(word, "ться") || strings.HasSuffix(word, "тись")
}

func loadEntries(path string) ([]entry, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	var entries []entry
	scanner := bufio.NewScanner(file)
	scanner.Buffer(make([]byte, 1024), 10*1024*1024)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			continue
		}
		var item entry
		if err := json.Unmarshal([]byte(line), &item); err != nil {
			return nil, err
		}
		entries = append(entries, item)
	}
	return entries, scanner.Err()
}

func writeEntries(path string, entries []entry) error {
	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil && filepath.Dir(path) != "." {
		return err
	}
	tmp, err := os.CreateTemp(filepath.Dir(path), ".enrich-*.jsonl")
	if err != nil {
		return err
	}
	tmpPath := tmp.Name()
	writer := bufio.NewWriter(tmp)
	for _, item := range entries {
		line, err := json.Marshal(item)
		if err != nil {
			_ = tmp.Close()
			_ = os.Remove(tmpPath)
			return err
		}
		if _, err := writer.Write(append(line, '\n')); err != nil {
			_ = tmp.Close()
			_ = os.Remove(tmpPath)
			return err
		}
	}
	if err := writer.Flush(); err != nil {
		_ = tmp.Close()
		_ = os.Remove(tmpPath)
		return err
	}
	if err := tmp.Close(); err != nil {
		_ = os.Remove(tmpPath)
		return err
	}
	return os.Rename(tmpPath, path)
}

func refreshManifestForEntries(entriesPath string) error {
	manifestPath := filepath.Join(filepath.Dir(entriesPath), "manifest.edn")
	raw, err := os.ReadFile(manifestPath)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	if err != nil {
		return err
	}
	stats, err := fileStats(entriesPath)
	if err != nil {
		return err
	}
	entry := fmt.Sprintf("\"dictionary-entries.jsonl\" {:count %d, :bytes %d, :sha256 \"%s\"}", stats.Count, stats.Bytes, stats.SHA256)
	text := string(raw)
	start := strings.Index(text, "\"dictionary-entries.jsonl\"")
	if start == -1 {
		return fmt.Errorf("manifest missing dictionary-entries.jsonl entry: %s", manifestPath)
	}
	mapStart := strings.Index(text[start:], "{")
	if mapStart == -1 {
		return fmt.Errorf("manifest dictionary-entries.jsonl entry has no stats map: %s", manifestPath)
	}
	mapStart += start
	depth := 0
	mapEnd := -1
	for i := mapStart; i < len(text); i++ {
		switch text[i] {
		case '{':
			depth++
		case '}':
			depth--
			if depth == 0 {
				mapEnd = i + 1
				i = len(text)
			}
		}
	}
	if mapEnd == -1 {
		return fmt.Errorf("manifest dictionary-entries.jsonl stats map is not closed: %s", manifestPath)
	}
	updated := text[:start] + entry + text[mapEnd:]
	now := utcNow()
	if strings.Contains(updated, ":enriched-at") {
		updated = replaceEDNStringValue(updated, ":enriched-at", now)
	} else {
		generatedAt := ednExistingGeneratedAt(updated)
		if generatedAt == "" {
			return fmt.Errorf("manifest missing generated-at string: %s", manifestPath)
		}
		updated = strings.Replace(updated, fmt.Sprintf(":generated-at %q", generatedAt), fmt.Sprintf(":generated-at %q, :enriched-at %q", generatedAt, now), 1)
	}
	return atomicWriteText(manifestPath, strings.TrimRight(updated, "\n")+"\n")
}

type jsonlStats struct {
	Count  int
	Bytes  int64
	SHA256 string
}

func fileStats(path string) (jsonlStats, error) {
	file, err := os.Open(path)
	if err != nil {
		return jsonlStats{}, err
	}
	defer file.Close()
	hash := sha256.New()
	scanner := bufio.NewScanner(file)
	scanner.Buffer(make([]byte, 1024), 10*1024*1024)
	count := 0
	var bytes int64
	for scanner.Scan() {
		line := scanner.Bytes()
		hash.Write(line)
		hash.Write([]byte{'\n'})
		bytes += int64(len(line) + 1)
		count++
	}
	if err := scanner.Err(); err != nil {
		return jsonlStats{}, err
	}
	return jsonlStats{Count: count, Bytes: bytes, SHA256: hex.EncodeToString(hash.Sum(nil))}, nil
}

func replaceEDNStringValue(text, key, value string) string {
	start := strings.Index(text, key)
	if start == -1 {
		return text
	}
	quoteStart := strings.Index(text[start:], "\"")
	if quoteStart == -1 {
		return text
	}
	quoteStart += start
	quoteEnd := strings.Index(text[quoteStart+1:], "\"")
	if quoteEnd == -1 {
		return text
	}
	quoteEnd += quoteStart + 1
	return text[:quoteStart] + fmt.Sprintf("%q", value) + text[quoteEnd+1:]
}

func ednExistingGeneratedAt(text string) string {
	start := strings.Index(text, ":generated-at")
	if start == -1 {
		return ""
	}
	quoteStart := strings.Index(text[start:], "\"")
	if quoteStart == -1 {
		return ""
	}
	quoteStart += start
	quoteEnd := strings.Index(text[quoteStart+1:], "\"")
	if quoteEnd == -1 {
		return ""
	}
	quoteEnd += quoteStart + 1
	return text[quoteStart+1 : quoteEnd]
}

func atomicWriteText(path, text string) error {
	dir := filepath.Dir(path)
	tmp, err := os.CreateTemp(dir, ".manifest-*.edn")
	if err != nil {
		return err
	}
	if _, err := tmp.WriteString(text); err != nil {
		_ = tmp.Close()
		_ = os.Remove(tmp.Name())
		return err
	}
	if err := tmp.Close(); err != nil {
		_ = os.Remove(tmp.Name())
		return err
	}
	return os.Rename(tmp.Name(), path)
}

func loadState(path, inputPath, outputPath string) (state, error) {
	now := utcNow()
	base := state{Version: stateVersion, InputPath: inputPath, OutputPath: outputPath, StartedAt: now, UpdatedAt: now}
	raw, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		return base, nil
	}
	if err != nil {
		return base, err
	}
	var legacy struct {
		Done []string `json:"done"`
	}
	if json.Unmarshal(raw, &legacy) == nil && legacy.Done != nil {
		base.LegacyDoneCount = len(legacy.Done)
		return base, nil
	}
	if err := json.Unmarshal(raw, &base); err != nil {
		return base, err
	}
	if base.Version == 0 {
		base.Version = stateVersion
	}
	if base.InputPath == "" {
		base.InputPath = inputPath
	}
	if base.OutputPath == "" {
		base.OutputPath = outputPath
	}
	return base, nil
}

func saveState(path string, st state) error {
	st.UpdatedAt = utcNow()
	return atomicWriteJSON(path, st)
}

func atomicWriteJSON(path string, payload any) error {
	dir := filepath.Dir(path)
	if dir != "." {
		if err := os.MkdirAll(dir, 0755); err != nil {
			return err
		}
	}
	tmp, err := os.CreateTemp(dir, ".state-*.json")
	if err != nil {
		return err
	}
	enc := json.NewEncoder(tmp)
	enc.SetIndent("", "  ")
	enc.SetEscapeHTML(false)
	if err := enc.Encode(payload); err != nil {
		_ = tmp.Close()
		_ = os.Remove(tmp.Name())
		return err
	}
	if err := tmp.Close(); err != nil {
		_ = os.Remove(tmp.Name())
		return err
	}
	return os.Rename(tmp.Name(), path)
}

func appendJSONL(path string, payload any) error {
	dir := filepath.Dir(path)
	if dir != "." {
		if err := os.MkdirAll(dir, 0755); err != nil {
			return err
		}
	}
	file, err := os.OpenFile(path, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0644)
	if err != nil {
		return err
	}
	defer file.Close()
	enc := json.NewEncoder(file)
	enc.SetEscapeHTML(false)
	return enc.Encode(payload)
}

func logCrash(path string, err error) error {
	return appendJSONL(path, map[string]any{
		"ts":      utcNow(),
		"cwd":     must(os.Getwd()),
		"argv":    os.Args,
		"type":    fmt.Sprintf("%T", err),
		"message": err.Error(),
	})
}

func needsTranslation(item entry) bool {
	for _, raw := range anySlice(item["translation"]) {
		tr, _ := raw.(map[string]any)
		if tr["lang"] == "ru" {
			return false
		}
	}
	return true
}

func selectCandidates(entries []entry, cfg config) []entry {
	var candidates []entry
	for _, item := range entries {
		if needsTranslation(item) && intField(item, "rank") >= cfg.minRank {
			candidates = append(candidates, item)
		}
	}
	sort.Slice(candidates, func(i, j int) bool {
		a, b := candidates[i], candidates[j]
		if cfg.priority == "rank" {
			if intField(a, "rank") != intField(b, "rank") {
				return intField(a, "rank") > intField(b, "rank")
			}
			return stringField(a, "value") < stringField(b, "value")
		}
		ca, cb := corePriority(a), corePriority(b)
		if ca != cb {
			return ca > cb
		}
		if intField(a, "rank") != intField(b, "rank") {
			return intField(a, "rank") > intField(b, "rank")
		}
		return stringField(a, "value") < stringField(b, "value")
	})
	return candidates
}

func corePriority(item entry) int {
	meta := mapField(item, "meta")
	cefr, _ := meta["cefr_level"].(string)
	score := map[string]int{"a1": 300000, "a2": 200000, "b1": 100000}[cefr]
	senseCount := intFromAny(meta["sense_count"])
	formsCount := len(anySlice(item["forms"]))
	posScore := map[string]int{"verb": 800, "noun": 700, "adj": 400, "adv": 300, "phrase": 100}[stringField(item, "pos")]
	return score + min(senseCount, 20)*1500 + min(formsCount, 30)*80 + posScore - wordComplexity(stringField(item, "value"))
}

func wordComplexity(value string) int {
	bare := strings.TrimSpace(bareWord(value))
	count := len(strings.Fields(bare))
	return len([]rune(bare))*35 + max(0, count-1)*600
}

func bareWord(value string) string {
	lower := strings.ToLower(value)
	for _, article := range []string{"der ", "die ", "das "} {
		if strings.HasPrefix(lower, article) {
			return value[len(article):]
		}
	}
	return value
}

func sensePrompt(item entry) string {
	var lines []string
	for idx, raw := range anySlice(mapField(item, "meta")["senses"]) {
		if idx >= 4 {
			break
		}
		sense, _ := raw.(map[string]any)
		glosses := stringList(sense["glosses"])
		if len(glosses) == 0 {
			continue
		}
		tags := stringList(sense["tags"])
		tagText := ""
		if len(tags) > 0 {
			tagText = " tags=" + strings.Join(tags, ",")
		}
		lines = append(lines, "    - "+strings.Join(glosses, ", ")+tagText)
	}
	if len(lines) == 0 {
		return ""
	}
	return "\n" + strings.Join(lines, "\n")
}

func senseSummary(item entry) string {
	var parts []string
	for idx, raw := range anySlice(mapField(item, "meta")["senses"]) {
		if idx >= 2 {
			break
		}
		sense, _ := raw.(map[string]any)
		glosses := stringList(sense["glosses"])
		if len(glosses) == 0 {
			continue
		}
		text := strings.Join(glosses, ", ")
		tags := stringList(sense["tags"])
		if len(tags) > 0 {
			text += " [" + strings.Join(tags, ", ") + "]"
		}
		parts = append(parts, text)
	}
	return or(shorten(strings.Join(parts, " | "), 140), "-")
}

func orderedModelSpecs(cfg config) []modelSpec {
	var specs []modelSpec
	seen := map[string]bool{}
	for _, raw := range append([]string{cfg.model}, cfg.models...) {
		if raw == "" {
			continue
		}
		spec := parseModelSpec(raw)
		key := spec.Model + "|" + strings.Join(spec.ProviderOnly, "+") + "|" + fmt.Sprint(spec.AllowFallbacks)
		if seen[key] {
			continue
		}
		seen[key] = true
		specs = append(specs, spec)
	}
	return specs
}

func parseModelSpec(raw string) modelSpec {
	value := strings.TrimSpace(raw)
	var allow *bool
	if strings.HasSuffix(value, "!") {
		value = strings.TrimSuffix(value, "!")
		v := false
		allow = &v
	}
	if !strings.Contains(value, "@") {
		return modelSpec{Model: value, AllowFallbacks: allow, Label: raw}
	}
	parts := strings.Split(value, "@")
	model := strings.Join(parts[:len(parts)-1], "@")
	providers := splitPlus(parts[len(parts)-1])
	return modelSpec{Model: strings.TrimSpace(model), ProviderOnly: providers, AllowFallbacks: allow, Label: raw}
}

func splitPlus(raw string) []string {
	var out []string
	for _, item := range strings.Split(raw, "+") {
		item = strings.TrimSpace(item)
		if item != "" {
			out = append(out, item)
		}
	}
	return out
}

func waitWithTUI(duration time.Duration, resetAt time.Time, reason string, snap *snapshot, updates chan<- snapshot, started time.Time) {
	deadline := time.Now().Add(duration)
	until := resetAt
	if until.IsZero() {
		until = deadline
	}
	for {
		remaining := time.Until(deadline)
		if remaining < 0 {
			remaining = 0
		}
		snap.Status = reason
		snap.WaitUntil = until.Local().Format("2006-01-02 15:04:05 MST")
		snap.WaitFor = formatDuration(remaining)
		updates <- enrichEstimate(*snap, started)
		if remaining == 0 {
			break
		}
		time.Sleep(minDuration(30*time.Second, remaining))
	}
	snap.WaitUntil = "-"
	snap.WaitFor = "-"
}

func backoffDelay(attempt int, retryAfter time.Duration, cfg config) time.Duration {
	if retryAfter > 0 {
		return minDuration(cfg.maxBackoff, maxDuration(cfg.delay, retryAfter))
	}
	mult := math.Pow(2, float64(attempt-1))
	return minDuration(cfg.maxBackoff, maxDuration(cfg.delay, time.Duration(float64(cfg.delay)*mult)))
}

func increaseDelay(current time.Duration, cfg config) time.Duration {
	next := maxDuration(current+500*time.Millisecond, time.Duration(float64(current)*1.5))
	next = maxDuration(cfg.delay, next)
	return minDuration(cfg.maxDelay, next)
}

func relaxDelay(current, floor time.Duration) time.Duration {
	if current <= floor {
		return current
	}
	return maxDuration(floor, time.Duration(float64(current)*0.9))
}

func enrichEstimate(s snapshot, started time.Time) snapshot {
	elapsed := time.Since(started)
	completed := s.Selected - s.Remaining
	s.Elapsed = formatDuration(elapsed)
	if completed <= 0 || elapsed <= 0 {
		s.ETA = "after first batch"
		s.Rate = "-"
		return s
	}
	rate := float64(completed) / elapsed.Seconds()
	eta := time.Duration(float64(max(0, s.Remaining)) / rate * float64(time.Second))
	s.ETA = formatDuration(eta)
	s.Rate = fmt.Sprintf("%.1f/min", rate*60)
	return s
}

func newTUI(ch <-chan snapshot) tuiModel {
	vp := viewport.New(80, 10)
	return tuiModel{ch: ch, vp: vp, snap: snapshot{Status: "starting"}}
}

func (m tuiModel) Init() tea.Cmd {
	return tea.Batch(waitUpdate(m.ch), tea.Tick(time.Second, func(time.Time) tea.Msg { return tickMsg{} }))
}

func waitUpdate(ch <-chan snapshot) tea.Cmd {
	return func() tea.Msg {
		s, ok := <-ch
		if !ok {
			return doneMsg{Done: true}
		}
		if s.Done {
			return doneMsg(s)
		}
		return updateMsg(s)
	}
}

func (m tuiModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	var cmd tea.Cmd
	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.String() {
		case "q", "ctrl+c":
			m.finished = true
			return m, tea.Quit
		case "pgup":
			m.vp.HalfViewUp()
		case "pgdown":
			m.vp.HalfViewDown()
		case "home":
			m.vp.GotoTop()
		case "end":
			m.vp.GotoBottom()
		default:
			m.vp, cmd = m.vp.Update(msg)
		}
		return m, cmd
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.ready = true
		m.vp.Width = max(20, msg.Width-2)
		m.vp.Height = max(3, msg.Height-20)
		m.refreshViewport()
		return m, nil
	case updateMsg:
		m.snap = snapshot(msg)
		m.refreshViewport()
		return m, waitUpdate(m.ch)
	case doneMsg:
		done := snapshot(msg)
		if done.Err != "" {
			m.snap.Err = done.Err
			m.snap.Status = done.Status
			m.snap.ExitCode = done.ExitCode
		} else if done.Status != "" {
			m.snap.Status = done.Status
			m.snap.ExitCode = done.ExitCode
		}
		m.finished = true
		m.refreshViewport()
		return m, tea.Quit
	case tickMsg:
		if m.finished {
			return m, nil
		}
		return m, tea.Tick(time.Second, func(time.Time) tea.Msg { return tickMsg{} })
	}
	m.vp, cmd = m.vp.Update(msg)
	return m, cmd
}

func (m tuiModel) View() string {
	if !m.ready {
		return "starting..."
	}
	s := m.snap
	header := titleStyle.Render("Dictionary Enrichment") + "\n"
	if s.Total == 0 && s.Selected == 0 && s.Status == "starting" {
		body := box("Preparing", "loading dictionary and checkpoint...", m.width)
		help := mutedStyle.Render("recent: ↑/↓ scroll  PgUp/PgDn page  Home/End  q quit")
		recent := box("Recent", mutedStyle.Render("no recent rows yet"), m.width)
		return fitScreen(header, body, recent, "", help, m.height)
	}
	header += mutedStyle.Render(fmt.Sprintf("entries %d total · %d missing ru · %d selected · output %s", s.Total, s.Missing, s.Selected, blank(s.Output))) + "\n"
	body := box("Run", summaryGrid(s, m.width), m.width)
	status := fmt.Sprintf("Status: %s\nWords: %s", s.Status, s.Words)
	if s.WaitUntil != "" && s.WaitUntil != "-" {
		status += "\nWaiting until: " + s.WaitUntil + " (" + s.WaitFor + ")"
	}
	if s.LastError != "" {
		status += "\n" + errStyle.Render("Last error: "+shorten(s.LastError, max(40, m.width-8)))
	}
	if s.LastResponse != "" {
		status += "\n" + warnStyle.Render("Last response: "+shorten(s.LastResponse, max(40, m.width-8)))
	}
	if s.Err != "" {
		status += "\n" + errStyle.Render("Crash: "+s.Err)
	}
	footer := box("Current Batch", status, m.width)
	help := mutedStyle.Render("recent: ↑/↓ scroll  PgUp/PgDn page  Home/End  q quit")
	recentHeight := recentViewportHeight(m.height, header, body, footer, help)
	vp := m.vp
	vp.Width = max(20, m.width-6)
	vp.Height = recentHeight
	recent := box("Recent", progressLine(s, vp.Width)+"\n"+recentHeader(vp.Width)+"\n"+vp.View(), m.width)
	return fitScreen(header, body, recent, footer, help, m.height)
}

func recentViewportHeight(totalHeight int, header, body, footer, help string) int {
	fixed := lipgloss.Height(header) + lipgloss.Height(body) + lipgloss.Height(footer) + lipgloss.Height(help) + 7
	return max(3, totalHeight-fixed)
}

func progressLine(s snapshot, width int) string {
	total := max(0, s.InitialMissing)
	if total == 0 {
		total = max(0, s.Missing+s.Enriched)
	}
	done := max(0, total-s.Missing)
	if total == 0 {
		return mutedStyle.Render("File progress: 0/0")
	}
	percent := float64(done) / float64(total)
	label := fmt.Sprintf("File progress: %d/%d %.1f%%", done, total, percent*100)
	barWidth := max(10, width-lipgloss.Width(label)-3)
	filled := int(math.Round(percent * float64(barWidth)))
	filled = min(max(0, filled), barWidth)
	bar := okStyle.Render(strings.Repeat("=", filled)) + mutedStyle.Render(strings.Repeat("-", barWidth-filled))
	return fmt.Sprintf("%s  [%s]", label, bar)
}

func fitScreen(header, body, recent, footer, help string, height int) string {
	parts := []string{strings.TrimRight(header, "\n"), body, recent}
	if footer != "" {
		parts = append(parts, footer)
	}
	parts = append(parts, help)
	out := strings.Join(parts, "\n")
	lines := strings.Split(out, "\n")
	if height <= 0 || len(lines) <= height {
		return out
	}
	return strings.Join(lines[:height], "\n")
}

func box(title string, content string, width int) string {
	return lipgloss.NewStyle().
		Border(lipgloss.RoundedBorder()).
		BorderForeground(lipgloss.Color("8")).
		Padding(0, 1).
		Width(max(20, width-2)).
		Render(titleStyle.Render(title) + "\n" + strings.TrimRight(content, "\n"))
}

func summaryGrid(s snapshot, width int) string {
	colWidth := max(24, (width-10)/3)
	rows := [][][2]string{
		{{"Mode", s.Mode}, {"Remaining", fmt.Sprint(s.Remaining)}, {"Requests", fmt.Sprint(s.Requests)}},
		{{"Enriched", fmt.Sprint(s.Enriched)}, {"No parse", fmt.Sprint(s.Failed)}, {"Batches", fmt.Sprint(s.Batches)}},
		{{"Model", shorten(s.Model, colWidth-9)}, {"Batch", s.Batch}, {"Delay", s.Delay}},
		{{"Backoff", s.Backoff}, {"Wait for", s.WaitFor}, {"Rate", s.Rate}},
		{{"Elapsed", s.Elapsed}, {"ETA", s.ETA}, {"Priority", s.Priority}},
		{{"Privacy", s.Privacy}},
	}
	lines := make([]string, 0, len(rows))
	for _, row := range rows {
		cells := make([]string, 0, 3)
		for _, cell := range row {
			cells = append(cells, metricCell(cell[0], cell[1], colWidth))
		}
		lines = append(lines, lipgloss.JoinHorizontal(lipgloss.Top, cells...))
	}
	return strings.Join(lines, "\n")
}

func metricCell(key string, value string, width int) string {
	text := fmt.Sprintf("%-10s %s", key+":", blank(value))
	return lipgloss.NewStyle().Width(width).Render(text)
}

func blank(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return "-"
	}
	return value
}

func (m *tuiModel) refreshViewport() {
	m.vp.SetContent(formatRecentRows(m.snap.Recent, max(20, m.vp.Width)))
	m.vp.GotoBottom()
}

func recentHeader(width int) string {
	wordWidth, posWidth, textWidth := recentWidths(width)
	header := joinRecentCells([]string{"Word", "POS", "Senses", "Translations"}, []int{wordWidth, posWidth, textWidth, textWidth})
	rule := mutedStyle.Render(strings.Repeat("-", max(20, width)))
	return header + "\n" + rule
}

func recentWidths(width int) (int, int, int) {
	wordWidth := 24
	posWidth := 6
	spacing := 6
	textWidth := max(16, (width-wordWidth-posWidth-spacing)/2)
	return wordWidth, posWidth, textWidth
}

func formatRecentRows(rows []recentRow, width int) string {
	if len(rows) == 0 {
		return mutedStyle.Render("no recent rows yet")
	}
	wordWidth, posWidth, textWidth := recentWidths(width)
	var b strings.Builder
	for _, row := range rows {
		style := okStyle
		if row.Status != "ok" {
			style = warnStyle
		}
		lines := wrappedRecentRow(row, []int{wordWidth, posWidth, textWidth, textWidth})
		for _, line := range lines {
			b.WriteString(style.Render(line))
			b.WriteByte('\n')
		}
	}
	return b.String()
}

func wrappedRecentRow(row recentRow, widths []int) []string {
	cols := [][]string{
		wrapText(row.Word, widths[0]),
		wrapText(row.POS, widths[1]),
		wrapText(row.Senses, widths[2]),
		wrapText(row.Translations, widths[3]),
	}
	height := 1
	for _, col := range cols {
		height = max(height, len(col))
	}
	lines := make([]string, 0, height)
	for lineIndex := 0; lineIndex < height; lineIndex++ {
		cells := make([]string, 0, len(cols))
		for colIndex, col := range cols {
			value := ""
			if lineIndex < len(col) {
				value = col[lineIndex]
			}
			cells = append(cells, value)
			if colIndex == len(cols)-1 {
				break
			}
		}
		lines = append(lines, joinRecentCells(cells, widths))
	}
	return lines
}

func joinRecentCells(cells []string, widths []int) string {
	parts := make([]string, 0, len(cells))
	for index, cell := range cells {
		if index >= len(widths) {
			break
		}
		parts = append(parts, padCell(cell, widths[index]))
	}
	return strings.Join(parts, "  ")
}

func wrapText(text string, width int) []string {
	text = strings.TrimSpace(text)
	if text == "" {
		return []string{""}
	}
	words := strings.Fields(text)
	lines := []string{}
	current := ""
	flush := func() {
		if current != "" {
			lines = append(lines, current)
			current = ""
		}
	}
	for _, word := range words {
		for lipgloss.Width(word) > width {
			head, tail := splitByWidth(word, width)
			if current != "" {
				flush()
			}
			lines = append(lines, head)
			word = tail
		}
		if current == "" {
			current = word
			continue
		}
		next := current + " " + word
		if lipgloss.Width(next) <= width {
			current = next
		} else {
			flush()
			current = word
		}
	}
	flush()
	if len(lines) == 0 {
		return []string{""}
	}
	return lines
}

func splitByWidth(text string, width int) (string, string) {
	var head strings.Builder
	used := 0
	for index, char := range text {
		charWidth := lipgloss.Width(string(char))
		if used > 0 && used+charWidth > width {
			return head.String(), text[index:]
		}
		head.WriteRune(char)
		used += charWidth
	}
	return head.String(), ""
}

func padCell(text string, width int) string {
	padding := width - lipgloss.Width(text)
	if padding <= 0 {
		return text
	}
	return text + strings.Repeat(" ", padding)
}

func recentRows(batch []entry, parsed map[string][]map[string]any, model string, batchIndex int) []recentRow {
	rows := make([]recentRow, 0, len(batch))
	for _, item := range batch {
		translations := "no parse"
		status := "fail"
		if parsed != nil {
			if found := parsed[stringField(item, "_id")]; len(found) > 0 {
				translations = translationSummary(found)
				status = "ok"
			}
		}
		rows = append(rows, recentRow{
			Ts: utcNow(), BatchIndex: batchIndex, Status: status, Word: stringField(item, "value"),
			POS: stringField(item, "pos"), Senses: senseSummary(item), Model: model, Translations: translations,
		})
	}
	return rows
}

func translationSummary(items []map[string]any) string {
	var values []string
	for _, item := range items {
		if value, _ := item["value"].(string); value != "" {
			values = append(values, value)
		}
	}
	return or(shorten(strings.Join(values, ", "), 120), "-")
}

func makeBatches(items []entry, size int) [][]entry {
	var batches [][]entry
	for start := 0; start < len(items); start += size {
		end := min(start+size, len(items))
		batches = append(batches, items[start:end])
	}
	return batches
}

func batchWords(batch []entry) string {
	return shorten(strings.Join(entryWords(batch), ", "), 120)
}

func entryWords(batch []entry) []string {
	out := make([]string, 0, len(batch))
	for _, item := range batch {
		out = append(out, stringField(item, "value"))
	}
	return out
}

func entryIDs(batch []entry) []string {
	out := make([]string, 0, len(batch))
	for _, item := range batch {
		out = append(out, stringField(item, "_id"))
	}
	return out
}

func failPayload(item entry, batchIndex int, batch []entry, snap snapshot) map[string]any {
	return map[string]any{
		"ts": utcNow(), "id": stringField(item, "_id"), "word": stringField(item, "value"),
		"pos": stringField(item, "pos"), "rank": intField(item, "rank"), "batch_index": batchIndex,
		"batch_words": entryWords(batch), "error": snap.LastError, "response_snippet": snap.LastResponse,
	}
}

func parseRetryAfter(raw string) time.Duration {
	if raw == "" {
		return 0
	}
	if f, err := strconv.ParseFloat(raw, 64); err == nil {
		return seconds(f)
	}
	if t, err := http.ParseTime(raw); err == nil {
		return maxDuration(0, time.Until(t))
	}
	return 0
}

func rateLimitResetAt(headers http.Header, body string) time.Time {
	raw := headers.Get("X-RateLimit-Reset")
	var payload map[string]any
	if err := json.Unmarshal([]byte(body), &payload); err == nil {
		if errObj, _ := payload["error"].(map[string]any); errObj != nil {
			if metadata, _ := errObj["metadata"].(map[string]any); metadata != nil {
				if h, _ := metadata["headers"].(map[string]any); h != nil {
					for key, value := range h {
						if strings.EqualFold(key, "X-RateLimit-Reset") {
							raw = fmt.Sprint(value)
						}
					}
				}
			}
		}
	}
	if raw == "" {
		return time.Time{}
	}
	f, err := strconv.ParseFloat(raw, 64)
	if err != nil {
		return time.Time{}
	}
	if f > 10000000000 {
		f = f / 1000
	}
	return time.Unix(int64(f), 0)
}

func privacyLabel(cfg config) string {
	parts := []string{}
	if cfg.allowFreeTraining {
		parts = append(parts, "free-training allowed")
	} else {
		parts = append(parts, "data_collection=deny")
	}
	if len(cfg.providerOnly) > 0 {
		parts = append(parts, "only="+strings.Join(cfg.providerOnly, "+"))
	}
	return strings.Join(parts, ", ")
}

func formatSeconds(d time.Duration) string {
	return fmt.Sprintf("%.1fs", d.Seconds())
}

func formatDuration(d time.Duration) string {
	if d < 0 {
		d = 0
	}
	seconds := int(d.Seconds())
	h := seconds / 3600
	m := (seconds % 3600) / 60
	s := seconds % 60
	if h > 0 {
		return fmt.Sprintf("%dh %dm %ds", h, m, s)
	}
	if m > 0 {
		return fmt.Sprintf("%dm %ds", m, s)
	}
	return fmt.Sprintf("%ds", s)
}

func utcNow() string {
	return time.Now().UTC().Format(time.RFC3339Nano)
}

func shorten(text string, limit int) string {
	text = strings.Join(strings.Fields(text), " ")
	if limit <= 0 || len([]rune(text)) <= limit {
		return text
	}
	runes := []rune(text)
	if limit <= 3 {
		return string(runes[:limit])
	}
	return string(runes[:limit-3]) + "..."
}

func stringField(item map[string]any, key string) string {
	value, _ := item[key].(string)
	return value
}

func intField(item map[string]any, key string) int {
	return intFromAny(item[key])
}

func intFromAny(value any) int {
	switch v := value.(type) {
	case int:
		return v
	case int64:
		return int(v)
	case float64:
		return int(v)
	case json.Number:
		i, _ := v.Int64()
		return int(i)
	}
	return 0
}

func mapField(item map[string]any, key string) map[string]any {
	value, _ := item[key].(map[string]any)
	if value == nil {
		return map[string]any{}
	}
	return value
}

func anySlice(value any) []any {
	items, _ := value.([]any)
	return items
}

func stringList(value any) []string {
	var out []string
	for _, raw := range anySlice(value) {
		if text, ok := raw.(string); ok && strings.TrimSpace(text) != "" {
			out = append(out, strings.TrimSpace(text))
		}
	}
	return out
}

func contains(items []string, needle string) bool {
	for _, item := range items {
		if item == needle {
			return true
		}
	}
	return false
}

func fileExists(path string) bool {
	_, err := os.Stat(path)
	return err == nil
}

func or(value, fallback string) string {
	if value != "" {
		return value
	}
	return fallback
}

func must[T any](value T, err error) T {
	if err != nil {
		var zero T
		return zero
	}
	return value
}

func minDuration(a, b time.Duration) time.Duration {
	if a < b {
		return a
	}
	return b
}

func maxDuration(a, b time.Duration) time.Duration {
	if a > b {
		return a
	}
	return b
}
