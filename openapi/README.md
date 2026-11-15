# Contract reference copies

`openapi.yaml` is a verbatim copy of `multi-tour-docs` `origin/main:07-api/contracts/openapi/cash-reporting.yaml`.
`_shared.yaml` is a verbatim copy of `multi-tour-docs` `origin/main:07-api/contracts/openapi/_shared.yaml`.

`multi-tour-docs` owns the contract. These copies are the ones the HTTP tests validate against (ADR-004 D7); they are kept
byte-equal to the source and are never edited here. A change to the contract is made in `multi-tour-docs` first.
