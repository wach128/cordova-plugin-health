package org.apache.cordova.health;

import androidx.health.connect.client.aggregate.AggregateMetric;
import androidx.health.connect.client.aggregate.AggregationResult;
import androidx.health.connect.client.records.NutritionRecord;
import androidx.health.connect.client.records.metadata.DataOrigin;
import androidx.health.connect.client.records.metadata.Metadata;
import androidx.health.connect.client.records.HydrationRecord;
import androidx.health.connect.client.records.NutritionRecord;
import androidx.health.connect.client.records.Record;
import androidx.health.connect.client.request.AggregateGroupByDurationRequest;
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest;
import androidx.health.connect.client.request.AggregateRequest;
import androidx.health.connect.client.time.TimeRangeFilter;
import androidx.health.connect.client.records.MealType;
import androidx.health.connect.client.units.Energy;
import androidx.health.connect.client.units.Mass;
import androidx.health.connect.client.units.Volume;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import kotlin.reflect.KClass;

public class NutritionFunctions {

    public static KClass<? extends Record> dataTypeToClass() {
        return kotlin.jvm.JvmClassMappingKt.getKotlinClass(NutritionRecord.class);
    }


    public static void populateFromQuery(Record datapoint, JSONObject obj) throws JSONException {
		
        JSONObject nutritionStats = new JSONObject();

        NutritionRecord nutritionR = (NutritionRecord) datapoint;
        nutritionStats.put("startDate", nutritionR.getStartTime().toEpochMilli());
        nutritionStats.put("endDate", nutritionR.getEndTime().toEpochMilli());

        String name = nutritionR.getName();
        nutritionStats.put("item", name);

        double kcal = nutritionR.getEnergy().getKilocalories();
        nutritionStats.put("calories", kcal);

        double sugar = nutritionR.getSugar().getGrams();
        nutritionStats.put("sugar", sugar);

        int mealType = nutritionR.getMealType();
        if(mealType == MealType.MEAL_TYPE_BREAKFAST) {
            nutritionStats.put("meal_type", "breakfast");
        } else if(mealType == MealType.MEAL_TYPE_LUNCH) {
            nutritionStats.put("meal_type", "lunch");
        } else if(mealType == MealType.MEAL_TYPE_DINNER) {
            nutritionStats.put("meal_type", "dinner");
        } else if(mealType == MealType.MEAL_TYPE_SNACK) {
            nutritionStats.put("meal_type", "snack");
        } else {
            nutritionStats.put("meal_type", "unknown");
        }

        double protein = nutritionR.getProtein().getGrams();
        nutritionStats.put("protein", protein);

        double fat = nutritionR.getTotalFat().getGrams();
        nutritionStats.put("fat.total", fat);

        double carbs = nutritionR.getTotalCarbohydrate().getGrams();
        nutritionStats.put("carbs.total", carbs);

        obj.put("value", nutritionStats);
        obj.put("unit", "meal");
    }

    public static void populateFromAggregatedQuery(AggregationResult response, JSONObject retObj) throws JSONException {
		
		JSONObject nutritionStats = new JSONObject();
		
        if (response.get(NutritionRecord.ENERGY_TOTAL) != null) {
            double sugar = response.get(NutritionRecord.SUGAR_TOTAL).getGrams();
            nutritionStats.put("sugar.total", sugar);

            double totalEnergy = response.get(NutritionRecord.ENERGY_TOTAL).getKilocalories();
            nutritionStats.put("calories", totalEnergy);

            double totalProtein = response.get(NutritionRecord.PROTEIN_TOTAL).getGrams();
            nutritionStats.put("protein", totalProtein);

            double totalFat = response.get(NutritionRecord.TOTAL_FAT_TOTAL).getGrams();
            nutritionStats.put("fat.total", totalFat);

            double totalCarbs = response.get(NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL).getGrams();
            nutritionStats.put("carbs.total", totalCarbs);

            retObj.put("value", nutritionStats);
            retObj.put("unit", "meal");
        }
		
		    retObj.put("value", nutritionStats);
        retObj.put("unit", "meal");
    }

    public static AggregateGroupByPeriodRequest prepareAggregateGroupByPeriodRequest (TimeRangeFilter timeRange, Period period, HashSet<DataOrigin> dor) {
        Set<AggregateMetric<?>> metrics = new HashSet<>();
        metrics.add(NutritionRecord.ENERGY_TOTAL);
        metrics.add(NutritionRecord.PROTEIN_TOTAL);
        metrics.add(NutritionRecord.TOTAL_FAT_TOTAL);
        metrics.add(NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL);

        return new AggregateGroupByPeriodRequest(metrics, timeRange, period, dor);
    }

    public static AggregateGroupByDurationRequest prepareAggregateGroupByDurationRequest (TimeRangeFilter timeRange, Duration duration, HashSet<DataOrigin> dor) {
        Set<AggregateMetric<?>> metrics = new HashSet<>();
        metrics.add(NutritionRecord.ENERGY_TOTAL);
        metrics.add(NutritionRecord.PROTEIN_TOTAL);
        metrics.add(NutritionRecord.TOTAL_FAT_TOTAL);
        metrics.add(NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL);
        return new AggregateGroupByDurationRequest(metrics, timeRange, duration, dor);
    }

    public static AggregateRequest prepareAggregateRequest(TimeRangeFilter timeRange, HashSet<DataOrigin> dor) {
        Set<AggregateMetric<?>> metrics = new HashSet<>();
        metrics.add(NutritionRecord.ENERGY_TOTAL);
        metrics.add(NutritionRecord.PROTEIN_TOTAL);
        metrics.add(NutritionRecord.TOTAL_FAT_TOTAL);
        metrics.add(NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL);
        return new AggregateRequest(metrics, timeRange, dor);
    }

    public static void prepareStoreRecords(JSONObject storeObj, long st, long et, List<Record> data) throws JSONException {
        JSONObject nutritionObj = storeObj.getJSONObject("value");

        int mealType = MealType.MEAL_TYPE_UNKNOWN;
      
        if (nutritionObj.has("meal_type")) {
            String meal = nutritionObj.getString("meal_type");
  
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

        double sugar = storeObj.getDouble("sugar");
        double kcal = storeObj.getDouble("calories");
        double protein = storeObj.getDouble("protein");
        double fat = storeObj.getDouble("fat.total");
        double carbs = storeObj.getDouble("carbs.total");
        String name = storeObj.getString("item");

        NutritionRecord record = new NutritionRecord(
                Instant.ofEpochMilli(st),
                null,
                Instant.ofEpochMilli(et),
                null,
                null,
                null,
                null,
                Energy.kilocalories(kcal),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Mass.grams(protein),
                null,
                null,
                null,
                null,
                Mass.grams(sugar),
                null,
                Mass.grams(carbs),
                Mass.grams(fat),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                name,
                mealType,
                Metadata.EMPTY
        );
        data.add(record);
    }

    /* Hydration */

    public static void populateHydrationFromQuery(Record datapoint, JSONObject obj) throws JSONException {
        HydrationRecord hydrationDP = (HydrationRecord) datapoint;
        obj.put("startDate", hydrationDP.getStartTime().toEpochMilli());
        obj.put("endDate", hydrationDP.getEndTime().toEpochMilli());
        
        double volume = hydrationDP.getVolume().getLiters();
        obj.put("value", volume);
        obj.put("unit", "l");
    }

    public static void populateHydrationFromAggregatedQuery(AggregationResult response, JSONObject retObj) throws JSONException {
        if (response.get(HydrationRecord.VOLUME_TOTAL) != null) {
            double liters = Objects.requireNonNull(response.get(HydrationRecord.VOLUME_TOTAL)).getLiters();
            retObj.put("value", liters);
            retObj.put("unit", "l");
        } else {
            retObj.put("value", 0);
            retObj.put("unit", "l");
        }
    }

    public static AggregateGroupByPeriodRequest prepareHydrationAggregateGroupByPeriodRequest(TimeRangeFilter timeRange, Period period, HashSet<DataOrigin> dor) {
        Set<AggregateMetric<Volume>> metrics = new HashSet<>();
        metrics.add(HydrationRecord.VOLUME_TOTAL);

        return new AggregateGroupByPeriodRequest(metrics, timeRange, period, dor);
    }

    public static AggregateGroupByDurationRequest prepareHydrationAggregateGroupByDurationRequest(TimeRangeFilter timeRange, Duration duration, HashSet<DataOrigin> dor) {
        Set<AggregateMetric<Volume>> metrics = new HashSet<>();
        metrics.add(HydrationRecord.VOLUME_TOTAL);

        return new AggregateGroupByDurationRequest(metrics, timeRange, duration, dor);
    }

    public static AggregateRequest prepareHydrationAggregateRequest(TimeRangeFilter timeRange, HashSet<DataOrigin> dor) {
        Set<AggregateMetric<Volume>> metrics = new HashSet<>();
        metrics.add(HydrationRecord.VOLUME_TOTAL);

        return new AggregateRequest(metrics, timeRange, dor);
    }

    public static void prepareHydrationStoreRecords(JSONObject storeObj, List<Record> data) throws JSONException {
        long st = storeObj.getLong("startDate");
        long et = storeObj.getLong("endDate");

        double liters = storeObj.getDouble("value");
        Volume vol = Volume.liters(liters);

        HydrationRecord record = new HydrationRecord(
                Instant.ofEpochMilli(st), null,
                Instant.ofEpochMilli(et), null,
                vol,
                Metadata.EMPTY
        );
        data.add(record);
    }
}
