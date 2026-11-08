# Pixel Telo Privacy Policy

**Updated: November 8, 2026**

This policy describes the current Pixel Telo client. Number identification starts locally. Real-time lookup, official offline synchronization, quality feedback, and community reports have separate purposes.

## 1. Permissions and local access

| Permission | Purpose |
| --- | --- |
| `READ_CONTACTS` | Check whether a number is a local contact and allow it first; resolve contact names for loaded call records; support Directory integration |
| `WRITE_CONTACTS` | Meet the system permission requirement for forwarding Directory queries; the app does not directly create, edit, or delete contacts |
| `READ_CALL_LOG` | Meet the Directory lookup forwarding requirement when targeting Android API 37 or later; the app does not batch-read the system call log |
| `READ_PHONE_STATE` | Observe call connection and end for optional vibration, overlay dismissal, and post-call feedback reminders |
| `VIBRATE` | Provide optional connection and hang-up vibration |
| `INTERNET` | Real-time lookup, offline database checks and downloads, user-submitted quality feedback, and community report management |
| `POST_NOTIFICATIONS` | Notify about offline updates and post-call quality feedback; automatic update checks require notification permission |
| `SYSTEM_ALERT_WINDOW` | Show an optional call overlay only after explicit enablement and permission; disabled by default |
| `CAMERA` | Decode a self-hosted pairing QR code locally when requested; release the camera when the scanner is paused or closed |

Contact lookups use the current number or numbers in the loaded page window. The app does not enumerate or upload the address book. Names are used for local display and a bounded memory cache, and are not stored in the app database or manual backups. Saving a contact opens a system screen at the user's request.

System screening and Directory callbacks supply numbers. Outgoing calls can also enter the existing identification flow. The app does not actively batch-read, export, or upload the system `CallLog`. The permission requirements are documented in the [Android Directory reference](https://developer.android.com/reference/android/provider/ContactsContract.Directory).

## 2. Data stored on the device

- **Processing records:** Depending on settings and results, numbers, times, result types, remarks, source labels, province/city information, query durations, and query backend. Online records may also contain official feedback credentials, expiry information, use states, and historical community snapshots.
- **User rules and labels:** Numbers, prefixes, source-label or location rules, remarks, matching and force-block options, and separate local number labels.
- **Offline database:** Identification data and version information downloaded through the official synchronization channel. Offline lookups do not send the queried number to a server.
- **Preferences:** Multiple blocking, timeout, source-order, display, notification, vibration, and update-interval settings.
- **Self-hosted configuration:** Service address, TLS mode, SPKI pin, instance identity, and verification state. Tokens are encrypted using Android Keystore AES-GCM, stored separately, and excluded from system backup and device transfer.
- **Community management identity:** A randomly generated 32-byte installation identity stored in a no-backup directory. This is not an IMEI, Android ID, or hardware identifier. Possession grants management access to its reports, so it must be protected as a credential.

Temporary downloads are stored in app-private storage for verification and database replacement. Logs do not include complete phone numbers, tokens, complete self-hosted addresses, pins, or request/response bodies.

## 3. Network transmissions

### Real-time lookup

When permitted by contact checks, user rules, international-number gating, local-first lookup, and network settings, `POST /api/v2/query` sends a normalized number and source list. `GET /api/v2/sources` obtains available sources. A manual retry is a user-requested action.

The default address is `https://pixeltelo.api.mystery0.vip/`. Users may explicitly pair and enable their own HTTPS service. Self-hosted requests include that service's Bearer token. A self-hosted failure does not automatically forward the number to official real-time lookup. The selected service operator controls its own data handling; users should assess that operator before enabling it.

Requests do not include contacts, contact names, recordings, device location, or hardware identifiers. Number location information comes from the lookup service, not device positioning.

### Official offline synchronization

`GET /api/v1/sync/check` sends the current offline database version and returns update information. Data downloads require user confirmation. This channel always uses official synchronization and does not read self-hosted tokens or configuration.

Automatic checks are disabled by default. When enabled, WorkManager checks at the selected interval, which defaults to 24 hours. Checks only notify about updates; they do not start downloads. Confirmed downloads run as separate WorkManager tasks.

### Lookup quality feedback

Selecting an accurate or inaccurate result sends an existing feedback token and the feedback direction to official `POST /api/v2/query/feedback`. Self-hosted responses do not grant official feedback capability. The app does not automatically perform another lookup to obtain a token.

### Community reports and management

After confirmation, `POST /api/v2/community/reports` sends the random installation identity and an existing official feedback token. The server associates the number through that token. The `list`, `withdraw`, and `delete` operations under that endpoint send the identity and the necessary cursor or report ID.

Quality feedback and community reports are independent and do not automatically add a number to the blocklist. Report history is obtained from the official service. Deleting a local call record does not delete a server report. Active reports can be withdrawn; withdrawn history can be deleted when its state allows it. Administrative invalidation is separate from withdrawal, and deleting history does not remove a server restriction against reporting that number again.

These network operations use HTTPS. Service operators receive the requests and connection metadata needed to serve them. This client policy does not promise retention periods for official or user-selected services that the client does not control.

## 4. Backup, migration, and deletion

System backup and device transfer exclude the runtime `app-database` and encrypted self-hosted tokens. The random community identity is also excluded. Other preferences retain the existing system backup behavior.

Manual ZIP backups can include processing records, block/allow lists, and local number labels. Records may include historical community display snapshots. Backups do not include contact names, self-hosted tokens, the community identity, feedback tokens, or reporting eligibility. They contain complete numbers and user remarks; users should select trusted storage and protect their archives.

Users can disable network lookup and optional display/notification features, and delete local records, rules, labels, or the offline database. Uninstalling or clearing app data removes local data but does not automatically delete submitted server reports. Losing the old installation identity removes access to its report history. Creating a replacement identity requires confirmation and does not restore that access.

## 5. Data collection the app does not perform

The app does not read hardware identifiers, record calls, obtain device location, upload the address book or complete system call history, collect usage analytics, or automatically upload crash diagnostics. The random community identity and user-requested feedback/reports are the separate data processing described above.

## 6. Children, changes, and contact

The app is not directed to children under 13 and does not knowingly collect their personal information. Changes to this policy will be reflected here and in its date.

For privacy questions, use [GitHub Issues](https://github.com/Pixel-Tailor-CN/PixelTelo/issues). Do not include raw phone numbers, tokens, or installation identities in public reports.
