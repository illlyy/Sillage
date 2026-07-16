# Vendored codex-web source

- Upstream repository: https://github.com/0xcaff/codex-web.git
- Upstream commit at snapshot: `888692f7d885118c6a92bbaf60cf2121f5947adf`
- Source changes are captured in `LOCAL_CHANGES.patch` and are also present in the vendored files.
- The Android application loads the generated WebView snapshot from `app/src/main/assets/codex-desktop/`.
- Upstream desktop asset version from `scripts/prepare`: `APP_VERSION="26.707.30751"`
- Current Android asset snapshot: `4869 files / 188366927 bytes`
- Deterministic asset tree SHA-256: `58c26f67ae9e50b4590fb49093c45a6baba49996b2178170019c90f47acd6900`

## Update workflow

1. Update the source under this directory from the upstream repository.
2. Review and reapply `LOCAL_CHANGES.patch` or merge the equivalent changes.
3. Regenerate the WebView output using the upstream preparation/build scripts.
4. Copy the resulting `scratch/asar/webview` contents into `app/src/main/assets/codex-desktop/`.
5. Run the Android tests and inspect the generated diff before committing.

The generated desktop assets are kept as a private backup of the currently runnable app. Do not edit them by hand. Confirm redistribution terms before making this repository public.