package com.amineauto.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.DownloadListener;
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
        installFileSupport();
        openSiteLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        openSiteLink(intent);
    }

    // Downloads and printing. A WebView keeps a download to itself and ignores
    // window.print() entirely, so the site had no way to produce a file or a
    // printout from inside the app — see NativeFiles.java.
    private void installFileSupport() {
        if (bridge == null) return;
        final WebView web = bridge.getWebView();
        if (web == null) return;

        web.addJavascriptInterface(new NativeFiles(this, web), "AmineAutoNative");

        // Only http(s) ever reaches a DownloadListener. blob: and data: — which
        // is what the proforma PDF and the QR codes actually produce — never do,
        // and the site routes those to AmineAutoNative.saveAndOpen instead.
        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimeType, long contentLength) {
                if (url == null || url.startsWith("blob:") || url.startsWith("data:")) return;
                try {
                    Intent out = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    out.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(out);
                } catch (Exception ignored) {
                    // No handler for this type; nothing useful to do but not crash.
                }
            }
        });
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
