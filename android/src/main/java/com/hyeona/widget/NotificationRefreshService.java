package com.hyeona.widget;
import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;
public final class NotificationRefreshService extends JobService {
 private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
 private final ConcurrentHashMap<Integer,Future<?>> tasks=new ConcurrentHashMap<>();
 static void ensurePeriodic(Context c){JobScheduler s=c.getSystemService(JobScheduler.class);if(s.getPendingJob(162)==null)s.schedule(new JobInfo.Builder(162,new ComponentName(c,NotificationRefreshService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60*1000L).setPersisted(true).build());}
 static void schedule(Context c){ensurePeriodic(c);c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(163,new ComponentName(c,NotificationRefreshService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setOverrideDeadline(1000).build());}
 @Override public boolean onStartJob(JobParameters p){tasks.put(p.getJobId(),WORK.submit(()->{try{NotificationKeeper.refreshNow(this);}finally{new android.os.Handler(getMainLooper()).post(()->{tasks.remove(p.getJobId());jobFinished(p,false);});}}));return true;}
 @Override public boolean onStopJob(JobParameters p){Future<?> f=tasks.remove(p.getJobId());if(f!=null)f.cancel(true);return true;}
}
