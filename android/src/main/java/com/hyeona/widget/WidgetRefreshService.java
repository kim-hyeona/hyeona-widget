package com.hyeona.widget;
import android.app.job.*;
import android.content.*;
public final class WidgetRefreshService extends JobService {
 static void schedule(Context c){JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);scheduler.enqueue(new JobInfo.Builder(161,new ComponentName(c,WidgetRefreshService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setOverrideDeadline(1000).build(),new JobWorkItem(new Intent()));}
 @Override public boolean onStartJob(JobParameters p){drain(p);return true;}
 private void drain(JobParameters p){JobWorkItem work=p.dequeueWork();if(work==null){jobFinished(p,false);return;}WidgetUpdater.runRefresh(this,()->{try{p.completeWork(work);drain(p);}catch(IllegalArgumentException ignored){}});}
 @Override public boolean onStopJob(JobParameters p){return true;}
}
