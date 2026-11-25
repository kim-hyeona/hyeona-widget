# Today task dates (Android 1.6.6)

Today uses Asia/Seoul. Only tasks whose assigned start date is today are included in the default list, widget and pinned Today notification. Undated, past and future tasks remain available under the explicit All tasks view; no Notion dates are rewritten automatically.

The task editor has a native date picker. New tasks default to today; users can choose another date or clear the date. Creates/updates write the chosen date to the task database, separate from calendar events. Completed tasks assigned to today remain visible in the app.

Requests have a date filter, followed by a local date check. Widget caches store task dates and a cache day; legacy undated caches and caches from another day are rejected before display. Crossing midnight invalidates Today notifications and refreshes the widget. Late responses from another tab/request are discarded.

Regression tests cover identical task titles on multiple days, unscheduled tasks, date/time offsets, cache rollover, writes to the task database, and the native date-picker control.
