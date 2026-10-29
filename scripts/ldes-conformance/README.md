# OpenLDES conformance adapter

The runner fetches the upstream test suite and harness from `main` by default,
then installs this client-owned adapter into the suite before building it.
Use `--suite-ref COMMIT` to reproduce a particular suite revision; the resolved
commit is logged and saved as `suite-revision.txt` alongside the report.

This adapter uses the production member suppliers, RDF datasets, context
metadata, and persistent node/member repositories. Its source consolidates the
adapter from suite commit `f1f2dc60a70e3dca97fdd7e264f60a7174196bf2` and the
compatibility edits previously embedded in `run-ldes-conformance.sh`. Keeping
it here lets client API changes and adapter wiring be reviewed together without
text substitutions against upstream Java source. Test cases, expectations, and
the harness remain upstream-owned.

The adapter reports unordered and ordered traversal, member extraction,
context, and persistent state. Keep `adapter.json` and the `adapter.mjs` probe
consistent when changing capabilities. The probe records the client revision
provided by the runner.

The adapter represents one synchronization run by stopping repeated node
requests. It interrupts native polling waits and caches discovery context
between runs. It therefore does not advertise autonomous polling, and does not
establish production polling timing or LDIO setup-request behavior. TREE
Profile extraction is also not advertised. These limitations must remain visible
when interpreting inapplicable scenarios and conformance results.
