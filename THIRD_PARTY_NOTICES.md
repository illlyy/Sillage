# Third-Party Notices

This private monorepo combines source and generated assets from multiple upstream projects. The original license files remain authoritative.

## Termux application

- Upstream: https://github.com/termux/termux-app
- Base revision: `5b657c6adf4304e5198951ce815fe0205dcac29c`
- License: GNU GPL version 3 only; see `LICENSE.md`.
- Module-specific exceptions remain in the original module license files, including `termux-shared/LICENSE.md`.

## codex-web

- Upstream: https://github.com/0xcaff/codex-web
- Snapshot revision: `888692f7d885118c6a92bbaf60cf2121f5947adf`
- Upstream package metadata declares MIT licensing.
- Local Android bridge changes are stored under `vendor/codex-web/` and summarized in `vendor/codex-web/UPSTREAM.md`.

## Mihomo

- Upstream: https://github.com/MetaCubeX/mihomo
- Bundled version: `v1.19.28`
- License: GNU GPL version 3.
- License and pinned binary checksum: `app/src/main/assets/mihomo/LICENSE-GPL-3.0.txt` and `app/src/main/assets/mihomo/THIRD_PARTY_NOTICES.txt`.

## MetaCubeXD

- Upstream: https://github.com/MetaCubeX/metacubexd
- Bundled version: `v1.268.4`
- License: MIT.
- License and archive checksum: `app/src/main/assets/mihomo/LICENSE-METACUBEXD.txt` and `app/src/main/assets/mihomo/THIRD_PARTY_NOTICES.txt`.

## Codex Desktop generated WebView

`app/src/main/assets/codex-desktop/` is a generated snapshot derived from a pinned Codex Desktop package and the vendored codex-web preparation process. It is retained in this repository only to reproduce the currently runnable private Android build. No public redistribution right is asserted by this notice. Confirm the applicable upstream terms and remove the generated snapshot from public history before making the repository public.

## Official skills snapshot

`app/src/main/assets/official-skills.json` and `app/src/main/assets/official-skills.zip` are a pinned runtime snapshot used by the Android application. Their source revision and redistribution terms must be recorded and reviewed before public publication.
## RikkaHub native UI

- Upstream: https://github.com/rikkahub/rikkahub
- Snapshot revision: `de4f157910c8e2ff22aabc3ae9c03a29c96edc53`.
- Snapshot scope and update notes: `vendor/rikkahub-native/UPSTREAM.md`.
- License: segmented dual-license text retained at `vendor/rikkahub-native/LICENSE`.
- The snapshot excludes RikkaHub web-ui and backend/database/service implementations.

Fcode ports may modify the native UI for the Codex/Termux backend. Confirm upstream commercial/public-distribution rights before publication.