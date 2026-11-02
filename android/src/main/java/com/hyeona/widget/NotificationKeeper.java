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
    static final int ROUTINE_ID=1204;

    static void refresh(Context c) {
        createChannel(c);restoreRoutine(c);NotificationRefreshService.schedule(c);
    }
    static void refreshNow(Context app) {
        createChannel(app);String token=app.getSharedPreferences("prefs",0).getString("token","");
        if(token.isEmpty()){post(app,ROUTINE_ID,"오늘 루틴","눌러서 Notion을 연결해 주세요");return;}
        try{postRoutine(app,NotionClient.query(token,"routine"));}catch(Exception e){restoreRoutine(app);}
        try{postSchedule(app,token);}catch(Exception ignored){}
        try{postTodo(app,token);}catch(Exception ignored){}
    }
    static void postRoutine(Context c,List<NotionClient.Item> items){
        int done=0;StringBuilder body=new StringBuilder();
        for(NotionClient.Item item:items){if(item.done)done++;if(body.length()>0)body.append("\n");body.append(item.done?"✓ ":"○ ").append(item.title);}
        String title="오늘 루틴 · "+done+"/"+items.size();
        String content=items.isEmpty()?"등록된 루틴이 없어요 · 눌러서 추가":body.toString();
        c.getSharedPreferences("prefs",0).edit().putString("routine_notification_title",title).putString("routine_notification_body",content).apply();
        post(c,ROUTINE_ID,title,content);
    }
    static void restoreRoutine(Context c){android.content.SharedPreferences p=c.getSharedPreferences("prefs",0);post(c,ROUTINE_ID,p.getString("routine_notification_title","오늘 루틴"),p.getString("routine_notification_body","루틴을 불러오는 중 · 눌러서 확인"));}

    private static void postSchedule(Context app, String token) throws Exception {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        List<NotionClient.Item> all = NotionClient.query(token, "calendar");
        List<NotionClient.Item> items = new ArrayList<>();
        for (NotionClient.Item item : all) if (item.date.startsWith(today)) items.add(item);
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
        List<NotionClient.Item> items = NotionClient.query(token, "todo");
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
            NotificationChannel channel = new NotificationChannel(CHANNEL, "고정 일정·할 일·루틴", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("오늘 일정과 할 일, 루틴을 알림창에 표시");
            channel.setShowBadge(false);
            c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private static void post(Context c, int id, String title, String body) {
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        createChannel(c);
        NotificationManager manager=c.getSystemService(NotificationManager.class);if(!manager.areNotificationsEnabled())return;
        manager.notify(id,build(c,id,title,body));
    }
    static Notification build(Context c,int id,String title,String body){
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("section",id==ROUTINE_ID?"routine":id==TODO_ID?"todo":"calendar");
        PendingIntent openPi = PendingIntent.getActivity(c, id + 10, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent refresh = new Intent(c, NotificationReceiver.class).setAction(NotificationReceiver.REFRESH);
        PendingIntent refreshPi = PendingIntent.getBroadcast(c, id + 20, refresh, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        Notification n = b.setSmallIcon(android.R.drawable.ic_menu_today).setContentTitle(title).setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body)).setContentIntent(openPi)
                .addAction(new Notification.Action.Builder(null, "새로고침", refreshPi).build())
                .setVisibility(Notification.VISIBILITY_PRIVATE).setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(true).setShowWhen(false).build();
        n.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        return n;
    }
}

