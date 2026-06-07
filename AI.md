# AI Pair Programming

This project is built collaboratively with [Claude Code](https://claude.ai/code) (Anthropic). Some features are implemented by the human — the project is also used as a learning opportunity, so the split varies intentionally.

## How it works

The human acts as tech lead: defining requirements, opening issues, reviewing output, and making judgment calls. Claude handles implementation: exploring the codebase, planning, writing code, and running tests.

Each feature follows this rhythm:

1. **Plan** — Claude explores the codebase and proposes an approach; the human approves or redirects before any code is written.
2. **Implement** — Claude writes the code following the project's conventions and architecture.
3. **Review** — the human reviews the diff, flags issues, and requests refinements.
4. **Commit** — changes are committed once the human is satisfied.

## Why be transparent about this

AI-assisted development is becoming common but not always disclosed. We think it's worth being upfront: the architecture decisions, code style, and product direction are human-driven; the implementation is largely AI-generated and human-reviewed.
