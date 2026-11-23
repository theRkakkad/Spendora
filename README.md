# Spend-Wisely

**Thoughtfully built by Pranav Patel to solve everyday problems.**

Spend-Wisely is an Android app for keeping track of everyday expenses in rupees. Add purchases as you make them, review your daily and monthly totals, and export a CSV when you need a spreadsheet.

[Download the APK from GitHub Releases](https://github.com/MRPRANAVPATEL/Spend-Wisely/releases/latest)

## Features

- **Manual expense entry** lets you add, edit, label, and delete purchases.
- **Spending dashboard** shows today’s total and expenses for the month you select. Search by the person or business you paid or by expense type.
- **SMS detection** identifies supported bank debit and UPI alerts using configurable senders and keywords. Matching transactions appear as pending expenses for you to review.
- **Reminders** prompt you to add details to pending expenses.
- **CSV export** lets you save or share a selected month or your full expense history.

## Install and get started

> Built for personal use and Android development practice. Install it at your own discretion.

1. On the latest release page, find `Spend-Wisely-1.2.apk` under **Assets**.
2. Open the APK on a phone running **Android 10 or later** and follow Android’s installation prompts.
3. Open **Expense Tracker** from the launcher, enter your name, and tap **Add expense** to record your first purchase.
4. Open **Settings & setup**, allow **SMS**, **Notifications**, and **Appear on top**, then tap **Test popup**. The test creates a sample ₹499 pending expense, which you can delete afterwards. Real payment detection requires a supported debit SMS from your bank.

### Automatic payment alerts restored in 1.2

The earlier manual-only APK removed the incoming SMS receiver and popup activity. This version restores automatic bank SMS detection and the original payment popup. It requests access to **new incoming SMS only**, without reading the SMS inbox. It also detects when the payment notification channel is disabled and keeps an ordinary actionable notification when Android denies optional full-screen access.

This APK uses the original `com.expensetracker.app` identity. The manual-only rebuild used `com.pranav.spendwisely`, so its existing records stay in that separate app. Keep it installed while you export any records you need. This build does not import that app's CSV records. Do not uninstall any version containing records you need before exporting them.

The APK from GitHub Actions is debug-signed. Android requires a matching signing key for in-place updates. If you need to uninstall an existing copy before switching builds, export a CSV first to keep a copy of your records.

## SMS detection and privacy

Manual entry is available without SMS access. For automatic detection, open **Settings & setup**, grant SMS access, and review the sender and keyword lists. Tap **Add details** on a detected payment to label it and include it in your totals.

| Android access | Purpose |
| --- | --- |
| New incoming SMS | Checks supported debit and UPI alerts as they arrive. Does not read SMS history. |
| Notifications | Shows prompts and reminders for expenses waiting for details. |
| Appear on top | Lets the payment popup appear over another app when Android allows it. |
| Full-screen notifications (optional) | Allows a lock-screen prompt when Android grants it. Ordinary notifications work without it. |

Expense records are stored on the phone in a Room database. SMS processing runs on the device. When you choose **Export CSV**, you can save the file through Android’s file picker or share it with another app.

## Build from source

| Area | Technology |
| --- | --- |
| Language | Kotlin 1.9.24 |
| UI | Jetpack Compose and Material 3 |
| Local data | Room and Preferences DataStore |
| Reminders | WorkManager |
| Build tools | Java 17, Gradle 8.7, and Android Gradle Plugin 8.5.2 |
| Android SDK | Compile and target API 34. Minimum API 29. |

Install a JDK 17, Gradle 8.7, and Android SDK Platform 34 with Build Tools 34.0.0. Set `JAVA_HOME` and `ANDROID_HOME`, then run the commands from the repository root.

```sh
gradle testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions

[Build APK](https://github.com/MRPRANAVPATEL/Spend-Wisely/actions/workflows/build-apk.yml) runs on each push and can also be started with **Run workflow**. It runs the SMS parser unit tests and Android payment alert regression tests, then builds the debug APK. A successful run uploads the `app-debug-apk` artifact and publishes a numbered GitHub Release with `Spend-Wisely-1.2.apk` attached. Tests cover the incoming-SMS receiver and popup registrations, notification permission denial, blocked payment channels, full-screen denial, and notification links to the correct payment. A real bank SMS still needs to be checked on the phone.

---

*Thoughtfully built by Pranav Patel to make everyday expense tracking easier.*
