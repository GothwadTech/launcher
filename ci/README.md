# CI source files

`ci/workflows/` holds the **source of truth** for the two GitHub Actions workflows.

Why the copies exist: the automated agent that maintains this repository runs with a
GitHub App installation that does **not** have the `workflows` permission, so it cannot
create or update anything under `.github/workflows/` (both `git push` and the REST
contents API are rejected with *"Resource not accessible by integration"*). Files outside
that directory can be updated normally, so the reviewed workflow content lives here and is
copied into `.github/workflows/` by a maintainer.

## Applying an update

1. Open the file here on GitHub and use **Download** (raw) — do **not** copy/paste the
   text, the web editor has silently dropped lines during a paste before, which left
   `main` with a workflow that failed to parse.
2. Go to `.github/workflows/` → **Add file → Upload files** → upload
   `build_apk.yml` (and/or `release.yml`) → **Commit changes**.
3. Check the Actions tab: a push to `main` should produce a successful *Build APKs & AAB*
   run within a few minutes.

Keeping these two files byte-identical to `.github/workflows/*` avoids drift.
