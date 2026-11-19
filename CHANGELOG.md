# Changelog

All notable changes of `multi-tour-cash-reporting-api` (Cash and Reporting service) are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [3.1.0] - 2026-11-18

Minor release after 3.0.0. Every entry below is a pull request merged into `develop`, promoted to `qa` with `git cherry-pick -x` and re-applied to `release/3.1.0` the same way; the entry is the title of the original pull request without its type. The frozen head of `qa` is `46e121aee68e1ec4e3c2a5cc4ca83c4bd731bb85`.

### Changed

#### Cross-cutting: contract of the signed cash income read (no single story is named in the pull request)

- Remove x-proposed from the signed cash income read ([PR 101](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/101), promoted by [PR 103](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/103))
- Clear the signed PROPOSED text of getInternalCashIncomeOfPayment ([PR 102](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/102), promoted by [PR 103](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/103))

## [3.0.0] - 2026-11-14

Cut 3 release. Every entry below is a pull request merged into `qa` and re-applied to `release/3.0.0` with `git cherry-pick -x`; the heading of each group is the user story named in the pull request, and the entry is the title of the pull request without its type. The frozen head of `qa` is `d31190e0edab8889e6d3f8731be207846bbf6ee1`.

### Added

#### HU-CSH-001 - Operate the daily cash

- Serve the cash income of a payment to the register-payment saga ([PR 97](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/97))

#### HU-CSH-003 - Consult the monthly report and the cash consolidation

- The monthly report, its summary and the cash consolidation ([PR 82](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/82))
- The monthly and zone events of the cash dashboard ([PR 84](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/84))

### Changed

#### HU-CSH-001 - Operate the daily cash

- The first batch of the daily cash ([PR 21](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/21))
- The second batch of the daily cash ([PR 22](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/22))
- The third batch of the daily cash ([PR 23](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/23))
- The contract and the movement concept ([PR 24](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/24))
- The write controllers and the contract gate ([PR 26](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/26))
- The fix of the concurrent cash day creation ([PR 28](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/28))
- The key per operation fix ([PR 30](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/30))
- The parametrized base of the cash day ([PR 32](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/32))
- The correction route ([PR 34](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/34))
- The CI with PostgreSQL ([PR 36](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/36))
- The compose file of the api ([PR 38](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/38))
- The replay of a key decided in the use case ([PR 43](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/43))
- The routes of job 8 ([PR 44](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/44))
- The saga income and refund routes ([PR 45](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/45))
- The reversal of a movement ([PR 46](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/46))
- The content of the cash events ([PR 50](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/50))

#### HU-CSH-002 - Consult the daily dashboard

- The domain of the dashboard ([PR 64](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/64))
- The use cases of the dashboard ([PR 65](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/65))
- The dashboard adapters and the receiving contract ([PR 73](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/73))
- The event receiver of the dashboard ([PR 74](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/74))
- The daily dashboard route and the demo fixtures ([PR 76](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/76))

#### HU-CSH-003 - Consult the monthly report and the cash consolidation

- The monthly totals of the cash report ([PR 67](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/67))
- The monthly report use cases ([PR 68](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/68))
- The idempotent monthly projection updater ([PR 69](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/69))
- The monthly projection PostgreSQL adapters ([PR 80](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/80))

#### HU-SEC-005 - Manage the tenant's internal users

- The revoked session cutoff filter ([PR 79](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/79))

#### HU-SEC-007 - Exceptional and emergency access of the Platform Administrator

- The usedAt of the exceptional access audit ([PR 58](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/58))

#### HU-WRK-007 - Relay events between domains

- The outbox routes of job 7 (part 1) ([PR 55](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/55))
- The contract of the outbox routes of job 7 (part 2) ([PR 56](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/56))

#### Cross-cutting: CI, test fixes and alignment promotions (no single story is named in the PR title, branch or body)

- Guard the hexagonal layers of cash-reporting with architecture rules ([PR 88](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/88))
- The checks on pull requests to main ([PR 90](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/90))
- Sync the contract copy with the published cash contract ([PR 95](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/95))
- Run the test workflow only on pull requests to develop and qa ([PR 99](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/99))

### Fixed

#### Cross-cutting: CI, test fixes and alignment promotions (no single story is named in the PR title, branch or body)

- The single income per payment ([PR 93](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/93))

### Security

#### HU-CSH-001 - Operate the daily cash

- The token filter and the read controllers ([PR 25](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/25))

#### HU-SEC-007 - Exceptional and emergency access of the Platform Administrator

- The exceptional access token and its audit ([PR 54](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/54))

#### Cross-cutting: CI, test fixes and alignment promotions (no single story is named in the PR title, branch or body)

- The SERVICE token check of the internal routes ([PR 87](https://github.com/code-corhuila/multi-tour-cash-reporting-api/pull/87))

[Unreleased]: https://github.com/code-corhuila/multi-tour-cash-reporting-api/compare/v3.1.0...HEAD
[3.1.0]: https://github.com/code-corhuila/multi-tour-cash-reporting-api/compare/v3.0.0...v3.1.0
[3.0.0]: https://github.com/code-corhuila/multi-tour-cash-reporting-api/releases/tag/v3.0.0
