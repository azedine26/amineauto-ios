package com.amineauto.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

// The app is a native shell around https://amineauto.com (see capacitor.config.json).
//
// Capacitor always starts on the site's home page. When the app is opened FROM a
// link instead (an amineauto.com link tapped in WhatsApp/SMS, or a launcher
// shortcut), that link's page is loaded once the bridge is up, so the user lands
// where the TWA used to take them. Only https://amineauto.com is accepted.
public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openSiteLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        openSiteLink(intent);
    }

    private void openSiteLink(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return;
        Uri uri = intent.getData();
        if (uri == null || !"https".equals(uri.getScheme()) || !"amineauto.com".equals(uri.getHost())) return;
        if (bridge == null) return;
        final WebView web = bridge.getWebView();
        final String target = uri.toString();
        web.post(() -> web.loadUrl(target));
    }
}
