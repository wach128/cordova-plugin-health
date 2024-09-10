package org.apache.cordova.health;

import androidx.health.connect.client.aggregate.AggregateMetric;
import androidx.health.connect.client.aggregate.AggregationResult;
import androidx.health.connect.client.records.Record;
import androidx.health.connect.client.records.metadata.DataOrigin;
import androidx.health.connect.client.records.metadata.Metadata;
import androidx.health.connect.client.request.AggregateGroupByDurationRequest;
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest;
import androidx.health.connect.client.request.AggregateRequest;
import androidx.health.connect.client.time.TimeRangeFilter;
import androidx.health.connect.client.records.MealType;
import androidx.health.connect.client.units.Energy;
import androidx.health.connect.client.units.Mass;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import kotlin.reflect.KClass;

public class NutritionFunctions {
    public static KClass<? extends Record> dataTypeToClass() {
        return kotlin.jvm.JvmClassMappingKt.getKotlinClass(NutritionRecord.class);
    }

    public static void populateFromQuery(Record datapoint, JSONObject obj, JSONArray resultset, boolean keepSession) throws JSONException {
        
        JSONObject nutritionStats = new JSONObject();

        NutritionRecord nutritionR = (NutritionRecord) datapoint;
        nutritionStats.put("startDate", nutritionR.getTime().toEpochMilli());
        nutritionStats.put("endDate", nutritionR.getTime().toEpochMilli());

        String name = nutritionR.getName();
        nutritionStats.put("name", name);

        Double kcal = nutritionR.getEnergy().inKilocalories();
        nutritionStats.put("kcal", kcal);

        int mealType = nutritionR.getMealType();
        if(mealType === MealType.MEAL_TYPE_BREAKFAST) {
            nutritionStats.put("mealType", "breakfast");
        } else if(mealType === MealType.MEAL_TYPE_LUNCH) {
            nutritionStats.put("mealType", "lunch");
        } else if(mealType === MealType.MEAL_TYPE_DINNER) {
            nutritionStats.put("mealType", "dinner");
        } else if(mealType === MealType.MEAL_TYPE_SNACK) {
            nutritionStats.put("mealType", "snack");
        } else {
            nutritionStats.put("mealType", "unknown");
        }

        Double protein = nutritionR.getProtein().inGrams();
        nutritionStats.put("protein", protein);

        Double fat = nutritionR.totalFat().inGrams();
        nutritionStats.put("fat", fat);

        Double carbs = nutritionR.totalCarbohydrate().inGrams();
        nutritionStats.put("carbs", carbs);

        nutritionStats.put("value", nutritionStats);
        nutritionStats.put("unit", "meal");
    }

    public static void populateFromAggregatedQuery(AggregationResult response, JSONObject retObj) throws JSONException {
        if (response.get(NutritionRecord.ENERGY_TOTAL) != null) {
            JSONObject nutritionStats = new JSONObject();

            double totalEnergy = response.get(NutritionRecord.ENERGY_TOTAL).inKilocalories();
            nutritionStats.put("kcal", totalEnergy);

            double totalProtein = response.get(NutritionRecord.PROTEIN_TOTAL).inGrams();
            nutritionStats.put("protein", totalProtein);

            double totalFat = response.get(NutritionRecord.TOTAL_FAT_TOTAL).inGrams();
            nutritionStats.put("fat", totalFat);

            double totalCarbs = response.get(NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL).inGrams();
            nutritionStats.put("carbs", totalCarbs);

            retObj.put("value", nutritionStats);
            retObj.put("unit", "meal");
        } else {
            retObj.put("value", {});
            retObj.put("unit", "meal");
        }
    }

    public static AggregateGroupByPeriodRequest prepareAggregateGroupByPeriodRequest (TimeRangeFilter timeRange, Period period, HashSet<DataOrigin> dor) {
        Set<AggregateMetric<Duration>> metrics = new HashSet<>();
        metrics.add(NutritionRecord.ENERGY_TOTAL);
        metrics.add(NutritionRecord.PROTEIN_TOTAL);
        metrics.add(NutritionRecord.TOTAL_FAT_TOTAL);
        metrics.add(NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL);

        return new AggregateGroupByPeriodRequest(metrics, timeRange, period, dor);
    }

    public static AggregateGroupByDurationRequest prepareAggregateGroupByDurationRequest (TimeRangeFilter timeRange, Duration duration, HashSet<DataOrigin> dor) {
        Set<AggregateMetric<Duration>> metrics = new HashSet<>();
        metrics.add(NutritionRecord.ENERGY_TOTAL);
        metrics.add(NutritionRecord.PROTEIN_TOTAL);
        metrics.add(NutritionRecord.TOTAL_FAT_TOTAL);
        metrics.add(NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL);
        return new AggregateGroupByDurationRequest(metrics, timeRange, duration, dor);
    }

    public static AggregateRequest prepareAggregateRequest(TimeRangeFilter timeRange, HashSet<DataOrigin> dor) {
        Set<AggregateMetric<Duration>> metrics = new HashSet<>();
        metrics.add(NutritionRecord.ENERGY_TOTAL);
        metrics.add(NutritionRecord.PROTEIN_TOTAL);
        metrics.add(NutritionRecord.TOTAL_FAT_TOTAL);
        metrics.add(NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL);
        return new AggregateRequest(metrics, timeRange, dor);
    }

    public static void prepareStoreRecords(JSONObject storeObj, List<Record> data) throws JSONException {
        double nutritionObj = storeObj.getDouble("value");

        int mealType = MealType.MEAL_TYPE_UNKNOWN;
      
        if (nutritionObj.has("meal")) {
            String meal = nutritionObj.getString("mealType");
  
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

        double kcal = nutritionObj.getDouble("kcal");
        double protein = nutritionObj.getDouble("protein");
        double fat = nutritionObj.getDouble("fat");
        double carbs = nutritionObj.getDouble("carbs");
        String name = nutritionObj.getString("name");

        NutritionRecord record = new NutritionRecord(
                Instant.ofEpochMilli(st),
                null,
                Instant.ofEpochMilli(st),
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
                null,
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

}
