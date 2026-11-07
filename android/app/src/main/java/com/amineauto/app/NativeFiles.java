package com.amineauto.app;

// The bridge the site uses to save-and-open a file, and to print, from inside
// the app. A WebView does neither on its own, and both failed in silence:
//
//   - a click on an <a download> whose href is a blob: URL does NOTHING. The
//     WebView's DownloadListener is only ever called for http(s), so the
//     proforma PDF — fetched with an Authorization header, turned into a Blob
//     and handed to an anchor — produced no file and no error.
//   - window.print() is a no-op without Android's PrintManager.
//
// The JS half is frontend/src/lib/nativeFiles.ts in the site, and it installs
// itself only when window.AmineAutoNative exists — so the web side can ship
// before this APK does, and an older APK keeps behaving exactly as it did.
//
// addJavascriptInterface exposes these two methods to whatever runs in the
// WebView. That is acceptable here because Capacitor only ever navigates the
// WebView to amineauto.com (capacitor.config.json sets server.url and no
// allowNavigation, so any other host is handed to the system browser), and
// because neither method can read anything back out: one writes bytes the page
// already had into the app's own private directory, the other prints the page
// the user is looking at.

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;

public class NativeFiles {

    private final MainActivity activity;
    private final WebView webView;

    NativeFiles(MainActivity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    /**
     * Write a base64 payload into the app's own external files directory and
     * offer it to whatever on the device can open that type.
     *
     * The directory is app-private, so this needs no storage permission on any
     * version we support, and the FileProvider already declared in the manifest
     * is what makes the file readable by the viewer we hand it to. Nothing is
     * written anywhere the user did not ask for, and the name is sanitised
     * because it comes from the page.
     */
    @JavascriptInterface
    public void saveAndOpen(String base64, String filename, String mime) {
        try {
            String safe = sanitize(filename);
            File dir = new File(activity.getExternalFilesDir(null), "downloads");
            if (!dir.exists() && !dir.mkdirs()) {
                toast("تعذر إنشاء مجلد التنزيلات");
                return;
            }
            File out = new File(dir, safe);
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            FileOutputStream fos = new FileOutputStream(out);
            try {
                fos.write(bytes);
            } finally {
                fos.close();
            }

            Uri uri = FileProvider.getUriForFile(
                    activity, activity.getPackageName() + ".fileprovider", out);
            String type = (mime == null || mime.isEmpty()) ? guessMime(safe) : mime;

            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(uri, type);
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                activity.startActivity(view);
            } catch (ActivityNotFoundException noViewer) {
                // Nothing installed opens this type — a bare Android with no PDF
                // reader is common. Offer to share it instead, so the file the
                // user asked for is not silently stranded on disk.
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType(type);
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(Intent.createChooser(share, safe));
            }
        } catch (Exception e) {
            toast("تعذر فتح الملف");
        }
    }

    /**
     * Print the page through Android's print service, which also offers
     * "save as PDF". Must run on the UI thread: a JavascriptInterface call
     * arrives on the WebView's own JS thread, and touching the WebView from
     * there throws.
     */
    @JavascriptInterface
    public void printPage(final String jobName) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    PrintManager pm = (PrintManager) activity.getSystemService(Context.PRINT_SERVICE);
                    if (pm == null) {
                        toast("الطباعة غير متوفرة على هذا الجهاز");
                        return;
                    }
                    String name = (jobName == null || jobName.trim().isEmpty()) ? "AmineAuto" : jobName.trim();
                    pm.print(name, webView.createPrintDocumentAdapter(name),
                            new PrintAttributes.Builder().build());
                } catch (Exception e) {
                    toast("تعذر الطباعة");
                }
            }
        });
    }

    private void toast(final String msg) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** No path separators and no traversal: this name is chosen by the page. */
    private static String sanitize(String name) {
        String n = (name == null || name.trim().isEmpty()) ? "fichier" : name.trim();
        n = n.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "_");
        while (n.contains("..")) {
            n = n.replace("..", "_");
        }
        if (n.startsWith(".")) {
            n = "_" + n.substring(1);
        }
        return n.length() > 120 ? n.substring(n.length() - 120) : n;
    }

    private static String guessMime(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "*/*";
        String ext = name.substring(dot + 1).toLowerCase();
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return m == null ? "*/*" : m;
    }
}
