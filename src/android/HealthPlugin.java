/**
 * Tester plugin
 */
package org.apache.cordova.health;

import android.app.Activity;
import android.content.Intent;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class HealthPlugin extends CordovaPlugin {

    public static String TAG = "cordova-plugin-health";

    /**
     * Constructor
     */
    public HealthPlugin() {
    }


    /**
     * Executes the request.
     *
     * @param action          the action to execute.
     * @param args            the exec() arguments.
     * @param callbackContext the callback context used when calling back into JavaScript.
     * @return whether the action was valid.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        if (action.equals("isAvailable")) {
            PluginResult result = new PluginResult(PluginResult.Status.OK);
            callbackContext.sendPluginResult(result);
        } else if(action.equals("setPrivacyPolicyURL")) {
            try {
                String url = args.getString(0);
                if (PermissionsRationaleActivity.setUrl(url)) {
                    PluginResult result = new PluginResult(PluginResult.Status.OK);
                    callbackContext.sendPluginResult(result);
                } else {
                    callbackContext.error("Not a valid URL");
                    return false;
                }
            } catch (JSONException ex) {
                callbackContext.error("Cannot parse the URL");
                return false;
            }

        } else if(action.equals("launchPrivacyPolicy")) {
            Activity currentActivity = this.cordova.getActivity();
            Intent activityIntent = new Intent(currentActivity, PermissionsRationaleActivity.class);
            currentActivity.startActivity(activityIntent);
            PluginResult result = new PluginResult(PluginResult.Status.OK);
            callbackContext.sendPluginResult(result);
        } else {
            // Unsupported action
            return false;
        }
        return true;
    }
}
