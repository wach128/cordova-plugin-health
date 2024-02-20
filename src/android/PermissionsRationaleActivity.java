package org.apache.cordova.health;

import android.app.Activity;
import android.os.Bundle;
import android.util.Patterns;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class PermissionsRationaleActivity extends Activity {

    /**
     * URL that will be opened when the activity starts
     */
    static private String url ="file:///android_asset/www/privacypolicy.html";

    /**
     * Used to set the URL to be opened by the activity
     * @param newUrl URL to be opened
     * @return true if URL is valid
     */
    static public boolean setUrl(String newUrl) {
        if (newUrl != null && Patterns.WEB_URL.matcher(newUrl).matches()) {
            PermissionsRationaleActivity.url = newUrl;
            return true;
        } else return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView myWebView = new WebView(getApplicationContext());
        myWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;                
            }
        });
        setContentView(myWebView);
        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        myWebView.loadUrl(PermissionsRationaleActivity.url);
    }
}