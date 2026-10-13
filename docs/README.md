# Gothwad Launcher — Developer Docs

Everything a contributor needs that does not belong in the README (which is for users).

| Document | What it answers |
| --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | How the app is structured: layers, process model, component map, data flow, startup path |
| [CONFIG.md](CONFIG.md) | Every persisted setting: type, default, who reads it, which UI writes it |
| [SECURITY.md](SECURITY.md) | Threat model, credential storage, the three lock-enforcement layers, permission rationale |
| [PERFORMANCE.md](PERFORMANCE.md) | Memory/startup budget, caching strategy, the rules that keep a 2 GB TV box usable |
| [CI_RELEASE.md](CI_RELEASE.md) | Workflows, secrets, version-code policy, signing, F-Droid reproducibility |
| [TESTING.md](TESTING.md) | What is covered, how to run it, what to add when you touch logic |
| [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | Device/OEM symptoms → cause → fix (boot ads, safe mode, accessibility, DPI) |
| [I18N.md](I18N.md) | String-extraction status and the exact remaining inventory |
| [ISSUE_LOG.md](ISSUE_LOG.md) | The `#NN` numbers referenced in code comments, and what each one changed |
| [baseline-profile.md](baseline-profile.md) | The shipped ART profile, and how to generate a measured one |
| [ROADMAP.md](ROADMAP.md) | Known remaining work, ordered by value/risk |

Repository-level documents live at the root: [README.md](../README.md) (user facing),
[AGENTS.md](../AGENTS.md) (hard architecture rules), [CONTRIBUTING.md](../CONTRIBUTING.md),
[CHANGELOG.md](../CHANGELOG.md).

## Two rules that are easy to break

1. **No Jetpack Compose.** See [AGENTS.md](../AGENTS.md) — this is a memory decision, not a
   style preference, and it is backed by measured numbers.
2. **One process owns the DataStore.** `ConfigStore` may only be touched from the main process.
   The `:watchdog` process communicates through small files in device-protected storage
   (`ProcessHeartbeat`), never through `ConfigStore`. Two processes on one Preferences DataStore
   file can corrupt it.

The full list of non-negotiables — density handling, fail-secure locks, no invented device state,
signing, minification — is in [AGENTS.md](../AGENTS.md).

## Tools

`tools/` holds two Python static checks that need neither a JDK nor an Android SDK. CI runs both
before Gradle, which matters because a dangling `R.string.*` or a truncated `.kt` file is otherwise
only caught several minutes later:

```bash
python3 tools/ktcheck.py app/src              # real Kotlin scanner: comments, strings, ${templates}
python3 tools/check_resources.py app/src/main # every resource reference resolves to a definition
```

## Files that are not the app

* `metadata.json` — project metadata for the tooling that hosts this repository. It is not read by
  the app and is not part of the build; leave it alone.
* `AGENTS.md` — the hard rules for contributors and coding agents alike.
* `docs/ISSUE_LOG.md` — the `#NN` numbers quoted in code comments resolve here, not to GitHub
  issues.
