/**
 * Tester plugin
 */
package org.apache.cordova.health;

import android.app.Activity;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.PermissionController;
import androidx.health.connect.client.permission.HealthPermission;
import androidx.health.connect.client.records.StepsRecord;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.jvm.internal.BaseContinuationImpl;
import kotlin.jvm.functions.Function1;
import kotlin.reflect.KClass;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.Job;


public class HealthPlugin extends CordovaPlugin {

    /**
     * Tag used in logs
     */
    public static String TAG = "cordova-plugin-health";

    /**
     * API client
     */
    private HealthConnectClient healthConnectClient;

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
            int availabilityStatus = HealthConnectClient.getSdkStatus(this.cordova.getContext());
            if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE) {
                callbackContext.error("Health Connect is not available");
                return true;
            }
            if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
                callbackContext.error("Health Connect is not installed");
                return true;
            }
            callbackContext.success();
        } else if (action.equals("setPrivacyPolicyURL")) {
            try {
                String url = args.getString(0);
                if (PermissionsRationaleActivity.setUrl(url)) {
                    callbackContext.success();
                } else {
                    callbackContext.error("Not a valid URL");
                    return false;
                }
            } catch (JSONException ex) {
                callbackContext.error("Cannot parse the URL");
                return false;
            }
        } else if (action.equals("launchPrivacyPolicy")) {
            Activity currentActivity = this.cordova.getActivity();
            Intent activityIntent = new Intent(currentActivity, PermissionsRationaleActivity.class);
            currentActivity.startActivity(activityIntent);
            callbackContext.success();
        } else if ("isAuthorized".equals(action)) {
            connectAPI();
            checkAuthorization(args, callbackContext);
            return true;
        } else {
            // Unsupported action
            return false;
        }
        return true;
    }

    private void connectAPI() {
        if (healthConnectClient == null) {
            healthConnectClient = HealthConnectClient.getOrCreate(cordova.getContext());
        }
    }

    private KClass<? extends androidx.health.connect.client.records.Record> dataTypeNameToClass(String name) {
        if (name.equalsIgnoreCase("steps")) {
            return kotlin.jvm.JvmClassMappingKt.getKotlinClass(StepsRecord.class);
        }
        return null;
    }

    private void checkAuthorization(final JSONArray args, final CallbackContext callbackContext) {
        try {
            JSONObject readWriteObj = args.getJSONObject(0);

            // see https://kt.academy/article/cc-other-languages
            Set<String> grantedPermissions = BuildersKt.runBlocking(
                    EmptyCoroutineContext.INSTANCE,
                    (s, c) -> healthConnectClient.getPermissionController().getGrantedPermissions(c)
            );

            if (readWriteObj.has("read")) {
                JSONArray readArray = readWriteObj.getJSONArray("read");
                for (int j = 0; j < readArray.length(); j++) {
                    KClass datatype = dataTypeNameToClass(readArray.getString(j));
                    String perm = HealthPermission.getReadPermission(datatype);
                    if (!grantedPermissions.contains(perm)) {
                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, false));
                        return;
                    }
                }
            }
            if (readWriteObj.has("write")) {
                JSONArray readArray = readWriteObj.getJSONArray("read");
                for (int j = 0; j < readArray.length(); j++) {
                    KClass datatype = dataTypeNameToClass(readArray.getString(j));
                    String perm = HealthPermission.getWritePermission(datatype);
                    if (!grantedPermissions.contains(perm)) {
                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, false));
                        return;
                    }
                }
            }
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, true));

        } catch (JSONException ex) {
            callbackContext.error("Cannot read object" + ex.getMessage());
        } catch (InterruptedException ex2) {
            callbackContext.error("Thread interrupted" + ex2.getMessage());
        }
    }
}
