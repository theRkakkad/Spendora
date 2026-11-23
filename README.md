<div align="center">

# Spendora

### Know where your money went. Without giving your data away.

**Kotlin · Jetpack Compose · Offline-first · Android 10+**

[Releases](https://github.com/theRkakkad/Spendora/releases/latest) ·
[Issues](https://github.com/theRkakkad/Spendora/issues) ·
[Source](https://github.com/theRkakkad/Spendora)

</div>

---

## What is Spendora?

Spendora is a private, on-device expense tracker for everyday rupee spending. Record purchases by hand, catch supported bank and UPI debit alerts, clean up the details when you have a moment, and export clean CSV files whenever you need them.

It is built around one idea: **your spending history should belong to you**. There is no account, no server, no analytics, and no `INTERNET` permission in the app manifest. SMS processing, storage, reminders, and export all happen on the phone.

> Current version: **1.2** · Package identity: **`com.spendora`**

---

## Why Spendora exists

Most expense apps ask you to sign in, sync your entire financial life to someone else's cloud, and navigate a dashboard built for investors instead of people buying tea, fuel, and groceries.

Spendora takes a different route:

| Principle | How it shows up |
| --- | --- |
| **Private by default** | No network access, no account, no cloud copy. |
| **Low friction** | Add an expense in seconds, or let a supported SMS create a pending entry. |
| **Honest about automation** | Only new incoming SMS is checked; the inbox is never read. |
| **Useful offline** | Everything works in airplane mode. |
| **Portable data** | Export a month or your whole history as CSV. |
| **Understandable code** | Small Kotlin modules with clear responsibilities. |

---

## Highlights

### Manual expense entry
Add, edit, label, and delete purchases. Each entry keeps the amount, date and time, label, sender, UPI reference, and whether it came from you or from SMS.

### Spending dashboard
See today's total and the expenses for the month you select. Search by merchant, person, category text, or anything you typed into a label. Pending and saved expenses are separated so nothing gets counted before you confirm it.

### SMS detection
Spendora watches **new incoming SMS only** and matches supported debit and UPI messages using sender and keyword rules you can edit in **Settings & setup**. A match becomes a pending expense; you decide the final label and save it.

### Payment prompts
A detected payment creates an actionable notification and, when Android allows it, a full-screen popup. If full-screen access is denied, Spendora falls back to a normal notification instead of blocking your phone.

### Reminders
Pending expenses stay visible and get WorkManager reminders, including restore-after-reboot handling, so forgotten alerts do not quietly disappear.

### CSV export
Export the selected month or the entire history. Files are written through Android's file picker and can be shared with a spreadsheet app. Filenames are generated for you, for example `spendora-2026-11.csv`.

---

## How it works

```text
Incoming SMS ──▶ SmsReceiver ──▶ SmsParser ──▶ SmsProcessor
                                             │
                                             ├─▶ Room: PENDING expense
                                             ├─▶ Notification + popup
                                             └─▶ WorkManager reminder

You review ──▶ Add details ──▶ Room: SAVED expense ──▶ Dashboard totals
                                      │
                                      └─▶ CsvExporter ──▶ Your file picker
```

Manual expenses skip the SMS path and go straight into the saved list.

---

## Privacy and permissions

Spendora asks for the smallest set of permissions that makes each feature work.

| Permission | Why it is requested | What it does **not** do |
| --- | --- | --- |
| `RECEIVE_SMS` | Detect supported payment alerts as they arrive. | Does not read your SMS inbox or old messages. |
| `POST_NOTIFICATIONS` | Show pending-expense prompts and reminders. | Does not send data anywhere. |
| `SYSTEM_ALERT_WINDOW` | Show the payment popup over another app where Android permits it. | Optional; notifications still work without it. |
| `USE_FULL_SCREEN_INTENT` | Show a lock-screen prompt where Android grants it. | Optional; ordinary notifications are the fallback. |
| `RECEIVE_BOOT_COMPLETED` | Restore reminders after a restart. | Does not start at boot for any other purpose. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Keep reminders reliable. | Requested only from setup when needed. |

Additional guarantees:

- The manifest declares **no `INTERNET` permission**.
- Expense records live in a local Room database named `spendora.db`.
- Settings and SMS rules live in Preferences DataStore.
- SMS parsing runs on-device.
- Backups are disabled through `android:allowBackup="false"`.
- CSV export uses Android's file picker; Spendora does not upload the file.

---

## Install

### From a release

1. Open the [latest release](https://github.com/theRkakkad/Spendora/releases/latest).
2. Download the APK from **Assets** when a release is available.
3. On an Android 10 or newer device, open the APK and follow the install prompts.
4. Enter your name, add your first expense, then visit **Settings & setup** to configure SMS detection.

### From source

```sh
git clone https://github.com/theRkakkad/Spendora.git
cd Spendora
gradle clean testDebugUnitTest assembleDebug
```

The debug APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

#### Toolchain

| Requirement | Version |
| --- | --- |
| JDK | 17 |
| Gradle | 8.7 |
| Android SDK Platform | 34 |
| Android Build Tools | 34.0.0 |
| Kotlin | 1.9.24 |
| Android Gradle Plugin | 8.5.2 |
| minSdk / targetSdk | 29 / 34 |

Set `JAVA_HOME` and `ANDROID_HOME` before running Gradle. This repository contains Gradle wrapper configuration but no wrapper JAR, so use an installed Gradle 8.7 or add the wrapper JAR locally.

---

## Upgrade and data note

Spendora now uses the application id **`com.spendora`**.

Android treats a changed application id as a different app. If you installed an older build using `com.expensetracker.app` or `com.pranav.spendwisely`, its records stay with that old installation. Before uninstalling anything:

1. Open the old app.
2. Export a CSV.
3. Keep that file somewhere safe.
4. Install Spendora and import or re-add records manually once import support is available.

Debug builds are debug-signed. Android requires the same signing key for an in-place update, so switching signing keys may also require uninstalling first. Export before doing that.

---

## Project structure

```text
Spendora/
├── app/
│   ├── build.gradle.kts                  # App module, SDK levels, dependencies
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml       # Components and permissions
│       │   ├── java/com/spendora/app/
│       │   │   ├── SpendoraApp.kt        # Lightweight service locator
│       │   │   ├── data/                 # Expense model, Room DAO, settings
│       │   │   ├── export/               # CSV writer
│       │   │   ├── notify/               # Channels, popup, notifications, reminders
│       │   │   ├── sms/                  # Receiver, parser, processor
│       │   │   ├── ui/                   # Activity, view model, screens, theme
│       │   │   └── util/                 # Formatting and setup checks
│       │   └── res/                      # Strings, themes, icons, file paths
│       └── test/java/com/spendora/app/   # JVM unit and Robolectric tests
├── build.gradle.kts                      # Root build plugins
├── gradle.properties
├── settings.gradle.kts                   # rootProject.name = "Spendora"
└── README.md
```

---

## Architecture in brief

- **`SpendoraApp`** creates one database, one repository, one settings store, and the notification channel.
- **Room** owns the single source of truth for expenses.
- **`SmsParser`** is pure Kotlin and unit-testable.
- **`SmsProcessor`** converts parsed results into pending expenses and triggers alerts.
- **`NotificationHelper`** posts ongoing pending-expense notifications and a summary.
- **`MainViewModel`** builds dashboard state and handles save, delete, settings, and export actions.
- **Compose screens** are stateless enough to reason about and small enough to extend.

---

## Tests

Run:

```sh
gradle testDebugUnitTest
```

Current coverage includes:

- SMS parsing for supported debit and UPI formats.
- Incoming SMS receiver and popup registration behaviour.
- Notification permission denial paths.
- Disabled payment channel handling.
- Full-screen intent denial fallback.
- Notification links pointing to the correct pending expense.

A real bank SMS should still be tested on a physical device because bank message formats vary.

---

## Shipped in 2.0

| Feature | Where to find it |
| --- | --- |
| **Category picker with icons and colours** | Add/edit expense dialog. Food, Travel, Bills, Shopping, Health, Fun, and Other. |
| **Monthly budget with progress ring** | Wallet icon on the dashboard. Shows remaining amount and an over-limit warning. |
| **Search and filter chips** | Dashboard. Filter by month, category, source, status, and amount range. |
| **SMS rule tester** | Settings → Test an SMS rule. Paste a message and see the parse decision and reason. |
| **Better CSV export** | Dashboard → Export CSV. Choose columns, date range, total row, and Excel BOM. |
| **Bulk select, edit, and delete** | Checkboxes on expense rows. Set category/account or delete the selected entries in one action. |
| **Encrypted local backup and restore** | Backup icon on the dashboard. Passphrase-based AES-GCM export for device migration. |
| **Recurring expense detection** | Insights screen. Groups repeat saved payments and estimates their cadence. |
| **Home-screen widget and quick tile** | Add the Spendora widget or quick tile to see today's total and open the app. |
| **Spending insights** | Chart icon on the dashboard. Merchant rankings, weekday patterns, and month comparisons. |
| **Receipt attachments** | Add/edit expense dialog → Attach receipt. Uses a persisted content URI. |
| **Multiple accounts** | Cash, UPI, Card, and Bank are seeded on first launch; each expense records its account. |
| **Shared expenses and splits** | People icon on the dashboard. Add split shares and mark them settled. |
| **CI workflow and signed releases** | `.github/workflows/build-apk.yml` runs tests and builds debug/release APKs on every push. |

### Still planned

| Feature | Why it matters |
| --- | --- |
| **JSON export alongside CSV** | Gives power users a lossless portable format. |
| **Notification history screen** | Reopen missed prompts instead of digging through the shade. |
| **Multi-currency support** | Useful for travel, with manually maintained rates to preserve offline operation. |
| **Rule-based auto-labelling** | Learn from your edits and suggest labels for familiar merchants. |
| **Wear OS quick entry** | Record spending from the wrist. |
| **Optional user-owned cloud backup** | Only behind explicit opt-in and end-to-end encryption. |

Signed release builds require `SPENDORA_KEYSTORE_FILE`, `SPENDORA_KEYSTORE_PASSWORD`, `SPENDORA_KEY_ALIAS`, and `SPENDORA_KEY_PASSWORD` to be configured as repository secrets or local environment variables. Debug builds need no signing setup.

---

## Contributing

1. Fork the repository and create a small branch.
2. Keep changes focused and explain the reason in the pull request.
3. Run `gradle testDebugUnitTest` before opening a PR.
4. Never commit real SMS messages, bank details, UPI references, or personal expense data in tests or fixtures.
5. Prefer pure functions for parsing and formatting so they can be tested without Android.

Bug reports should include the app version, Android version, phone manufacturer, and a **redacted** description of the problem.

---

## Naming history

This project was previously branded **Spend-Wisely / Expense Tracker** and used the package `com.expensetracker.app`. The codebase, display name, namespace, database file, notification identifiers, deep-link scheme, and export filenames are now unified under **Spendora**.

---

<div align="center">

**Spendora** — spend aware, stay private.

</div>
