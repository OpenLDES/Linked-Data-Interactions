---
layout: default
parent: LDIO Outputs
title: HTTP Out
---

# LDIO HTTP Out

***Ldio:HttpOut***

The LDIO HTTP Out is a basic Http Client that will send the given Linked Data model to a target url.

## Config

| Property         | Description                                                                                                  | Required | Default | Example                     | Supported values    |
|:-----------------|:-------------------------------------------------------------------------------------------------------------|:---------|:--------|:----------------------------|:--------------------|
| _endpoint_       | Target url.                                                                                                  | Yes      | N/A     | http://example.com/endpoint | HTTP and HTTPS urls |
| _continueOnFail_ | Indicates if the pipeline continues when the target still answers with a non-2xx status after the retries. | No       | true    | false                       | true or false       |

### Failed deliveries

When the target still answers with a non-2xx status after the configured retries (see the retry properties
below), the output always logs the failure. What happens next depends on _continueOnFail_:

- `true` (default): the member is dropped and the pipeline continues. The input is not informed, so for example
  `Ldio:HttpIn` still answers `202` and `ldio_data_out_total` still counts the member.
- `false`: the output throws an exception. The member is not counted in `ldio_data_out_total`, and the input decides
  what happens:
    - `Ldio:HttpIn` answers with an error status (`500`) instead of `202`, so the caller can retry.
    - `Ldio:HttpInPoller` logs the error; the next scheduled poll still runs. Members later in the same polled response
      are not processed in that poll.
    - `Ldio:LdesClient` halts the pipeline. The failed member is still in the client state at that point, so with a
      persisted state it is offered again when the client restarts. Resuming the pipeline through the admin
      API does not restart the client; restart the LDIO instead.

Connection errors (for example an unreachable target) are always raised to the input, regardless of this setting.

{% include ldio-core/rdf-writer.md %}

{% include ldio-core/http-requester.md %}