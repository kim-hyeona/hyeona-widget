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
    private static final int ID = 1200;

    static void refresh(Context context) {
        Context app = context.getApplicationContext();
        createChannel(app);
        post(app, "오늘의 대시보드", "일정을 불러오는 중이에요…");
        Executors.newSingleThreadExecutor().execute(() -> {
            String token = app.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("token", "");
            if (token.isEmpty()) { post(app, "현아 대시보드", "눌러서 Notion 토큰을 연결해 주세요"); return; }
            try {
                String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                List<NotionClient.Item> all = NotionClient.query(token, "calendar");
                List<NotionClient.Item> todayItems = new ArrayList<>();
                for (NotionClient.Item item : all) if (today.equals(item.date)) todayItems.add(item);
                int done = 0; StringBuilder names = new StringBuilder();
                for (NotionClient.Item item : todayItems) {
                    if (item.done) done++;
                    if (names.length() < 55 && !item.done) { if (names.length() > 0) names.append(" · "); names.append(item.title); }
                }
                String title = "오늘 할 일  " + done + "/" + todayItems.size();
                String body = todayItems.isEmpty() ? "오늘 할 일이 없어요 ♡" : (names.length() == 0 ? "오늘 할 일을 전부 완료했어요 ✓" : names.toString());
                post(app, title, body);
            } catch (Exception e) { post(app, "현아 대시보드", "Notion 연결을 확인해 주세요"); }
        });
    }

    private static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "고정 대시보드", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("일괄 지우기로 사라지지 않는 오늘의 대시보드"); channel.setShowBadge(false);
            c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private static void post(Context c, String title, String body) {
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(c, 1201, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent refresh = new Intent(c, NotificationReceiver.class).setAction(NotificationReceiver.REFRESH);
        PendingIntent refreshPi = PendingIntent.getBroadcast(c, 1202, refresh, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        Notification n = b.setSmallIcon(android.R.drawable.ic_menu_today).setContentTitle(title).setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body)).setContentIntent(openPi)
                .addAction(new Notification.Action.Builder(null, "새로고침", refreshPi).build())
                .setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(true).setShowWhen(false).build();
        n.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(ID, n);
    }
}
