package com.hyeona.widget;
import android.app.Activity; import android.graphics.Color; import android.os.Bundle; import android.text.InputType; import android.widget.*;
public class MainActivity extends Activity {
 @Override public void onCreate(Bundle state){super.onCreate(state); LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(48,56,48,40);root.setBackgroundColor(Color.rgb(232,246,247));
 TextView title=new TextView(this);title.setText("🌊 현아 위젯");title.setTextSize(27);title.setTextColor(Color.rgb(40,55,59));root.addView(title);
 TextView guide=new TextView(this);guide.setText("노션 할 일을 홈 화면 위젯에 띄워요.\n아래에 Notion 내부 통합 시크릿을 한 번만 저장한 뒤, 홈 화면을 길게 눌러 ‘현아 위젯’을 추가해 주세요.\n\nChatGPT 로그인은 필요하지 않아요.");guide.setTextSize(16);guide.setTextColor(Color.rgb(77,101,106));guide.setPadding(0,24,0,28);root.addView(guide);
 EditText token=new EditText(this);token.setHint("Notion 내부 통합 시크릿");token.setSingleLine(true);token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setText(getSharedPreferences("prefs",MODE_PRIVATE).getString("token",""));root.addView(token,new LinearLayout.LayoutParams(-1,-2));
 Button save=new Button(this);save.setText("저장하고 위젯 새로고침");root.addView(save,new LinearLayout.LayoutParams(-1,-2));save.setOnClickListener(v->{getSharedPreferences("prefs",MODE_PRIVATE).edit().putString("token",token.getText().toString().trim()).apply();WidgetUpdater.updateAll(this);Toast.makeText(this,"저장했어요. 이제 홈 화면에 위젯을 추가해 주세요 ♡",Toast.LENGTH_LONG).show();});setContentView(root);}
}
