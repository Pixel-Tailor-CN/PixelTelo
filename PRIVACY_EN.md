# Pixel Telo Privacy Policy

**Effective date: May 21, 2026**

This Privacy Policy applies to the Pixel Telo app ("the app"). Please read this policy carefully
before using the app.

---

## 1. App Overview

Pixel Telo is a caller ID and call blocking app designed for Google Pixel and AOSP-like Android
devices. The app is built around a
**privacy-first** design: incoming call analysis is performed locally whenever possible, and network
requests are minimized.

---

## 2. Permissions We Request

| Permission       | Purpose                                                                                                                                                         |
|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `READ_CONTACTS`  | Used for Android Directory Provider integration so the system Dialer can display number labels. The app does not read or upload contact contents.               |
| `WRITE_CONTACTS` | Required for Android Directory Provider compatibility. The app does not create, modify, or delete contacts.                                                     |
| `READ_CALL_LOG`  | Required for Android SDK 37+ Directory Provider phone lookup compatibility, so the app can receive incoming-call `phone_lookup` queries from the system Dialer. |
| `INTERNET`       | Used to query number information when the local database has no match, and to update the local spam number database.                                            |
| `POST_NOTIFICATIONS` | Used to notify the user when an offline database update is available after the user enables automatic update checks. Denying this permission prevents automatic update checks from being enabled, but does not affect manual updates, caller ID, or call blocking. |

Contact and call log related permissions are used so Android can forward incoming-call or phone
number lookup requests to the app. Pixel Telo
does not scan, export, or upload contacts or the system call log. `WRITE_CONTACTS` is only used to
satisfy Android Directory Provider
permission requirements and is not used to write to the contacts database.

### 2.1 Why `READ_CALL_LOG` Is Required

Pixel Telo's core feature is to show caller ID and spam labels in the system Dialer when the user
receives a phone call. The app receives
number lookup requests through an Android `ContactsContract.Directory` Provider, for example:

```text
content://vip.mystery0.pixel.telo.provider/phone_lookup/{phoneNumber}?account_name=TeloLocal&account_type=vip.mystery0.pixel.telo&callerPackage=com.google.android.dialer
```

According to the official Android documentation, when an app targets
`Build.VERSION_CODES.CINNAMON_BUN` (API level 37) or later, a Directory
Provider must hold `READ_CALL_LOG` in order to respond to `PhoneLookup.CONTENT_FILTER_URI` or
`CommonDataKinds.Phone.CONTENT_FILTER_URI`
queries. Without this permission, the system Contacts Provider will not forward these phone lookup
queries to the app, and caller ID / spam
label display cannot work correctly.

References:

-
`ContactsContract.Directory`: <https://developer.android.com/reference/android/provider/ContactsContract.Directory>
-
`Build.VERSION_CODES.CINNAMON_BUN`: <https://developer.android.com/reference/android/os/Build.VERSION_CODES#CINNAMON_BUN>

Pixel Telo uses `READ_CALL_LOG` only to receive and handle the system phone lookup callbacks
described above. The app does not actively read,
scan, upload, or analyze the user's full system call history.

---

## 3. Data We Collect

### 3.1 Data Stored Locally

**Blocked call records (stored in the device-local database)**

After an incoming call is processed by the app, the following information may be stored locally:

- Incoming phone number
- Call timestamp
- Processing result (blocked / notify only / allowed after timeout)
- Local lookup duration in milliseconds
- Network lookup duration in milliseconds, if any
- Remark, such as an automatically generated label or a user-entered note

**Spam number database (stored in the device-local database)**

The app downloads a spam number database to the device. It contains:

- Spam numbers and their category labels, such as "spam call" or "fraud call"
- Database version information

After the database is downloaded and stored locally, number lookup does **not require network access
**.

**App settings**

The app stores one setting in Android SharedPreferences: whether to notify only instead of directly
blocking calls (`notify_only`).

### 3.2 Data Transmitted Over the Network

**Cloud number lookup**

When the local spam database has no matching result, the app may send a lookup request to:

```text
Server: https://pixeltelo.api.mystery0.vip/
Endpoint: GET /api/v1/query?number={phoneNumber}
```

Only the phone number after removing the country code is sent, for example `+8613800138000` becomes
`13800138000`.
The app does **not send** device identifiers (IMEI, Android ID, etc.), account information, location
information, or any other personal data.

Each lookup request is **stateless**. The server does not set cookies or persistent sessions.

**Database update check**

When the user manually checks for updates, the app sends the current local database version to the
server so the server can determine whether a
new version is available. This request does not contain phone numbers or personal information.

The user can also enable **automatic update checks** in Settings. This feature is disabled by
default. When enabled, the app uses WorkManager to check the offline database version in the
background at the user-configured interval, which defaults to 24 hours. Automatic checks only send
the current local database version and do not include phone numbers or personal information. When a
new version is found, the app only sends a reminder notification and does not automatically download
or install the database update.

---

## 4. Data We Do Not Collect

The app does **not collect, process, or transmit** the following information:

- Device identifiers, such as IMEI, Android ID, or serial number
- User account or identity information
- Location
- Call recordings or call contents
- Full system call log or call history not processed by the app
- Call details batch-read from the system `CallLog` database
- Contact names, email addresses, or other contact personal information
- App usage analytics
- Crash logs or diagnostic data uploaded to a server

---

## 5. How We Use Data

The data we collect is used only for the following purposes:

1. **Real-time caller identification**: determine whether an incoming call is spam
2. **Blocked call history**: show local blocked call records inside the app
3. **System Dialer integration**: display number labels in the system Dialer through Directory
   Provider
4. **Spam database maintenance**: keep the local spam number database accurate through updates

---

## 6. Data Storage and Security

- All local data is stored in the app's private Android storage directory and protected by Android
  filesystem permissions
- Network requests are encrypted with HTTPS
- Downloaded spam databases are verified with SHA-256 before installation to prevent tampering
- The app does not share user data with any third party

---

## 7. User Control

You have full control over the data stored by the app:

- **View records**: view all blocked call records on the main screen
- **Delete records**: delete blocked call records individually or in batches
- **Backup and restore**: export blocked call records as a ZIP file and restore them when needed
- **Delete number database**: delete the local spam number database in settings
- **Uninstall the app**: all local data is removed when the app is uninstalled

---

## 8. Children's Privacy

The app is not directed to children under the age of 13 and does not knowingly collect personal
information from children.

---

## 9. Changes to This Privacy Policy

If this policy changes, we will update this page and revise the effective date. We recommend
reviewing this policy periodically.

---

## 10. Contact Us

If you have any privacy-related questions or concerns, please contact us through:

- **GitHub Issue
  **: [https://github.com/Pixel-Tailor-CN/PixelTelo/issues](https://github.com/Pixel-Tailor-CN/PixelTelo/issues)
