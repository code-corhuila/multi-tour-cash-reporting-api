# multi-tour-cash-reporting-api

## What it is, and what it is not

The HTTP API of the cash-reporting domain of Multi Tour (Java 21, Spring Boot 3.5.x), organised as three Maven
modules: `cash-reporting-core` (domain and use cases, no framework), `cash-reporting-adapters` (HTTP and persistence)
and `cash-reporting-app` (composition root and configuration). It listens on port 8085, internal.

It is not a database owner: the schema and its migrations live in `multi-tour-cash-reporting-db`, and nothing here
creates tables.

## How to start it

Requires Java 21 and Maven 3.9.

```bash
cp .env.example .env     # names only; fill in the values and export them in your shell
mvn -B -pl cash-reporting-app -am package
java -jar cash-reporting-app/target/cash-reporting-app-0.1.0-SNAPSHOT.jar
curl http://localhost:8085/health
```

## Where its data is

In the shared PostgreSQL instance of `multi-tour-infra-postgres`, in this domain's own schema, created by the `-db`
repository. This service connects with its domain user, never an administrator. No persistence exists yet: it arrives
with the adapters of HU-CSH-001.

## How to test it

```bash
mvn -B verify
```

Tests that need PostgreSQL read `TEST_DATABASE_URL` and are skipped without it.

## Where the rules are

Contract in `openapi/`; governance and documentation in
[`multi-tour-docs`](https://github.com/code-corhuila/multi-tour-docs) (spec of HU-CSH-001 under `07-api/specs/cash-reporting/`).

## Branching

Three permanent branches, none accepts a direct commit: `develop <- feat/... fix/... chore/...`,
`qa <- qa/...`, `main <- release/... hotfix/...`. Promotion is by `git cherry-pick -x`, never by merging one permanent
branch into another. `main` requires the approval of `ariel5253`.
