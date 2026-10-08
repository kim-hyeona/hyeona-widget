package com.hyeona.widget;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.view.View;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
public class WidgetRenderInstrumentation extends Instrumentation {
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle result=new Bundle();try{runOnMainSync(()->{try{verify();}catch(Exception e){throw new RuntimeException(e);}});result.putString("stream","WIDGET_RENDER_OK\n");finish(-1,result);}catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(0,result);}}
 private void verify()throws Exception{
  Context c=getTargetContext();Class<?> dashboard=Class.forName("com.hyeona.widget.WidgetUpdater$Dashboard");Constructor<?> dc=dashboard.getDeclaredConstructor();dc.setAccessible(true);
  Method render=WidgetUpdater.class.getDeclaredMethod("views",Context.class,dashboard,Calendar.class);render.setAccessible(true);
  Field careStart=dashboard.getDeclaredField("careStart");careStart.setAccessible(true);
  Class<?> event=Class.forName("com.hyeona.widget.WidgetUpdater$Event");Constructor<?> ec=event.getDeclaredConstructor(String.class,String.class,String.class,boolean.class);ec.setAccessible(true);
  for(int offset=0;offset<12;offset++)for(int populated=0;populated<2;populated++){
   Object d=dc.newInstance();Calendar month=Calendar.getInstance();month.set(Calendar.DAY_OF_MONTH,1);month.add(Calendar.MONTH,offset);Calendar week=Calendar.getInstance();week.add(Calendar.DAY_OF_MONTH,-((week.get(Calendar.DAY_OF_WEEK)+5)%7));careStart.set(d,week);
   if(populated==1){for(String name:new String[]{"events","todos","memos","routines"}){Field f=dashboard.getDeclaredField(name);f.setAccessible(true);List items=(List)f.get(d);for(int i=0;i<5;i++)items.add(ec.newInstance("test-"+i,"테스트 데이터",new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(month.getTime()),false));}}
   RemoteViews original=(RemoteViews)render.invoke(null,c,d,month);Parcel parcel=Parcel.obtain();original.writeToParcel(parcel,0);parcel.setDataPosition(0);RemoteViews remote=RemoteViews.CREATOR.createFromParcel(parcel);parcel.recycle();
   FrameLayout parent=new FrameLayout(c);View view=remote.apply(c,parent);remote.reapply(c,view);view.measure(View.MeasureSpec.makeMeasureSpec(384,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY));view.layout(0,0,384,720);
   if(view.findViewById(R.id.meal_breakfast)==null)throw new AssertionError("Missing meal row");
  }
 }
}
