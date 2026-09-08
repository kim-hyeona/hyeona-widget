package com.hyeona.widget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

final class NotificationKeeper {
    private static final String CHANNEL = "hyeona_pinned_dashboard";
    private static final int SCHEDULE_ID = 1200;
    private static final int TODO_ID = 1203;

    static void refresh(Context context) {
        Context app = context.getApplicationContext();
        createChannel(app);
        post(app, SCHEDULE_ID, "오늘 일정", "일정을 불러오는 중이에요…");
        post(app, TODO_ID, "오늘 할 일", "할 일을 불러오는 중이에요…");
        Executors.newSingleThreadExecutor().execute(() -> {
            String token = app.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("token", "");
            if (token.isEmpty()) {
                post(app, SCHEDULE_ID, "오늘 일정", "눌러서 Notion 토큰을 연결해 주세요");
                post(app, TODO_ID, "오늘 할 일", "눌러서 Notion 토큰을 연결해 주세요");
                return;
            }
            try { postSchedule(app, token); }
            catch (Exception e) { post(app, SCHEDULE_ID, "오늘 일정", "Notion 캘린더 연결을 확인해 주세요"); }
            try { postTodo(app, token); }
            catch (Exception e) { post(app, TODO_ID, "오늘 할 일", "Notion 루틴 연결을 확인해 주세요"); }
        });
    }

    private static void postSchedule(Context app, String token) throws Exception {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        List<NotionClient.Item> all = NotionClient.query(token, "calendar");
        List<NotionClient.Item> items = new ArrayList<>();
        for (NotionClient.Item item : all) if (today.equals(item.date)) items.add(item);
        int done = 0; StringBuilder names = new StringBuilder();
        for (NotionClient.Item item : items) {
            if (item.done) done++;
            if (names.length() < 80) { if (names.length() > 0) names.append(" · "); names.append(item.title); }
        }
        String title = "오늘 일정  " + done + "/" + items.size();
        String body = items.isEmpty() ? "오늘 일정이 없어요 ♡" : names.toString();
        post(app, SCHEDULE_ID, title, body);
    }

    private static void postTodo(Context app, String token) throws Exception {
        List<NotionClient.Item> items = NotionClient.query(token, "routine");
        int done = 0; StringBuilder names = new StringBuilder();
        for (NotionClient.Item item : items) {
            if (item.done) done++;
            else if (names.length() < 80) { if (names.length() > 0) names.append(" · "); names.append(item.title); }
        }
        String title = "오늘 할 일  " + done + "/" + items.size();
        String body = items.isEmpty() ? "등록된 할 일이 없어요 ♡" : (names.length() == 0 ? "오늘 할 일을 전부 완료했어요 ✓" : names.toString());
        post(app, TODO_ID, title, body);
    }

    private static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "고정 일정과 할 일", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("일괄 지우기로 사라지지 않는 오늘 일정과 할 일");
            channel.setShowBadge(false);
            c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private static void post(Context c, int id, String title, String body) {
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(c, id + 10, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent refresh = new Intent(c, NotificationReceiver.class).setAction(NotificationReceiver.REFRESH);
        PendingIntent refreshPi = PendingIntent.getBroadcast(c, id + 20, refresh, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        Notification n = b.setSmallIcon(android.R.drawable.ic_menu_today).setContentTitle(title).setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body)).setContentIntent(openPi)
                .addAction(new Notification.Action.Builder(null, "새로고침", refreshPi).build())
                .setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(true).setShowWhen(false).build();
        n.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, n);
    }
}
