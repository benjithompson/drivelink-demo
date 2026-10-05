# DriveLink API load test (Phase 7a)

A JMeter test plan that sends app traffic to the virtual service `drivelink-proto`. It runs in BlazeMeter as the test `drivelink-api-load` (id 15966561, project "Auto" 2613758, workspace 2194183), and locally with JMeter or Taurus.

## What one virtual user does

Each thread is one app user with its own load-test car. Thread 1 uses `perf001@drivelink.test` and `DLEV26PERF0000001`, thread 2 uses `perf002` and `DLEV26PERF0000002`, up to 500. Thread 501 uses car 1 again. The cars do not share state, so each user can check its own lock state.

1. Sign in once: `POST /v1/auth/token`, `GET /v1/me`, `GET /v1/vehicles`.
2. Refresh the vehicle status: `GET /v1/vehicles/{vin}/status`, then wait 30 s.
3. At every 6th refresh (about every 3 minutes), send a remote command: `POST /v1/vehicles/{vin}/commands`. The first command is UNLOCK, then LOCK and UNLOCK alternate.
4. Poll `GET /v1/commands/{commandId}` every 2 s while the status is `QUEUED` or `PENDING` (maximum 10 polls).
5. Read the status again and check that `locked` agrees with the command. This check proves that the per-car state of `drivelink-proto` works under load.

All requests send `X-Scenario` (default `default`), `X-Client-Version` and a new `X-Correlation-Id`.

## Files

| File | Purpose |
| --- | --- |
| `drivelink-api.jmx` | The JMeter test plan. The main script of the BlazeMeter test. |
| `drivelink-load.yaml` | Taurus config: 50 users, 1 min ramp-up, 5 min hold. For local runs and for CI (Phase 8). |
| `thresholds.yml` | Taurus pass/fail rules. The BlazeMeter test has the same failure criteria. |
| `build/target.csv` | The host name of `drivelink-proto`. Gitignored. `scripts/load-target.py` writes it from `MOCK_BASE_URL`. |

## Thresholds

| Rule | Value |
| --- | --- |
| p95 of `GET /vehicles/{vin}/status` | 1,500 ms or less |
| Errors, all requests | 1 % or less |

The BlazeMeter failure criteria ignore the ramp-up. The virtual service adds 300–800 ms of think time to each answer under `default`.

## JMeter properties

| Property | Default | Note |
| --- | --- | --- |
| `users`, `rampup`, `duration` | 2, 10 s, 120 s | BlazeMeter and Taurus replace these with their load settings. |
| `scenario` | `default` | The `X-Scenario` header. Other scenarios do not keep the per-car state, so the "locked after command" check can fail. |
| `status_interval_ms` | 30000 | Time between status refreshes |
| `command_every` | 6 | A command at every *n*th refresh |
| `poll_interval_ms`, `max_polls` | 2000, 10 | Command polling |
| `target_file` | `target.csv` | Path of the host file, relative to the JMX |

## Run

```sh
python3 scripts/load-target.py              # writes load/build/target.csv (prints no URL)
cd load
jmeter -n -t drivelink-api.jmx -Jtarget_file=build/target.csv -Jusers=2 -Jduration=75 \
  -Jstatus_interval_ms=5000 -Jcommand_every=2 -l results/smoke.jtl
bzt drivelink-load.yaml                     # 50 users, 6 minutes, with the thresholds
```

`results/` and `*.jtl` are gitignored.

## BlazeMeter test `drivelink-api-load`

| Setting | Value |
| --- | --- |
| Main script | `drivelink-api.jmx` |
| Load | 50 users, 1 min ramp-up, 5 min hold, 1 step |
| Location | `us-east4-a` (100 %) |
| JMeter | Stable (5.5) |
| Failure criteria | The two thresholds above |
| Cost of one run | 50 VUH (BlazeMeter MCP estimate). The workspace allowance is 500,000 VUH. |

The test also needs `target.csv` in its files. Upload `load/build/target.csv` in the BlazeMeter UI (test → Files). It is not in the repo, because it comes from `MOCK_BASE_URL`.

To start and read runs, use the PAG `blazemeter` server: `blazemeter_execution start` with `test_id` 15966561, then `blazemeter_execution read`. Ask Ben before a 500-user run.

## Limits

- Thread numbers start at 1 on each BlazeMeter engine. With more than one engine, two users can use the same car, and the "locked after command" check can fail. Check the engine count before the 500-user run (not yet checked).
- The sign-in request body is a guess of the app request. The virtual service accepts any body for `POST /v1/auth/token`.
