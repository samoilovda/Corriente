# Privacy Policy

**Corriente sends nothing anywhere, because it has no way to.**

## The short version

This app has no networking code at all. Not "we don't send your data" as a
promise — there is no HTTP client, no analytics SDK, no crash reporter, no
ad library, and no `INTERNET` permission in the manifest (verify yourself:
`grep -r uses-permission app/src/main/AndroidManifest.xml` finds nothing).
Android enforces permissions at the OS level, so an app without `INTERNET`
is physically unable to open a network socket, regardless of what its code
tries to do. This is architectural, not a setting you could turn off or a
policy that could quietly change in an update.

This is a deliberate design decision (see `docs/ARCHITECTURE.md`, ADR-013,
and `docs/INVARIANTS.md`, invariant I-24), not an oversight.

## What data exists, and where it lives

All of your financial data — accounts, categories, transactions, budgets,
recurring rules, imported history — lives in a local SQLite database inside
the app's private storage on your device. Nothing is copied off the device
automatically.

* **Backups** are files you explicitly create, in a location you choose:
  a JSON export saved via the system's document picker (SAF), or a folder
  you pick for scheduled auto-backups. Corriente only ever writes to that
  location through Android's standard file-access APIs; it does not upload
  those files anywhere itself.
* **Sharing a backup** (Settings → "Share backup") hands the file to
  Android's system share sheet. Whatever app you pick to receive it (email,
  cloud storage, chat) is responsible for what happens next — Corriente's
  part ends the moment the file is handed off.
* **Auto-backup to a folder** writes to a folder you selected (local
  storage, an SD card, or a folder that a *different* app you installed
  happens to keep synced to the cloud, e.g. a sync client for a cloud
  storage provider). Corriente does not know or care whether something else
  is watching that folder; it only ever does local file I/O.

## What Corriente does not do

* No account, no sign-in, no server-side anything — there is no server.
* No analytics, telemetry, or crash reporting sent off-device.
* No ads, no ad SDKs, no tracking identifiers.
* No third-party proprietary SDKs of any kind (see `README.md`, "Dependencies"
  section, for the full and short list of what the app is built from — all
  of it open source, none of it a networking or tracking library).
* No currency-conversion or exchange-rate lookups — the app has no concept
  of a live exchange rate; the only rates it ever shows are the ones
  implied by transfers you entered yourself between your own accounts.

## Changes to this policy

If Corriente ever grows a feature that needs the network, that would be a
significant architectural change requiring an explicit, documented decision
(an ADR) overriding I-24 — not a silent update. Until and unless that
happens, this document's first sentence is simply, verifiably true of the
code as it stands.
