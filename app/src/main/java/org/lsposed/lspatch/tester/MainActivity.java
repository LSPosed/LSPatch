package org.lsposed.lspatch.tester;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import org.lsposed.lspatch.R;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;


public class MainActivity extends Activity {

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        XposedHelpers.findAndHookMethod(this.getClass(), "checkXposed", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(true);
            }
        });

        TextView textView = findViewById(R.id.msg);
        if (checkXposed() && checkXposed2()) {
            textView.setText("ok");
        }
        else {
            textView.setText("fail");
        }
    }

    public void onClick(View view) {
    }

    public boolean checkXposed() {
        return false;
    }

    public boolean checkXposed2() {
        return false;
    }
}
