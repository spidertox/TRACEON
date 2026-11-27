# Privacy and security

**Local-first.** All data lives in the app's private Room database. `android:allowBackup="false"`, so Android's cloud auto-backup never copies it.
TRACEON makes no network requests of its own and has no analytics or crash-reporting SDK. Only the optional TRACEON browser loads web pages you open.

| Data | Collected when | Where | Remove |
|---|---|---|---|
| App foreground sessions (package, start, end) | You switch on app-usage tracking **and** an app **and** grant Usage access | Database | Settings -> Delete all data |
| Shared links/text | You press Save in the Share screen | Database | Delete the item |
| Imported history | You choose a file | Database, labeled as imported | Delete items / all data |
| Pages and searches in the TRACEON browser | Tracking on and not Private mode | Database | Delete items / all data |
| Notes, tags, categories you set | You enter them | Database | Edit / delete |

**Never collected:** passwords, messages, payment details, screenshots, incognito/private sessions, contacts, location.

**Permissions:** `INTERNET` (TRACEON browser only), `PACKAGE_USAGE_STATS` (special access granted by you in system settings; TRACEON re-checks it and pauses
recording if you revoke it, with a visible warning). No accessibility service, no storage permission (files go through the system file picker).

**WebView hardening:** only http/https navigations are allowed (other schemes are blocked), file and content access are off, no JavaScript bridge,
Safe Browsing on; Private mode clears cookies, cache and history and records nothing.

**Backups and exports** are files you create and store yourself. Optional encryption: PBKDF2-HMAC-SHA256 (150k iterations) -> AES-256-GCM, random salt and IV per file;
a wrong passphrase or any tampering fails authentication. A lost passphrase cannot be recovered. Demo data is never exported.

**Not done:** the database itself is not encrypted at rest (it relies on Android's per-app sandbox and device encryption); SQLCipher would be the next step.
