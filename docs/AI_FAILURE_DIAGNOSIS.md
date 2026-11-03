# FlowRelay AI Failure Diagnosis (Phase 1)

This is an opt-in, read-only LLM assistant for investigating jobs with status
`RETRY_SCHEDULED` or `DEAD_LETTERED`. It does not execute retries, change worker
settings, or claim that its hypotheses are definitive.

## Configuration

Provider selection: set `FLOWRELAY_AI_PROVIDER=ollama` to use a model running
on your Mac with no OpenAI credits, or `FLOWRELAY_AI_PROVIDER=openai` for the
existing cloud gateway. Both use the same read-only diagnosis endpoint.

Ollama example in the root untracked `.env`:

```dotenv
FLOWRELAY_AI_PROVIDER=ollama
FLOWRELAY_OLLAMA_MODEL=qwen2.5:1.5b
FLOWRELAY_OLLAMA_URL=http://host.docker.internal:11434/api/generate
```

`host.docker.internal` is Docker Desktop's hostname for accessing the host.
Install Ollama locally, run `ollama pull qwen2.5:1.5b`, and ensure Ollama
listens on an address reachable from Docker. On Mac, starting the service with
`OLLAMA_HOST=0.0.0.0:11434 ollama serve` in a separate Terminal is an easy
local-development option, but this binds all interfaces; use only on a trusted
network with the macOS firewall enabled. Do not expose port 11434 publicly.
Local inference on 8 GB RAM may be slow. No API key is needed for Ollama.


For the Docker Compose deployment, create a **local, untracked** `.env` file in
the project root (already ignored by Git):

```dotenv
OPENAI_API_KEY=YOUR_OPENAI_API_KEY
FLOWRELAY_AI_MODEL=gpt-6-luna
```

The backend Compose service passes these variables through to Spring Boot.
For running with `./gradlew bootRun` instead of Docker, export both variables
in the backend terminal before starting the process. Restart the backend
whenever these variables change. OpenAI API usage is billed separately from ChatGPT.

Never commit real API keys, paste them into GitHub issues, or put them in the
React frontend's `VITE_` variables.

## Demo

1. Run PostgreSQL, backend worker, and React dashboard.
2. Submit an `example.fail` job with `maxAttempts: 2`.
3. Wait for it to enter `DEAD_LETTERED` (or `RETRY_SCHEDULED`).
4. Open the job details page and click **Analyze failure with AI**.
5. Read the AI-generated suggestions; do not treat them as proven root causes.

Equivalent API request (replace ID):

```bash
curl -sS -X POST http://localhost:8080/api/v1/admin/ai/jobs/YOUR_JOB_ID/diagnose | python3 -m json.tool
```

The request is made only when clicked; it may incur API charges. Only bounded
job metadata and a redacted error snippet are sent to the model. Job payload,
worker identifiers, and event message text are excluded. Redaction is best
-effort, not a guarantee of complete secret removal. Do not enable this feature
with sensitive production data without a full data handling/security review.

## Test

```bash
./gradlew test --no-daemon
./gradlew integrationTest --no-daemon
cd dashboard && npm run build
```

## Limitations / follow-up

- Diagnosis is stateless, non-streamed, and not persisted in the database.
- API calls are synchronous and require network/API quota.
- Local demo uses the repository's optional admin API key configuration; protect
  admin routes before public deployment.
- Add rate limits, authorization, audit trail, and stronger redaction before production.
- Later phases: persisted delivery attempts, idempotency, retry analytics,
  and benchmark-backed intelligent operations.
- Original job queue and UI foundation derives from QueueForge (see root README
  and LICENSE); Phase 1 AI integration is a separate extension.
