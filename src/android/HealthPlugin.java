/**
 * Cordova plugin that accesses the Health Connect API
 */
package org.apache.cordova.health;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.PermissionController;
import androidx.health.connect.client.aggregate.AggregateMetric;
import androidx.health.connect.client.aggregate.AggregationResult;
import androidx.health.connect.client.aggregate.AggregationResultGroupedByDuration;
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod;
import androidx.health.connect.client.permission.HealthPermission;
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord;
import androidx.health.connect.client.records.BasalMetabolicRateRecord;
import androidx.health.connect.client.records.BloodGlucoseRecord;
import androidx.health.connect.client.records.BodyFatRecord;
import androidx.health.connect.client.records.DistanceRecord;
import androidx.health.connect.client.records.ExerciseLap;
import androidx.health.connect.client.records.ExerciseSegment;
import androidx.health.connect.client.records.ExerciseSessionRecord;
import androidx.health.connect.client.records.HeightRecord;
import androidx.health.connect.client.records.MealType;
import androidx.health.connect.client.records.Record;
import androidx.health.connect.client.records.StepsRecord;
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord;
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
import androidx.health.connect.client.units.BloodGlucose;
import androidx.health.connect.client.units.Energy;
import androidx.health.connect.client.units.Length;
import androidx.health.connect.client.units.Mass;
import androidx.health.connect.client.units.Percentage;
import androidx.health.connect.client.units.Power;
import androidx.health.platform.client.permission.Permission;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
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
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalUnit;
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
     * Used to get permissions
     */
    ActivityResultLauncher permissionsLauncher;

    /**
     * Constructor
     */
    public HealthPlugin() {
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        ActivityResultContract<Set<String>, Set<String>> requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract();
        permissionsLauncher = cordova.getActivity().registerForActivityResult(requestPermissionActivityContract, new ActivityResultCallback<Set<String>>() {
            @Override
            public void onActivityResult(Set<String> result) {
                Log.d(TAG, "got results from authorization request");
                if (callbackContext != null) {
                    for (String res : result) {
                        LOG.d(TAG, res);
                    }
                    if (result.isEmpty()) {
                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, false));
                    } else {
                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, true));
                    }
                } else {
                    LOG.e(TAG, "Got activity results before callback was created");
                }
            }
        });
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


    // DATA_TYPE add here when supporting new ones
    private KClass<? extends androidx.health.connect.client.records.Record> dataTypeNameToClass(String name) {
        if (name.equalsIgnoreCase("steps")) {
            return kotlin.jvm.JvmClassMappingKt.getKotlinClass(StepsRecord.class);
        }
        if (name.equalsIgnoreCase("weight")) {
            return kotlin.jvm.JvmClassMappingKt.getKotlinClass(WeightRecord.class);
        }
        if (name.equalsIgnoreCase("fat_percentage")) {
            return kotlin.jvm.JvmClassMappingKt.getKotlinClass(BodyFatRecord.class);
        }
        if (name.equalsIgnoreCase("activity")) {
            return kotlin.jvm.JvmClassMappingKt.getKotlinClass(ExerciseSessionRecord.class);
        }
        if (name.equalsIgnoreCase("calories")) {
            return kotlin.jvm.JvmClassMappingKt.getKotlinClass(TotalCaloriesBurnedRecord.class);
        }
        if (name.equalsIgnoreCase("calories.active")) {
            return kotlin.jvm.JvmClassMappingKt.getKotlinClass(ActiveCaloriesBurnedRecord.class);
        }
        if (name.equalsIgnoreCase("calories.basal")) {
            return kotlin.jvm.JvmClassMappingKt.getKotlinClass(BasalMetabolicRateRecord.class);
        }
        if (name.equalsIgnoreCase("blood_glucose")) {
            return kotlin.jvm.JvmClassMappingKt.getKotlinClass(BloodGlucoseRecord.class);
        }
        if (name.equalsIgnoreCase("distance")) {
            return kotlin.jvm.JvmClassMappingKt.getKotlinClass(DistanceRecord.class);
        }
        if (name.equalsIgnoreCase("height")) {
            return kotlin.jvm.JvmClassMappingKt.getKotlinClass(HeightRecord.class);
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
                    String dataTypeStr = readArray.getString(j);
                    KClass<? extends Record> datatype = dataTypeNameToClass(dataTypeStr);
                    if (datatype == null) {
                        callbackContext.error("Data type not supported " + dataTypeStr);
                        return;
                    }
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
                JSONArray writeArray = readWriteObj.getJSONArray("write");
                for (int j = 0; j < writeArray.length(); j++) {
                    String dataTypeStr = writeArray.getString(j);
                    KClass<? extends Record> datatype = dataTypeNameToClass(dataTypeStr);
                    if (datatype == null) {
                        callbackContext.error("Data type not supported " + dataTypeStr);
                        return;
                    }
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

                permissionsLauncher.launch(permissionsToRequest);
            } else {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, true));
            }
        } catch (JSONException ex) {
            callbackContext.error("Cannot read request object" + ex.getMessage());
        } catch (InterruptedException ex2) {
            callbackContext.error("Thread interrupted" + ex2.getMessage());
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

            for (Object datapointObj : response.getRecords()) {
                if (datapointObj instanceof androidx.health.connect.client.records.Record) {
                    androidx.health.connect.client.records.Record datapoint = (androidx.health.connect.client.records.Record) datapointObj;
                    JSONObject obj = new JSONObject();

                    String id = datapoint.getMetadata().getId();
                    if (id != null) {
                        obj.put("id", id);
                    }

                    Device dev = datapoint.getMetadata().getDevice();
                    if (dev != null) {
                        String device = "";
                        String manufacturer = dev.getManufacturer();
                        String model = dev.getModel();
                        if (manufacturer != null || model != null) {
                            obj.put("sourceDevice", manufacturer + " " + model);
                        }
                    }

                    DataOrigin origin = datapoint.getMetadata().getDataOrigin();
                    if (origin != null) {
                        obj.put("sourceBundleId", origin.getPackageName());
                    }

                    int methodInt = datapoint.getMetadata().getRecordingMethod();
                    String method = "unknown";
                    switch (methodInt) {
                        case 1:
                            method = "actively_recorded";
                            break;
                        case 2:
                            method = "automatically_recorded";
                            break;
                        case 3:
                            method = "manual_entry";
                            break;
                    }
                    obj.put("entryMethod", method);

                    // DATA_TYPES here we need to add support for each different data type
                    if (datapoint instanceof StepsRecord) {
                        StepsRecord stepsDP = (StepsRecord) datapoint;
                        obj.put("startDate", stepsDP.getStartTime().toEpochMilli());
                        obj.put("endDate", stepsDP.getEndTime().toEpochMilli());

                        long steps = stepsDP.getCount();
                        obj.put("value", steps);
                        obj.put("unit", "count");
                    } else if (datapoint instanceof WeightRecord) {
                        WeightRecord weightDP = (WeightRecord) datapoint;
                        obj.put("startDate", weightDP.getTime().toEpochMilli());
                        obj.put("endDate", weightDP.getTime().toEpochMilli());

                        double kgs = weightDP.getWeight().getKilograms();
                        obj.put("value", kgs);
                        obj.put("unit", "kg");
                    } else if (datapoint instanceof BodyFatRecord) {
                        BodyFatRecord bodyFatDP = (BodyFatRecord) datapoint;
                        obj.put("startDate", bodyFatDP.getTime().toEpochMilli());
                        obj.put("endDate", bodyFatDP.getTime().toEpochMilli());

                        double perc = bodyFatDP.getPercentage().getValue();
                        obj.put("value", perc);
                        obj.put("unit", "%");
                    } else if (datapoint instanceof ExerciseSessionRecord) {
                        ExerciseSessionRecord activityDP = (ExerciseSessionRecord) datapoint;
                        obj.put("startDate", activityDP.getStartTime().toEpochMilli());
                        obj.put("endDate", activityDP.getEndTime().toEpochMilli());

                        int exType = activityDP.getExerciseType();
                        String activityStr = ActivityMapper.activityFromExerciseType(exType);

                        obj.put("value", activityStr);
                        obj.put("unit", "activityType");
                    } else if (datapoint instanceof TotalCaloriesBurnedRecord) {
                        TotalCaloriesBurnedRecord caloriesDP = (TotalCaloriesBurnedRecord) datapoint;
                        obj.put("startDate", caloriesDP.getStartTime().toEpochMilli());
                        obj.put("endDate", caloriesDP.getEndTime().toEpochMilli());

                        double kcals = caloriesDP.getEnergy().getKilocalories();

                        obj.put("value", kcals);
                        obj.put("unit", "kcal");
                    } else if (datapoint instanceof ActiveCaloriesBurnedRecord) {
                        ActiveCaloriesBurnedRecord caloriesDP = (ActiveCaloriesBurnedRecord) datapoint;
                        obj.put("startDate", caloriesDP.getStartTime().toEpochMilli());
                        obj.put("endDate", caloriesDP.getEndTime().toEpochMilli());

                        double kcals = caloriesDP.getEnergy().getKilocalories();

                        obj.put("value", kcals);
                        obj.put("unit", "kcal");
                    } else if (datapoint instanceof BasalMetabolicRateRecord) {
                        BasalMetabolicRateRecord basalRateDP = (BasalMetabolicRateRecord) datapoint;
                        obj.put("startDate", basalRateDP.getTime().toEpochMilli());
                        obj.put("endDate", basalRateDP.getTime().toEpochMilli());

                        Power pow = basalRateDP.getBasalMetabolicRate();
                        if (pow != null) {
                            obj.put("value", pow.getKilocaloriesPerDay());
                            obj.put("unit", "kcal/day");
                        }
                    } else if (datapoint instanceof BloodGlucoseRecord) {
                        BloodGlucoseRecord bloodGlucose = (BloodGlucoseRecord) datapoint;
                        obj.put("startDate", bloodGlucose.getTime().toEpochMilli());
                        obj.put("endDate", bloodGlucose.getTime().toEpochMilli());

                        BloodGlucose gluco = bloodGlucose.getLevel();
                        double val = gluco.getMillimolesPerLiter();

                        //  { glucose: 5.5,
                        //  meal: 'breakfast',
                        // `meal` can be: 'before_' / 'after_' + 'meal' (iOS only), 'fasting', 'breakfast', 'dinner', 'lunch', 'snack', 'unknown'
                        //  sleep: 'fully_awake',
                        // `sleep` can be (iOS only): 'fully_awake', 'before_sleep', 'on_waking', 'during_sleep'
                        //  source: 'capillary_blood' }
                        // `source` can be: 'capillary_blood' ,'interstitial_fluid', 'plasma', 'serum', 'tears', whole_blood', 'unknown'
                        JSONObject glucob = new JSONObject();
                        glucob.put("glucose", val);

                        int temp_to_meal = bloodGlucose.getRelationToMeal();
                        String meal = "";
                        if (temp_to_meal == BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL) {
                            meal = "after_";
                        } else if (temp_to_meal == BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL) {
                            meal = "before_";
                        } else if (temp_to_meal == BloodGlucoseRecord.RELATION_TO_MEAL_FASTING) {
                            meal = "fasting";
                        } else {
                            meal = "";
                        }

                        temp_to_meal = bloodGlucose.getMealType();
                        if (temp_to_meal == MealType.MEAL_TYPE_BREAKFAST) {
                            meal += "breakfast";
                        } else if (temp_to_meal == MealType.MEAL_TYPE_DINNER) {
                            meal += "dinner";
                        } else if (temp_to_meal == MealType.MEAL_TYPE_LUNCH) {
                            meal += "lunch";
                        } else if (temp_to_meal == MealType.MEAL_TYPE_SNACK) {
                            meal += "snack";
                        } else {
                            meal += "unknown";
                        }

                        glucob.put("meal", meal);

                        String source = "";
                        int sourceInt = bloodGlucose.getSpecimenSource();
                        if (sourceInt == BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID) {
                            source = "interstitial_fluid";
                        } else if (sourceInt == BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD) {
                            source = "capillary_blood";
                        } else if (sourceInt == BloodGlucoseRecord.SPECIMEN_SOURCE_PLASMA) {
                            source = "plasma";
                        } else if (sourceInt == BloodGlucoseRecord.SPECIMEN_SOURCE_SERUM) {
                            source = "serum";
                        } else if (sourceInt == BloodGlucoseRecord.SPECIMEN_SOURCE_TEARS) {
                            source = "tears";
                        } else if (sourceInt == BloodGlucoseRecord.SPECIMEN_SOURCE_WHOLE_BLOOD) {
                            source = "whole_blood";
                        } else  {
                            source = "unknown";
                        }

                        glucob.put("source", source);


                        obj.put("value", glucob);
                        obj.put("unit", "mmol/L");
                    } else if (datapoint instanceof DistanceRecord) {
                        DistanceRecord disanceR = (DistanceRecord) datapoint;
                        obj.put("startDate", disanceR.getStartTime().toEpochMilli());
                        obj.put("endDate", disanceR.getEndTime().toEpochMilli());

                        double meters = disanceR.getDistance().getMeters();

                        obj.put("value", meters);
                        obj.put("unit", "m");
                    } else if (datapoint instanceof HeightRecord) {
                        HeightRecord heigthR = (HeightRecord) datapoint;
                        obj.put("startDate", heigthR.getTime().toEpochMilli());
                        obj.put("endDate", heigthR.getTime().toEpochMilli());

                        double meters = heigthR.getHeight().getMeters();

                        obj.put("value", meters);
                        obj.put("unit", "m");
                    } else {
                        callbackContext.error("Sample received of unknown type " + datatype.toString());
                        return;
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
            Log.e(TAG, "Could not parse query object", ex);
            callbackContext.error("Could not parse query object");
        } catch (InterruptedException ex2) {
            Log.e(TAG, "Thread interrupted", ex2);
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
                } else if (bucketType.equalsIgnoreCase("day")) {
                    stLDT = LocalDateTime.of(stZDT.getYear(), stZDT.getMonth(), stZDT.getDayOfMonth(), 0, 0, 0, 0);
                    // etLDT = LocalDateTime.of(etZDT.getYear(), etZDT.getMonth(), etZDT.getDayOfMonth(), 0, 0, 0, 0);
                } else if (bucketType.equalsIgnoreCase("week")) {
                    DayOfWeek weekStart = DayOfWeek.MONDAY;
                    stLDT = LocalDateTime.of(stZDT.getYear(), stZDT.getMonth(), stZDT.getDayOfMonth(), 0, 0, 0, 0).with(TemporalAdjusters.previousOrSame(weekStart));
                    // etLDT = LocalDateTime.of(etZDT.getYear(), etZDT.getMonth(), etZDT.getDayOfMonth(), 0, 0, 0, 0).with(TemporalAdjusters.previousOrSame(weekStart));
                } else if (bucketType.equalsIgnoreCase("month")) {
                    stLDT = LocalDateTime.of(stZDT.getYear(), stZDT.getMonth(), 1, 0, 0, 0, 0);
                    // etLDT = LocalDateTime.of(etZDT.getYear(), etZDT.getMonth(), 1, 0, 0, 0, 0);
                } else if (bucketType.equalsIgnoreCase("year")) {
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
                if (period != null) {
                    AggregateGroupByPeriodRequest request;
                    // DATA_TYPE: add here support for new data types
                    if (datatype.equalsIgnoreCase("steps")) {
                        Set<AggregateMetric<Long>> metrics = new HashSet<>();
                        metrics.add(StepsRecord.COUNT_TOTAL);
                        request = new AggregateGroupByPeriodRequest(metrics, timeRange, period, dor);
                    } else if (datatype.equalsIgnoreCase("activity")) {
                        Set<AggregateMetric<Duration>> metrics = new HashSet<>();
                        metrics.add(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL);
                        request = new AggregateGroupByPeriodRequest(metrics, timeRange, period, dor);
                    } else if (datatype.equalsIgnoreCase("calories")) {
                        Set<AggregateMetric<Energy>> metrics = new HashSet<>();
                        metrics.add(TotalCaloriesBurnedRecord.ENERGY_TOTAL);
                        request = new AggregateGroupByPeriodRequest(metrics, timeRange, period, dor);
                    } else if (datatype.equalsIgnoreCase("calories.active")) {
                        Set<AggregateMetric<Energy>> metrics = new HashSet<>();
                        metrics.add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL);
                        request = new AggregateGroupByPeriodRequest(metrics, timeRange, period, dor);
                    } else if (datatype.equalsIgnoreCase("calories.basal")) {
                        Set<AggregateMetric<Energy>> metrics = new HashSet<>();
                        metrics.add(BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL);
                        request = new AggregateGroupByPeriodRequest(metrics, timeRange, period, dor);
                    } else if (datatype.equalsIgnoreCase("distance")) {
                        Set<AggregateMetric<Length>> metrics = new HashSet<>();
                        metrics.add(DistanceRecord.DISTANCE_TOTAL);
                        request = new AggregateGroupByPeriodRequest(metrics, timeRange, period, dor);
                    } else if (datatype.equalsIgnoreCase("height")) {
                        Set<AggregateMetric<Length>> metrics = new HashSet<>();
                        metrics.add(HeightRecord.HEIGHT_AVG);
                        metrics.add(HeightRecord.HEIGHT_MIN);
                        metrics.add(HeightRecord.HEIGHT_MAX);
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
                    // DATA_TYPE: add here support for new data types
                    if (datatype.equalsIgnoreCase("steps")) {
                        Set<AggregateMetric<Long>> metrics = new HashSet<>();
                        metrics.add(StepsRecord.COUNT_TOTAL);
                        request = new AggregateGroupByDurationRequest(metrics, timeRange, duration, dor);
                    } else if (datatype.equalsIgnoreCase("activity")) {
                        Set<AggregateMetric<Duration>> metrics = new HashSet<>();
                        metrics.add(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL);
                        request = new AggregateGroupByDurationRequest(metrics, timeRange, duration, dor);
                    } else if (datatype.equalsIgnoreCase("calories")) {
                        Set<AggregateMetric<Energy>> metrics = new HashSet<>();
                        metrics.add(TotalCaloriesBurnedRecord.ENERGY_TOTAL);
                        request = new AggregateGroupByDurationRequest(metrics, timeRange, duration, dor);
                    } else if (datatype.equalsIgnoreCase("calories.active")) {
                        Set<AggregateMetric<Energy>> metrics = new HashSet<>();
                        metrics.add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL);
                        request = new AggregateGroupByDurationRequest(metrics, timeRange, duration, dor);
                    } else if (datatype.equalsIgnoreCase("calories.basal")) {
                        Set<AggregateMetric<Energy>> metrics = new HashSet<>();
                        metrics.add(BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL);
                        request = new AggregateGroupByDurationRequest(metrics, timeRange, duration, dor);
                    } else if (datatype.equalsIgnoreCase("distance")) {
                        Set<AggregateMetric<Length>> metrics = new HashSet<>();
                        metrics.add(DistanceRecord.DISTANCE_TOTAL);
                        request = new AggregateGroupByDurationRequest(metrics, timeRange, duration, dor);
                    } else if (datatype.equalsIgnoreCase("height")) {
                        Set<AggregateMetric<Length>> metrics = new HashSet<>();
                        metrics.add(HeightRecord.HEIGHT_AVG);
                        metrics.add(HeightRecord.HEIGHT_MIN);
                        metrics.add(HeightRecord.HEIGHT_MAX);
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
                // DATA_TYPE add here support for new data types
                if (datatype.equalsIgnoreCase("steps")) {
                    Set<AggregateMetric<Long>> metrics = new HashSet<>();
                    metrics.add(StepsRecord.COUNT_TOTAL);
                    request = new AggregateRequest(metrics, timeRange, dor);
                } else if (datatype.equalsIgnoreCase("activity")) {
                    Set<AggregateMetric<Duration>> metrics = new HashSet<>();
                    metrics.add(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL);
                    request = new AggregateRequest(metrics, timeRange, dor);
                } else if (datatype.equalsIgnoreCase("calories")) {
                    Set<AggregateMetric<Energy>> metrics = new HashSet<>();
                    metrics.add(TotalCaloriesBurnedRecord.ENERGY_TOTAL);
                    request = new AggregateRequest(metrics, timeRange, dor);
                } else if (datatype.equalsIgnoreCase("calories.active")) {
                    Set<AggregateMetric<Energy>> metrics = new HashSet<>();
                    metrics.add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL);
                    request = new AggregateRequest(metrics, timeRange, dor);
                } else if (datatype.equalsIgnoreCase("calories.basal")) {
                    Set<AggregateMetric<Energy>> metrics = new HashSet<>();
                    metrics.add(BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL);
                    request = new AggregateRequest(metrics, timeRange, dor);
                } else if (datatype.equalsIgnoreCase("distance")) {
                    Set<AggregateMetric<Length>> metrics = new HashSet<>();
                    metrics.add(DistanceRecord.DISTANCE_TOTAL);
                    request = new AggregateRequest(metrics, timeRange, dor);
                } else if (datatype.equalsIgnoreCase("height")) {
                    Set<AggregateMetric<Length>> metrics = new HashSet<>();
                    metrics.add(HeightRecord.HEIGHT_AVG);
                    metrics.add(HeightRecord.HEIGHT_MIN);
                    metrics.add(HeightRecord.HEIGHT_MAX);
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
            Log.e(TAG, "Could not parse query object or write response object", ex);
            callbackContext.error("Could not parse query object or write response object");
        } catch (InterruptedException ex2) {
            Log.e(TAG, "Thread interrupted", ex2);
            callbackContext.error("Thread interrupted" + ex2.getMessage());
        }
    }

    private void setAggregatedVal(String datatype, JSONObject retObj, AggregationResult response) throws JSONException {
        // DATA_TYPE add here new data types when extending
        if (datatype.equalsIgnoreCase("steps")) {
            if (response.get(StepsRecord.COUNT_TOTAL) != null) {
                long val = response.get(StepsRecord.COUNT_TOTAL);
                retObj.put("value", val);
                retObj.put("unit", "'count'");
            } else {
                retObj.put("value", 0);
                retObj.put("unit", "'count'");
            }
        } else if (datatype.equalsIgnoreCase("activity")) {
            Duration val = response.get(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL);
            if (val != null) {
                long millis = val.getSeconds() * 1000;
                retObj.put("value", millis);
                retObj.put("unit", "ms");
            } else {
                retObj.put("value", 0);
                retObj.put("unit", "ms");
            }
        } else if (datatype.equalsIgnoreCase("calories")) {
            if (response.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL) != null) {
                double kcals = response.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL).getKilocalories();
                retObj.put("value", kcals);
                retObj.put("unit", "kcal");
            } else {
                retObj.put("value", 0);
                retObj.put("unit", "kcal");
            }
        } else if (datatype.equalsIgnoreCase("calories.active")) {
            if (response.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL) != null) {
                double kcals = response.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL).getKilocalories();
                retObj.put("value", kcals);
                retObj.put("unit", "kcal");
            } else {
                retObj.put("value", 0);
                retObj.put("unit", "kcal");
            }
        } else if (datatype.equalsIgnoreCase("calories.basal")) {
            if (response.get(BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL) != null) {
                double kcals = response.get(BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL).getKilocalories();
                retObj.put("value", kcals);
                retObj.put("unit", "kcal");
            } else {
                retObj.put("value", 0);
                retObj.put("unit", "kcal");
            }
        } else if (datatype.equalsIgnoreCase("distance")) {
            if (response.get(DistanceRecord.DISTANCE_TOTAL) != null) {
                double meters = response.get(DistanceRecord.DISTANCE_TOTAL).getMeters();
                retObj.put("value", meters);
                retObj.put("unit", "m");
            } else {
                retObj.put("value", 0);
                retObj.put("unit", "kcal");
            }
        }  else if (datatype.equalsIgnoreCase("height")) {
            if (response.get(DistanceRecord.DISTANCE_TOTAL) != null) {
                JSONObject heightStats = new JSONObject();
                double metersAvg = response.get(HeightRecord.HEIGHT_AVG).getMeters();
                heightStats.put("average", metersAvg);
                double metersMin = response.get(HeightRecord.HEIGHT_MIN).getMeters();
                heightStats.put("min", metersMin);
                double metersMax = response.get(HeightRecord.HEIGHT_MAX).getMeters();
                heightStats.put("max", metersMax);
                retObj.put("value", heightStats);
                retObj.put("unit", "m");
            } else {
                retObj.put("value", null);
                retObj.put("unit", "m");
            }
        } else {
            LOG.e(TAG, "Data type not recognized " + datatype);
        }
    }

    /**
     * Stores a datapoint
     *
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

            InsertRecordsResponse response;

            // DATA_TYPE here we need to add support for each different data type
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
                response = BuildersKt.runBlocking(
                        EmptyCoroutineContext.INSTANCE,
                        (s, c) -> healthConnectClient.insertRecords(data, c)
                );
            } else if (datatype.equalsIgnoreCase("weight")) {
                double kgs = args.getJSONObject(0).getDouble("value");

                // TODO: we could add meta data when storing, including entry method, client ID and device
                WeightRecord record = new WeightRecord(
                        Instant.ofEpochMilli(st), null,
                        Mass.kilograms(kgs),
                        Metadata.EMPTY
                );
                List<WeightRecord> data = new LinkedList<>();
                data.add(record);
                response = BuildersKt.runBlocking(
                        EmptyCoroutineContext.INSTANCE,
                        (s, c) -> healthConnectClient.insertRecords(data, c)
                );
            } else if (datatype.equalsIgnoreCase("fat_percentage")) {
                double perc = args.getJSONObject(0).getDouble("value");

                BodyFatRecord record = new BodyFatRecord(
                        Instant.ofEpochMilli(st), null,
                        new Percentage(perc),
                        Metadata.EMPTY
                );
                List<BodyFatRecord> data = new LinkedList<>();
                data.add(record);
                response = BuildersKt.runBlocking(
                        EmptyCoroutineContext.INSTANCE,
                        (s, c) -> healthConnectClient.insertRecords(data, c)
                );
            } else if (datatype.equalsIgnoreCase("activity")) {
                String activityStr = args.getJSONObject(0).getString("value");
                int exerciseType = ActivityMapper.exerciseTypeFromActivity(activityStr);
                String title = null;
                String notes = null;
                List<ExerciseSegment> segments = new LinkedList<>();
                List<ExerciseLap> laps = new LinkedList<>();

                ExerciseSessionRecord record = new ExerciseSessionRecord(
                        Instant.ofEpochMilli(st), null,
                        Instant.ofEpochMilli(et), null,
                        exerciseType,
                        title, notes,
                        Metadata.EMPTY,
                        segments, laps
                );
                List<ExerciseSessionRecord> data = new LinkedList<>();
                data.add(record);
                response = BuildersKt.runBlocking(
                        EmptyCoroutineContext.INSTANCE,
                        (s, c) -> healthConnectClient.insertRecords(data, c)
                );
            } else if (datatype.equalsIgnoreCase("calories")) {
                double kcals = args.getJSONObject(0).getDouble("value");

                TotalCaloriesBurnedRecord record = new TotalCaloriesBurnedRecord(
                    Instant.ofEpochMilli(st), null,
                    Instant.ofEpochMilli(et), null,
                    Energy.kilocalories(kcals),
                    Metadata.EMPTY
                );
                List<TotalCaloriesBurnedRecord> data = new LinkedList<>();
                data.add(record);
                response = BuildersKt.runBlocking(
                    EmptyCoroutineContext.INSTANCE,
                    (s, c) -> healthConnectClient.insertRecords(data, c)
                );
            } else if (datatype.equalsIgnoreCase("calories.active")) {
                double kcals = args.getJSONObject(0).getDouble("value");

                ActiveCaloriesBurnedRecord record = new ActiveCaloriesBurnedRecord(
                        Instant.ofEpochMilli(st), null,
                        Instant.ofEpochMilli(et), null,
                        Energy.kilocalories(kcals),
                        Metadata.EMPTY
                );
                List<ActiveCaloriesBurnedRecord> data = new LinkedList<>();
                data.add(record);
                response = BuildersKt.runBlocking(
                        EmptyCoroutineContext.INSTANCE,
                        (s, c) -> healthConnectClient.insertRecords(data, c)
                );
            } else if (datatype.equalsIgnoreCase("calories.basal")) {
                double kcals = args.getJSONObject(0).getDouble("value");
                // convert kcals to power
                double ms = (et - st);
                double kcalsDay = kcals / (ms / (double) (86400000));
                Power pow = Power.kilocaloriesPerDay(kcalsDay);

                BasalMetabolicRateRecord record = new BasalMetabolicRateRecord(
                        Instant.ofEpochMilli(st), null,
                        pow,
                        Metadata.EMPTY
                );
                List<BasalMetabolicRateRecord> data = new LinkedList<>();
                data.add(record);
                response = BuildersKt.runBlocking(
                        EmptyCoroutineContext.INSTANCE,
                        (s, c) -> healthConnectClient.insertRecords(data, c)
                );
            } else if (datatype.equalsIgnoreCase("blood_glucose")) {

                JSONObject glucoseobj = args.getJSONObject(0).getJSONObject("value");
                double glucose = glucoseobj.getDouble("glucose");
                BloodGlucose level = BloodGlucose.millimolesPerLiter(glucose);

                int mealType = MealType.MEAL_TYPE_UNKNOWN;
                int relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN;

                if (glucoseobj.has("meal")) {
                    String meal = glucoseobj.getString("meal");
                    if (meal.equalsIgnoreCase("fasting")) {
                        mealType = MealType.MEAL_TYPE_UNKNOWN;
                        relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_FASTING;
                    } else {
                        if (meal.startsWith("before_")) {
                            relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL;
                            meal = meal.substring("before_".length());
                        } else if (meal.startsWith("after_")) {
                            relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL;
                            meal = meal.substring("after_".length());
                        }
                        if (meal.equalsIgnoreCase("dinner")) {
                            mealType = MealType.MEAL_TYPE_DINNER;
                        } else if (meal.equalsIgnoreCase("lunch")) {
                            mealType = MealType.MEAL_TYPE_LUNCH;
                        } else if (meal.equalsIgnoreCase("snack")) {
                            mealType = MealType.MEAL_TYPE_SNACK;
                        } else if (meal.equalsIgnoreCase("breakfast")) {
                            mealType = MealType.MEAL_TYPE_BREAKFAST;
                        }
                    }
                }

                int specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_UNKNOWN;
                if (glucoseobj.has("source")) {
                    String source = glucoseobj.getString("source");
                    if (source.equalsIgnoreCase("interstitial_fluid")) {
                        specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID;
                    } else if (source.equalsIgnoreCase("plasma")) {
                        specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_PLASMA;
                    } else if (source.equalsIgnoreCase("serum")) {
                        specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_SERUM;
                    } else if (source.equalsIgnoreCase("tears")) {
                        specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_TEARS;
                    } else if (source.equalsIgnoreCase("whole_blood")) {
                        specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_WHOLE_BLOOD;
                    } else if (source.equalsIgnoreCase("capillary_blood")) {
                        specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD;
                    }
                }

                BloodGlucoseRecord record = new BloodGlucoseRecord(
                        Instant.ofEpochMilli(st),null,
                        level,
                        specimenSource,
                        mealType,
                        relationToMeal,
                        Metadata.EMPTY);

                List<BloodGlucoseRecord> data = new LinkedList<>();
                data.add(record);
                response = BuildersKt.runBlocking(
                        EmptyCoroutineContext.INSTANCE,
                        (s, c) -> healthConnectClient.insertRecords(data, c)
                );
            } else if (datatype.equalsIgnoreCase("distance")) {
                double meters = args.getJSONObject(0).getDouble("value");
                Length len = Length.meters(meters);

                DistanceRecord record = new DistanceRecord(
                        Instant.ofEpochMilli(st), null,
                        Instant.ofEpochMilli(et), null,
                        len,
                        Metadata.EMPTY);

                List<DistanceRecord> data = new LinkedList<>();
                data.add(record);
                response = BuildersKt.runBlocking(
                        EmptyCoroutineContext.INSTANCE,
                        (s, c) -> healthConnectClient.insertRecords(data, c)
                );
            } else if (datatype.equalsIgnoreCase("height")) {
                double meters = args.getJSONObject(0).getDouble("value");
                Length len = Length.meters(meters);

                HeightRecord record = new HeightRecord(
                        Instant.ofEpochMilli(st), null,
                        len,
                        Metadata.EMPTY);

                List<HeightRecord> data = new LinkedList<>();
                data.add(record);
                response = BuildersKt.runBlocking(
                        EmptyCoroutineContext.INSTANCE,
                        (s, c) -> healthConnectClient.insertRecords(data, c)
                );
            } else {
                callbackContext.error("Datatype not supported " + datatype);
                return;
            }

            Log.d(TAG, "Data written of type " + datatype);

            String id = response.getRecordIdsList().get(0);

            callbackContext.success(id);

        } catch (JSONException ex) {
            callbackContext.error("Cannot parse request object " + ex.getMessage());
        } catch (InterruptedException ex2) {
            callbackContext.error("Thread interrupted" + ex2.getMessage());
        }
    }

    /**
     * Deletes datapoints
     *
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
                if (!args.getJSONObject(0).has("startDate")) {
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
