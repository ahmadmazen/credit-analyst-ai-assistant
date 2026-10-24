# Credit Analyst AI Assistant

A Spring Boot demo of a multi-agent credit analyst. A `CreditAnalystAgent` built with
[langchain4j](https://github.com/langchain4j/langchain4j) reasons over an applicant's data,
the general company policy and a product-specific guide, then returns a structured
APPROVE / REJECT / REVIEW decision with step-by-step reasoning. Every model turn and tool
call is streamed to a small browser UI over Server-Sent Events so you can watch the agents work.

The LLM behind the agents is the **Claude Code CLI running in headless mode** (`claude -p`).
No API key or HTTP client is needed; the app shells out to the locally installed and
authenticated `claude` binary.

## How it works

```
Browser UI  --POST /api/analysis-->  AnalysisService  -->  CreditAnalystAgent (Claude CLI)
    ^                                       |                    |  tools
    |                                       |                    +-- CreditAnalysisTools   (applicant data, DTI)
    +--SSE /api/analysis/{id}/events--------+                    +-- ProductGuidelineTools
                                                                      \-- DocumentAnalystAgent (Claude CLI)
```

1. The UI (`/`) lists the demo applicants and products and starts an analysis.
2. `AnalysisService` runs the analysis on a bounded thread pool and registers an `AnalysisRun`.
3. `CreditAnalystAgent` receives the general policy rules in its prompt and calls tools to fetch
   the applicant's profile, credit score, financials, DTI and loan application details.
4. For product rules it calls `getProductSpecificGuideline`, which delegates to a nested
   `DocumentAnalystAgent` that answers questions strictly from `car_loan_guide.txt` or
   `personal_loan_guide.txt`.
5. The final answer is forced into a JSON schema (`DecisionSchema`) and parsed into the run's
   decision. The UI receives `run_started`, `model_call`, `tool_call`, `tool_result`,
   `guideline_lookup`, `decision` and `run_completed` events as they happen.

### Claude CLI as a langchain4j model

`ClaudeCodeChatModel` implements langchain4j's `ChatLanguageModel` on top of the Claude Code CLI.
Because the CLI has no native function-calling API, tool use is emulated: the CLI is forced via
`--json-schema` to answer with either a list of tool-call requests or the final answer, and the
wrapper maps that back onto langchain4j's tool contract. Supporting classes:

| Class | Role |
|---|---|
| `ClaudePromptSerializer` | Flattens the chat history into a single prompt for `claude -p` |
| `ClaudeSchemaBuilder` | Builds the tool-call / final-answer wrapper schema |
| `ClaudeResponseParser` | Parses the CLI's JSON output into tool requests or an answer |
| `ProcessCommandRunner` | Runs the CLI as a subprocess with a timeout |
| `ClaudeCliLocator` | Finds the `claude` binary when it is not on the JVM's PATH |

## Demo data

Applicants live in memory in `ApplicantProfileRepository`. Each one exercises a different path:

| ID | Name | Expected outcome |
|---|---|---|
| APP-001 | Sara Green | Clean approval (DTI 15.8%, LTV 80%) |
| APP-002 | John Doe | Car loan fails LTV cap; personal loan fails the 700 score floor |
| APP-003 | Omar Haddad | Fails general DTI policy (50%) |
| APP-004 | Lena Torres | Credit score 650, below the 680 floor |
| APP-005 | Adam Nassar | Borderline: score exactly 700, DTI 39%, LTV exactly 90% |

Rules the agents reason over are plain text files in `src/main/resources`:

- `policy_rules.txt`: score >= 680, age >= 21, DTI < 40%
- `car_loan_guide.txt`: LTV <= 90%, minimum income $40,000
- `personal_loan_guide.txt`: cap $50,000, score >= 700

## Requirements

- Java 17+
- [Claude Code CLI](https://docs.anthropic.com/en/docs/claude-code) installed and authenticated
  (`claude` must work from a terminal)

## Running

```bash
./mvnw spring-boot:run
```

Open <http://localhost:8080>. Pick an applicant and a product, start the analysis and follow
the event stream. `/flow.html` shows the analysis flow diagram.

### Configuration (`application.properties`)

| Property | Default | Meaning |
|---|---|---|
| `claude.cli.path` | `claude` | Path or name of the CLI binary |
| `claude.cli.model` | `sonnet` | Model alias passed to the CLI |
| `claude.cli.timeout-seconds` | `180` | Per-call subprocess timeout |
| `claude.cli.max-turns` | `16` | Max agent turns per CLI call |
| `analysis.max-concurrent-runs` | `2` | Analyses allowed to run at once (HTTP 429 beyond that) |

## REST API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/scenarios` | Demo applicants and supported products |
| `GET` | `/api/rules` | Policy and product guide texts |
| `POST` | `/api/analysis` | Start a run. Body: `{"applicantId":"APP-001","productType":"Car Loan"}`. Returns `202` with `runId` |
| `GET` | `/api/analysis/{id}` | Snapshot of a run: status, decision and all events so far |
| `GET` | `/api/analysis/{id}/events` | SSE stream of the run's events, with replay of earlier ones |

Errors return `400` for an unknown applicant or product, `404` for an unknown run and `429`
when the concurrency limit is reached.

## Tests

```bash
./mvnw test
```

Unit and web-layer tests use a fake `CommandRunner`, so they do not need the CLI.
`ClaudeCodeChatModelIT` is a manual smoke test against the real CLI and is `@Disabled` by default.

## Project layout

```
src/main/java/sa/lendo/credit/analyst/multi_agent/
  agents/    langchain4j AI service interfaces (CreditAnalystAgent, DocumentAnalystAgent)
  claude/    Claude Code CLI adapter for langchain4j
  config/    Spring wiring
  events/    Run registry and event publisher feeding the SSE stream
  mock/      In-memory applicant profiles
  service/   AnalysisService, agent factory, decision schema and parser
  tools/     @Tool methods exposed to the agents
  web/       REST controllers and exception handling
src/main/resources/static/   Browser UI (index.html, flow.html, app.js, style.css)
```
