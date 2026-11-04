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

It needs `JWT_PUBLIC_KEY` (PEM) or `JWT_PUBLIC_KEY_FILE` and `CASH_PERMISSION_MANAGE_CASH` (*Gestionar caja*, G-PERM) to start.

## Where its data is

In the shared PostgreSQL instance of `multi-tour-infra-postgres`, in this domain's own schema, created by the `-db`
repository. This service connects with its domain user, never an administrator. Its counters (`cash_operation_total`,
`cash_refusal_total`, `cash_operation_seconds`) live in memory; no label carries a tenant or any data of a request.

## How to test it

```bash
mvn -B verify
```

Tests that need PostgreSQL read `TEST_DATABASE_URL`, `TEST_DATABASE_USER` and `TEST_DATABASE_PASSWORD` and are skipped
without them. The contract gate (`ContractGateTest`) runs last and fails the build if an operation of the contract with
`x-hu: HU-CSH-001`, or `getHealth`, was not answered by a test; the operations of other stories are in a named allowlist.

## Definition of done of HU-CSH-001 (spec, section 13)

Run against the container image, never against `ng serve` or a demo shortcut. Credentials and the test public key are
environment variables of your own shell; nothing is kept in the repository.

1. `mvn -B verify` (with the database variables above to include the `I` tests).
2. Apply the `-db` changelog to PostgreSQL 18 (its `deploy/Dockerfile` runner), create the user `cash_reporting_app`.
3. `docker build -t cash-reporting-api .` and run it with `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_PUBLIC_KEY` and
   `CASH_PERMISSION_MANAGE_CASH` set.
4. Steps 2 to 9 of the spec (formulas, repeated opening and closing, refusals, transfer after closing, fresh tenant,
   reopening, role matrix, idempotency per tenant) are executed by:

```bash
API_URL=http://localhost:8085 JWT_PRIVATE_KEY_FILE=private.pem MANAGE_CASH=<the permission string> scripts/done-slice.sh
```

5. Step 10, the count of tests: `grep -rhoE "void [a-zA-Z0-9]+\(" src/test | wc -l` in every `src/test` tree, and each
   pull request at or under 400 changed lines without tests.

## Coverage baseline

JaCoCo writes its report in every module (`target/site/jacoco`) and, for the adapters that the app tests exercise,
in `cash-reporting-app/target/site/jacoco-aggregate`. Baseline of HU-CSH-001 T-13, without a database: core 96.66% of
instructions and 97.68% of lines, adapters 63.24% and 52.95% (97.40% and 98.13% with the database variables). The build fails if the core goes under 96% of instructions
or 97% of lines; raise those numbers when the coverage rises, never lower them.

## Where the rules are

Contract in `openapi/`; governance and documentation in
[`multi-tour-docs`](https://github.com/code-corhuila/multi-tour-docs) (spec of HU-CSH-001 under `07-api/specs/cash-reporting/`).

## Branching

Three permanent branches, none accepts a direct commit: `develop <- feat/... fix/... chore/...`,
`qa <- qa/...`, `main <- release/... hotfix/...`. Promotion is by `git cherry-pick -x`, never by merging one permanent
branch into another. `main` requires the approval of `ariel5253`.
