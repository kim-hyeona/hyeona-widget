# Today task dates (Android 1.6.6)

Today uses Asia/Seoul. Only tasks whose assigned start date is today are included in the default list, widget and pinned Today notification. Undated, past and future tasks remain available under the explicit All tasks view; no Notion dates are rewritten automatically.

The task editor has a native date picker. New tasks default to today; users can choose another date or clear the date. Creates/updates write the chosen date to the task database, separate from calendar events. Completed tasks assigned to today remain visible in the app.

Requests have a date filter, followed by a local date check. Widget caches store task dates and a cache day; legacy undated caches and caches from another day are rejected before display. Crossing midnight invalidates Today notifications and refreshes the widget. Late responses from another tab/request are discarded.

Regression tests cover identical task titles on multiple days, unscheduled tasks, date/time offsets, cache rollover, writes to the task database, and the native date-picker control.

## Installation identity

The old dashboard signing key was only cached in CI and that cache is no longer available. The fixed APK intentionally uses the new package com.hyeona.widget.dates and launcher label ‘현아 위젯 (날짜수정)’; it does not overwrite or delete the old app. Existing Notion records are unchanged. The user should select the new app/widget when switching. Old app notifications should be disabled to avoid duplicate, unfiltered messages.

The delivered APK is signed outside this public repository with the existing private Rabbit developer key. After Rabbit 1.7.1 is installed, the new app can import the user's already configured Notion connection through a signature-protected, mutually verified local setup provider. No key is included in source, logs or APK assets. If Rabbit has no connection configured, the normal token-entry screen remains available. CI artifacts are unsigned and are not the installable deliverable.
