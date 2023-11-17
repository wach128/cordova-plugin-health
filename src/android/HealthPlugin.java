/**
 * Cordova plugin that accesses the Health Connect API
 */
package org.apache.cordova.health;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.PermissionController;
import androidx.health.connect.client.aggregate.AggregateMetric;
import androidx.health.connect.client.aggregate.AggregationResult;
import androidx.health.connect.client.aggregate.AggregationResultGroupedByDuration;
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod;
import androidx.health.connect.client.permission.HealthPermission;
import androidx.health.connect.client.records.Record;
import androidx.health.connect.client.records.StepsRecord;
import androidx.health.connect.client.records.WeightRecord;
import androidx.health.connect.client.records.metadata.DataOrigin;
import androidx.health.connect.client.records.metadata.Device;
import androidx.health.connect.client.records.metadata.Metadata;
import androidx.health.connect.client.request.AggregateGroupByDurationRequest;
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest;
import androidx.health.connect.client.request.AggregateRequest;
import androidx.health.connect.client.request.ReadRecordsRequest;
import androidx.health.connect.client.response.InsertRecordsResponse;
import androidx.health.connect.client.response.ReadRecordsResponse;
import androidx.health.connect.client.time.TimeRangeFilter;
import androidx.health.connect.client.units.Mass;
import androidx.health.platform.client.permission.Permission;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.reflect.KClass;
import kotlinx.coroutines.BuildersKt;


public class HealthPlugin extends CordovaPlugin {

    /**
     * Tag used in logs
     */
    public static String TAG = "cordova-plugin-health";

    /**
     * Used to get results from intents
     */
    private final int PERMISSIONS_INTENT = 878;

    /**
     * Callback context, reference needed when used in functions initialized before the plugin is called
     */
    private CallbackContext callbackContext;

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
        this.callbackContext = callbackContext;
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
            cordova.getThreadPool().execute(() -> {
                try {
                    connectAPI();
                    checkAuthorization(args, false);
                } catch (Exception ex) {
                    callbackContext.error(ex.getMessage());
                }
            });
            return true;
        } else if ("requestAuthorization".equals(action)) {
            cordova.getThreadPool().execute(() -> {
                try {
                    connectAPI();
                    checkAuthorization(args, true);
                } catch (Exception ex) {
                    callbackContext.error(ex.getMessage());
                }
            });
            return true;
        } else if ("query".equals(action)) {
            cordova.getThreadPool().execute(() -> {
                try {
                    connectAPI();
                    query(args);
                } catch (Exception ex) {
                    callbackContext.error(ex.getMessage());
                }
            });
            return true;
        } else if ("queryAggregated".equals(action)) {
            cordova.getThreadPool().execute(() -> {
                try {
                    connectAPI();
                    queryAggregated(args);
                } catch (Exception ex) {
                    callbackContext.error(ex.getMessage());
                }
            });
            return true;
        } else if ("store".equals(action)) {
            cordova.getThreadPool().execute(() -> {
                try {
                    connectAPI();
                    store(args);
                } catch (Exception ex) {
                    callbackContext.error(ex.getMessage());
                }
            });
            return true;
        } else if ("delete".equals(action)) {
            cordova.getThreadPool().execute(() -> {
                try {
                    connectAPI();
                    delete(args);
                } catch (Exception ex) {
                    callbackContext.error(ex.getMessage());
                }
            });
            return true;
        } else {
            // Unsupported action
            return false;
        }
        return true;
    }

    /**
     * Connects to the HealthConnect API
     */
    private void connectAPI() {
        if (healthConnectClient == null) {
            healthConnectClient = HealthConnectClient.getOrCreate(cordova.getContext());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    private KClass<? extends androidx.health.connect.client.records.Record> dataTypeNameToClass(String name) {
        if (name.equalsIgnoreCase("steps")) {
            return kotlin.jvm.JvmClassMappingKt.getKotlinClass(StepsRecord.class);
        }
        if (name.equalsIgnoreCase("weight")) {
            return kotlin.jvm.JvmClassMappingKt.getKotlinClass(WeightRecord.class);
        }
        return null;
    }

    /**
     * Checks if permissions have been granted, if request is true, permissions are also requested
     *
     * @param args    json array coming from the plugin
     * @param request if true also requests permissions
     */
    private void checkAuthorization(final JSONArray args, boolean request) {
        try {
            Log.d(TAG, "checking authorization");
            JSONObject readWriteObj = args.getJSONObject(0);

            // see https://kt.academy/article/cc-other-languages
            Set<String> grantedPermissions = BuildersKt.runBlocking(
                    EmptyCoroutineContext.INSTANCE,
                    (s, c) -> healthConnectClient.getPermissionController().getGrantedPermissions(c)
            );

            Set<String> permissionsToRequest = new HashSet<>();

            if (readWriteObj.has("read")) {
                JSONArray readArray = readWriteObj.getJSONArray("read");
                for (int j = 0; j < readArray.length(); j++) {
                    KClass<? extends Record> datatype = dataTypeNameToClass(readArray.getString(j));
                    String perm = HealthPermission.getReadPermission(datatype);
                    if (!grantedPermissions.contains(perm)) {
                        if (request) {
                            permissionsToRequest.add(perm);
                        } else {
                            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, false));
                            return;
                        }
                    }
                }
            }
            if (readWriteObj.has("write")) {
                JSONArray readArray = readWriteObj.getJSONArray("read");
                for (int j = 0; j < readArray.length(); j++) {
                    KClass<? extends Record> datatype = dataTypeNameToClass(readArray.getString(j));
                    String perm = HealthPermission.getWritePermission(datatype);
                    if (!grantedPermissions.contains(perm)) {
                        if (request) {
                            permissionsToRequest.add(perm);
                        } else {
                            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, false));
                            return;
                        }
                    }
                }
            }

            if (request && !permissionsToRequest.isEmpty()) {
                Log.d(TAG, "requesting authorization");
                ActivityResultContract<Set<String>, Set<String>> requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract();
                Intent intent = requestPermissionActivityContract.createIntent(cordova.getContext(), permissionsToRequest);
                cordova.startActivityForResult(this, intent, PERMISSIONS_INTENT);
            } else {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, true));
            }
        } catch (JSONException ex) {
            callbackContext.error("Cannot read request object" + ex.getMessage());
        } catch (InterruptedException ex2) {
            callbackContext.error("Thread interrupted" + ex2.getMessage());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == PERMISSIONS_INTENT) {
            Log.d(TAG, "authorization results received");
            if (resultCode == Activity.RESULT_OK) {

                // return the list of authorizations that have been granted
                try {
                    // Example:
                    // request_blocked: 2
                    // request_blocked_reason: [android.permission.health.READ_STEPS] is not declared!

                    int blocked = intent.getExtras().getInt("request_blocked");
                    if (blocked != 0) {
                        String reason = intent.getExtras().getString("request_blocked_reason");
                        callbackContext.error("Request blocked, reason: " + reason);
                    } else {
                        // example:
                        // granted_permissions_string
                        // [androidx.health.platform.client.permission.Permission@20fb2c40]
                        Bundle bd = intent.getExtras();
                        Set<String> keys = bd.keySet();
                        for (String key : keys) {
                            Log.d(TAG, key);
                            Object v = bd.get(key);
                            if (v instanceof Permission) {
                                Permission p = (Permission) v;
                                Log.d(TAG, p.toString());
                            } else {
                                Log.d(TAG, v.toString());
                            }
                        }

                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, true));
                    }
                } catch (Exception ex) {
                    callbackContext.error("Cannot create response object " + ex.getMessage());
                }
            } else {
                callbackContext.error("Could not receive authorization");
            }
        }
    }

    private void query(final JSONArray args) {

        try {
            if (!args.getJSONObject(0).has("startDate")) {
                callbackContext.error("Missing argument startDate");
                return;
            }
            long st = args.getJSONObject(0).getLong("startDate");
            if (!args.getJSONObject(0).has("endDate")) {
                callbackContext.error("Missing argument endDate");
                return;
            }
            long et = args.getJSONObject(0).getLong("endDate");
            if (!args.getJSONObject(0).has("dataType")) {
                callbackContext.error("Missing argument dataType");
                return;
            }
            String datatype = args.getJSONObject(0).getString("dataType");
            KClass<? extends Record> dt = dataTypeNameToClass(datatype);
            if (dt == null) {
                callbackContext.error("Datatype " + datatype + " not supported");
                return;
            }

            int limit = 1000;
            if (args.getJSONObject(0).has("limit")) {
                limit = args.getJSONObject(0).getInt("limit");
            }
            boolean ascending = false;
            if (args.getJSONObject(0).has("ascending")) {
                ascending = args.getJSONObject(0).getBoolean("ascending");
            }

            if (this.healthConnectClient == null) {
                callbackContext.error("You must call requestAuthorization() before query()");
                return;
            }


            TimeRangeFilter timeRange = TimeRangeFilter.between(Instant.ofEpochMilli(st), Instant.ofEpochMilli(et));
            HashSet<DataOrigin> dor = new HashSet<>();
            ReadRecordsRequest request = new ReadRecordsRequest(dt, timeRange, dor, ascending, limit, null);
            // see https://kt.academy/article/cc-other-languages
            ReadRecordsResponse response = BuildersKt.runBlocking(
                    EmptyCoroutineContext.INSTANCE,
                    (s, c) -> healthConnectClient.readRecords(request, c)
            );

            Log.d(TAG, "Data query successful");
            JSONArray resultset = new JSONArray();

            for (Object datapointObj: response.getRecords()) {
                if (datapointObj instanceof androidx.health.connect.client.records.Record) {
                    androidx.health.connect.client.records.Record datapoint = (androidx.health.connect.client.records.Record) datapointObj;
                    JSONObject obj = new JSONObject();

                    String id = datapoint.getMetadata().getId();
                    if (id != null) {
                        obj.put("id", id);
                    }

                    Device dev = datapoint.getMetadata().getDevice();
                    if (dev != null) {
                        String device = dev.getManufacturer() + " " + dev.getModel();
                        obj.put("sourceDevice", device);
                    }

                    DataOrigin origin = datapoint.getMetadata().getDataOrigin();
                    if (origin != null) {
                        obj.put("sourceBundleId", origin.getPackageName());
                    }

                    int methodInt = datapoint.getMetadata().getRecordingMethod();
                    String method = "unknown";
                    switch (methodInt) {
                        case 1:
                            method= "actively_recorded";
                            break;
                        case 2:
                            method= "automatically_recorded";
                            break;
                        case 3:
                            method= "manual_entry";
                            break;
                    }
                    obj.put("entryMethod", method);

                    if (datapoint instanceof StepsRecord) {
                        StepsRecord stepsDP = (StepsRecord) datapoint;
                        obj.put("startDate",stepsDP.getStartTime().toEpochMilli());
                        obj.put("endDate", stepsDP.getEndTime().toEpochMilli());

                        long steps = stepsDP.getCount();
                        obj.put("value", steps);
                        obj.put("unit", "count");
                    } else if (datapoint instanceof WeightRecord) {
                        WeightRecord weightDP = (WeightRecord) datapoint;
                        obj.put("startDate",weightDP.getTime().toEpochMilli());
                        obj.put("endDate", weightDP.getTime().toEpochMilli());

                        double kgs = weightDP.getWeight().getKilograms();
                        obj.put("value", kgs);
                        obj.put("unit", "kg");
                    }

                    // add to array
                    resultset.put(obj);
                } else {
                    Log.e(TAG, "Unrecognized type for record " + datapointObj.getClass());
                }
            }
            // done:
            callbackContext.success(resultset);
        } catch (JSONException ex) {
            callbackContext.error("Could not parse query object");
        } catch (InterruptedException ex2) {
            callbackContext.error("Thread interrupted" + ex2.getMessage());
        }
    }

    private void queryAggregated(final JSONArray args) {
        try {
            if (!args.getJSONObject(0).has("startDate")) {
                callbackContext.error("Missing argument startDate");
                return;
            }
            long st = args.getJSONObject(0).getLong("startDate");

            if (!args.getJSONObject(0).has("endDate")) {
                callbackContext.error("Missing argument endDate");
                return;
            }
            long et = args.getJSONObject(0).getLong("endDate");

            if (!args.getJSONObject(0).has("dataType")) {
                callbackContext.error("Missing argument dataType");
                return;
            }
            String datatype = args.getJSONObject(0).getString("dataType");
            KClass<? extends Record> dt = dataTypeNameToClass(datatype);
            if (dt == null) {
                callbackContext.error("Datatype " + datatype + " not supported");
                return;
            }

            boolean hasbucket = args.getJSONObject(0).has("bucket");

            HashSet<DataOrigin> dor = new HashSet<>();

            if (hasbucket) {
                String bucketType = args.getJSONObject(0).getString("bucket");
                ZonedDateTime stZDT = ZonedDateTime.ofInstant(Instant.ofEpochMilli(st), ZoneId.systemDefault());
                ZonedDateTime etZDT = ZonedDateTime.ofInstant(Instant.ofEpochMilli(et), ZoneId.systemDefault());
                // reset unused fields
                // int year, Month month, int dayOfMonth, int hour, int minute, int second
                LocalDateTime stLDT;
                LocalDateTime etLDT = LocalDateTime.from(etZDT);
                if (bucketType.equalsIgnoreCase("hour")) {
                    stLDT = LocalDateTime.of(stZDT.getYear(), stZDT.getMonth(), stZDT.getDayOfMonth(), stZDT.getHour(), 0, 0, 0);
                    // etLDT = LocalDateTime.of(etZDT.getYear(), etZDT.getMonth(), etZDT.getDayOfMonth(), etZDT.getHour(), 0, 0, 0);
                } else if(bucketType.equalsIgnoreCase("day")) {
                    stLDT = LocalDateTime.of(stZDT.getYear(), stZDT.getMonth(), stZDT.getDayOfMonth(), 0, 0, 0, 0);
                    // etLDT = LocalDateTime.of(etZDT.getYear(), etZDT.getMonth(), etZDT.getDayOfMonth(), 0, 0, 0, 0);
                } else if(bucketType.equalsIgnoreCase("week")) {
                    DayOfWeek weekStart = DayOfWeek.MONDAY;
                    stLDT = LocalDateTime.of(stZDT.getYear(), stZDT.getMonth(), stZDT.getDayOfMonth(), 0, 0, 0, 0).with(TemporalAdjusters.previousOrSame(weekStart));
                    // etLDT = LocalDateTime.of(etZDT.getYear(), etZDT.getMonth(), etZDT.getDayOfMonth(), 0, 0, 0, 0).with(TemporalAdjusters.previousOrSame(weekStart));
                } else if(bucketType.equalsIgnoreCase("month")) {
                    stLDT = LocalDateTime.of(stZDT.getYear(), stZDT.getMonth(), 1, 0, 0, 0, 0);
                    // etLDT = LocalDateTime.of(etZDT.getYear(), etZDT.getMonth(), 1, 0, 0, 0, 0);
                } else if(bucketType.equalsIgnoreCase("year")) {
                    stLDT = LocalDateTime.of(stZDT.getYear(), 1, 1, 0, 0, 0, 0);
                    // etLDT = LocalDateTime.of(etZDT.getYear(), 1, 1, 0, 0, 0, 0);
                } else {
                    callbackContext.error("Bucket not recognized " + bucketType);
                    return;
                }
                TimeRangeFilter timeRange = TimeRangeFilter.between(stLDT, etLDT);

                Duration duration = null;
                Period period = null;
                if (bucketType.equalsIgnoreCase("hour")) {
                    duration = Duration.ofHours(1);
                } else if (bucketType.equalsIgnoreCase("day")) {
                    period = Period.ofDays(1);
                } else if (bucketType.equalsIgnoreCase("week")) {
                    period = Period.ofWeeks(1);
                } else if (bucketType.equalsIgnoreCase("month")) {
                    period = Period.ofMonths(1);
                } else if (bucketType.equalsIgnoreCase("year")) {
                    period = Period.ofYears(1);
                } else {
                    callbackContext.error("Bucket length not recognized " + bucketType);
                    return;
                }
                if(period != null) {
                    AggregateGroupByPeriodRequest request;
                    if (datatype.equalsIgnoreCase("steps")) {
                        Set<AggregateMetric<Long>> metrics = new HashSet<>();
                        metrics.add(StepsRecord.COUNT_TOTAL);
                        request = new AggregateGroupByPeriodRequest(metrics, timeRange, period, dor);
                    } else {
                        callbackContext.error("Datatype not recognized " + datatype);
                        return;
                    }

                    List<AggregationResultGroupedByPeriod> response = BuildersKt.runBlocking(
                            EmptyCoroutineContext.INSTANCE,
                            (s, c) -> healthConnectClient.aggregateGroupByPeriod(request, c)
                    );

                    Log.d(TAG, "Got data from query aggregated");
                    JSONArray retBucketsArr = new JSONArray();

                    for (AggregationResultGroupedByPeriod bucket : response) {
                        JSONObject retObject = new JSONObject();
                        long stbkt = bucket.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                        long etbkt = bucket.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                        retObject.put("startDate", stbkt);
                        retObject.put("endDate", etbkt);
                        setAggregatedVal(datatype, retObject, bucket.getResult());

                        retBucketsArr.put(retObject);
                    }

                    callbackContext.success(retBucketsArr);
                } else {
                    AggregateGroupByDurationRequest request;
                    if (datatype.equalsIgnoreCase("steps")) {
                        Set<AggregateMetric<Long>> metrics = new HashSet<>();
                        metrics.add(StepsRecord.COUNT_TOTAL);
                        request = new AggregateGroupByDurationRequest(metrics, timeRange, duration, dor);
                    } else {
                        callbackContext.error("Datatype not recognized " + datatype);
                        return;
                    }

                    List<AggregationResultGroupedByDuration> response = BuildersKt.runBlocking(
                            EmptyCoroutineContext.INSTANCE,
                            (s, c) -> healthConnectClient.aggregateGroupByDuration(request, c)
                    );

                    Log.d(TAG, "Got data from query aggregated");
                    JSONArray retBucketsArr = new JSONArray();

                    for (AggregationResultGroupedByDuration bucket : response) {
                        JSONObject retObject = new JSONObject();
                        long stbkt = bucket.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                        long etbkt = bucket.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                        retObject.put("startDate", stbkt);
                        retObject.put("endDate", etbkt);
                        setAggregatedVal(datatype, retObject, bucket.getResult());

                        retBucketsArr.put(retObject);
                    }

                    callbackContext.success(retBucketsArr);
                }
            } else {
                TimeRangeFilter timeRange = TimeRangeFilter.between(Instant.ofEpochMilli(st), Instant.ofEpochMilli(et));

                AggregateRequest request;
                if (datatype.equalsIgnoreCase("steps")) {
                    Set<AggregateMetric<Long>> metrics = new HashSet<>();
                    metrics.add(StepsRecord.COUNT_TOTAL);
                    request = new AggregateRequest(metrics, timeRange, dor);
                } else {
                    callbackContext.error("Datatype not recognized " + datatype);
                    return;
                }

                AggregationResult response = BuildersKt.runBlocking(
                        EmptyCoroutineContext.INSTANCE,
                        (s, c) -> healthConnectClient.aggregate(request, c)
                );
                Log.d(TAG, "Got data from query aggregated");

                JSONObject retObject = new JSONObject();
                retObject.put("startDate", st);
                retObject.put("endDate", et);
                setAggregatedVal(datatype, retObject, response);

                callbackContext.success(retObject);
            }
        } catch (JSONException ex) {
                callbackContext.error("Could not parse query object or write response object");
        } catch (InterruptedException ex2) {
            callbackContext.error("Thread interrupted" + ex2.getMessage());
        }
    }

    private void setAggregatedVal(String datatype, JSONObject retObj, AggregationResult response) throws JSONException {
        if (datatype.equalsIgnoreCase("steps")) {
            long val = response.get(StepsRecord.COUNT_TOTAL);
            retObj.put("value", val);
        }
    }

    /**
     * Stores a datapoint
     * @param args
     */
    private void store(final JSONArray args) {
        try {
            if (!args.getJSONObject(0).has("startDate")) {
                callbackContext.error("Missing argument startDate");
                return;
            }
            long st = args.getJSONObject(0).getLong("startDate");

            if (!args.getJSONObject(0).has("endDate")) {
                callbackContext.error("Missing argument endDate");
                return;
            }
            long et = args.getJSONObject(0).getLong("endDate");

            if (!args.getJSONObject(0).has("dataType")) {
                callbackContext.error("Missing argument dataType");
                return;
            }
            String datatype = args.getJSONObject(0).getString("dataType");
            KClass<? extends Record> dt = dataTypeNameToClass(datatype);
            if (dt == null) {
                callbackContext.error("Datatype " + datatype + " not supported");
                return;
            }

            if (!args.getJSONObject(0).has("value")) {
                callbackContext.error("Missing argument value");
                return;
            }

            if (datatype.equalsIgnoreCase("steps")) {
                long steps = args.getJSONObject(0).getLong("value");
                // TODO: we could add meta data when storing, including entry method, client ID and device
                StepsRecord record = new StepsRecord(
                        Instant.ofEpochMilli(st), null,
                        Instant.ofEpochMilli(et), null,
                        steps,
                        Metadata.EMPTY
                        );
                List<StepsRecord> data = new LinkedList<>();
                data.add(record);
                InsertRecordsResponse response = BuildersKt.runBlocking(
                        EmptyCoroutineContext.INSTANCE,
                        (s, c) -> healthConnectClient.insertRecords(data, c)
                );
                Log.d(TAG, "Data written of type " + datatype);

                String id = response.getRecordIdsList().get(0);

                callbackContext.success(id);
            } if (datatype.equalsIgnoreCase("weight")) {
                double kgs = args.getJSONObject(0).getDouble("value");

                // TODO: we could add meta data when storing, including entry method, client ID and device
                WeightRecord record = new WeightRecord(
                        Instant.ofEpochMilli(st), null,
                        Mass.kilograms(kgs),
                        Metadata.EMPTY
                );
                List<WeightRecord> data = new LinkedList<>();
                data.add(record);
                InsertRecordsResponse response = BuildersKt.runBlocking(
                        EmptyCoroutineContext.INSTANCE,
                        (s, c) -> healthConnectClient.insertRecords(data, c)
                );
                Log.d(TAG, "Data written of type " + datatype);

                String id = response.getRecordIdsList().get(0);

                callbackContext.success(id);
            } else {
                callbackContext.error("Datatype not supported " + datatype);
            }

        } catch (JSONException ex) {
            callbackContext.error("Cannot parse request object " + ex.getMessage());
        } catch (InterruptedException ex2) {
            callbackContext.error("Thread interrupted" + ex2.getMessage());
        }
    }

    /**
     * Deletes datapoints
     * @param args
     */
    private void delete(final JSONArray args) {
        try {
            String datatype = args.getJSONObject(0).getString("dataType");
            KClass<? extends Record> dt = dataTypeNameToClass(datatype);
            if (dt == null) {
                callbackContext.error("Datatype " + datatype + " not supported");
                return;
            }

            if (args.getJSONObject(0).has("id")) {
                String id = args.getJSONObject(0).getString("id");

                List<String> recordids = new LinkedList<>();
                recordids.add(id);
                BuildersKt.runBlocking(
                        EmptyCoroutineContext.INSTANCE,
                        (s, c) -> healthConnectClient.deleteRecords(dt, recordids, new LinkedList<>(), c)
                );
                Log.d(TAG, "Data deleted by ID of type " + datatype);

                callbackContext.success();
            } else {
                if (args.getJSONObject(0).has("startDate")) {
                    callbackContext.error("Missing argument startDate");
                    return;
                }
                final long st = args.getJSONObject(0).getLong("startDate");

                if (!args.getJSONObject(0).has("endDate")) {
                    callbackContext.error("Missing argument endDate");
                    return;
                }
                final long et = args.getJSONObject(0).getLong("endDate");

                TimeRangeFilter timeRange = TimeRangeFilter.between(Instant.ofEpochMilli(st), Instant.ofEpochMilli(et));
                BuildersKt.runBlocking(
                        EmptyCoroutineContext.INSTANCE,
                        (s, c) -> healthConnectClient.deleteRecords(dt, timeRange, c)
                );
                Log.d(TAG, "Data deleted by time range of type " + datatype);

                callbackContext.success();
            }
        } catch (JSONException ex) {
            callbackContext.error("Cannot parse request object " + ex.getMessage());
        } catch (InterruptedException ex2) {
            callbackContext.error("Thread interrupted" + ex2.getMessage());
        }
    }
}
