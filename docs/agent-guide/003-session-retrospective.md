# Session Retrospective (Codex)

This document describes a repeatable workflow for reading historical Codex sessions for the current repo (by `cwd`), identifying “blind spots” that cause agent back-and-forth, then turning those lessons into durable assets (docs + skills).

## Goals

- Find repeated friction patterns (re-asking, re-planning, re-explaining, missing obvious repo facts).
- Distill the fix into:
  - a small doc patch (preferred when the answer is repo-specific), or
  - a skill update/new skill (preferred when it’s a reusable workflow).
- Avoid copying raw “system traces” into docs.

## Where Codex Stores Sessions

- Codex session files live in `~/.codex/sessions/YYYY/MM/DD/*.jsonl`.
- Each session begins with a `session_meta` JSON object whose `payload.cwd` is the working directory.

## Extract Only Useful Transcript (Ignore System Noise)

For Codex sessions, the highest-signal transcript is in:
- `event_msg.payload.type == "user_message"`
- `event_msg.payload.type == "agent_message"`

Ignore:
- `agent_reasoning`
- `token_count`

Those are useful for debugging the agent runtime, but they are mostly noise for engineering retrospectives.

## Workflow

### 1) Run the Audit for This Repo

The repo includes a session-audit helper under the `improve-skill` skill:

```bash
cd /Users/brz/repos/logseq/submodules/skills/improve-skill
./scripts/audit-sessions.js --agent codex --cwd /Users/brz/repos/logseq --limit 30 > /tmp/codex-audit.md
```

This produces:
- a per-session summary (message counts, question count, “plan-like” message count)
- repeated agent question patterns with suggested doc/skill targets

### 2) Classify Each Blind Spot

Use this rubric:

- **Repo fact missing**: agent doesn’t know “how to run tests”, “where docs are”, “what vendor is locked”.
  - Fix: patch `AGENTS.md`, `SPEC.md`, or a `docs/agent-guide/*.md` file.
- **Workflow missing**: agent repeatedly fumbles a multi-step process (local env, repro steps, common failure modes).
  - Fix: write or improve a skill in `submodules/skills/`.
- **Ambiguity / unclear request**: agent asks many questions because the request is underspecified.
  - Fix: add a small “intake checklist” section to a doc or skill (what details to ask for up front).

### 3) Create the Asset

Docs:
- Prefer putting workflow docs in `docs/agent-guide/` (follow numbering conventions; next after `002-` is `003-`, etc).

Skills:
- Skills live under `submodules/skills/<skill-name>/SKILL.md`.
- `submodules/skills/link_skills.clj` symlinks these into `~/.codex/skills/` (Codex) and `~/.config/opencode/skills/`.

### 4) Validate With the Next Session

Re-run `audit-sessions.js` after the next real session and confirm:
- the repeated question disappears, or
- the back-and-forth counts drop, or
- the agent references the new doc/skill correctly.

## Recommended Output Shape For Skill/Doc Patches

Keep fixes concrete:

- Add a single “Quick Start” block with exact commands.
- Add one “Common failure modes” section (what breaks, how to verify, how to fix).
- Add “Don’t do X” notes when a common trap exists (but keep them short).

