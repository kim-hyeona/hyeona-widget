package com.hyeona.widget;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    public static final String SITE = "https://mulgyeol-dashboard.kha99bbb.chatgpt.site";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28,28,28,20); root.setBackgroundColor(Color.rgb(232,246,247));
        TextView title = new TextView(this); title.setText("🌊 현아 위젯"); title.setTextSize(24); title.setTextColor(Color.rgb(40,55,59)); title.setPadding(0,0,0,12); root.addView(title);
        EditText token = new EditText(this); token.setHint("Notion 내부 통합 시크릿"); token.setSingleLine(true); token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); token.setText(getSharedPreferences("prefs",MODE_PRIVATE).getString("token","")); root.addView(token,new LinearLayout.LayoutParams(-1,-2));
        Button save = new Button(this); save.setText("토큰 저장하고 위젯 새로고침"); root.addView(save,new LinearLayout.LayoutParams(-1,-2));
        save.setOnClickListener(v->{getSharedPreferences("prefs",MODE_PRIVATE).edit().putString("token",token.getText().toString().trim()).apply(); WidgetUpdater.updateAll(this); Toast.makeText(this,"위젯을 새로고침했어요",Toast.LENGTH_SHORT).show();});
        WebView web = new WebView(this); web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true); web.setWebViewClient(new WebViewClient()); web.loadUrl(SITE); root.addView(web,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);
    }
}

