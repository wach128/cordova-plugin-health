package org.apache.cordova.health;

import androidx.health.connect.client.aggregate.AggregateMetric;
import androidx.health.connect.client.aggregate.AggregationResult;
import androidx.health.connect.client.records.Record;
import androidx.health.connect.client.records.StepsRecord;
import androidx.health.connect.client.records.metadata.DataOrigin;
import androidx.health.connect.client.records.metadata.Metadata;
import androidx.health.connect.client.request.AggregateGroupByDurationRequest;
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest;
import androidx.health.connect.client.request.AggregateRequest;
import androidx.health.connect.client.time.TimeRangeFilter;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import kotlin.reflect.KClass;

public class StepsFunctions {
    public static KClass<? extends Record> dataTypeToClass() {
        return kotlin.jvm.JvmClassMappingKt.getKotlinClass(StepsRecord.class);
    }

    public static void populateFromQuery(Record datapoint, JSONObject obj) throws JSONException {
        StepsRecord stepsDP = (StepsRecord) datapoint;
        obj.put("startDate", stepsDP.getStartTime().toEpochMilli());
        obj.put("endDate", stepsDP.getEndTime().toEpochMilli());

        long steps = stepsDP.getCount();
        obj.put("value", steps);
        obj.put("unit", "count");
    }

    public static void populateFromAggregatedQuery(AggregationResult response, JSONObject retObj) throws JSONException {
        if (response.get(StepsRecord.COUNT_TOTAL) != null) {
            long val = response.get(StepsRecord.COUNT_TOTAL);
            retObj.put("value", val);
            retObj.put("unit", "count");
        } else {
            retObj.put("value", 0);
            retObj.put("unit", "count");
        }
    }

    public static AggregateGroupByPeriodRequest prepareAggregateGroupByPeriodRequest (TimeRangeFilter timeRange, Period period, HashSet<DataOrigin> dor) {
        Set<AggregateMetric<Long>> metrics = new HashSet<>();
        metrics.add(StepsRecord.COUNT_TOTAL);
        return new AggregateGroupByPeriodRequest(metrics, timeRange, period, dor);
    }

    public static AggregateGroupByDurationRequest prepareAggregateGroupByDurationRequest (TimeRangeFilter timeRange, Duration duration, HashSet<DataOrigin> dor) {
        Set<AggregateMetric<Long>> metrics = new HashSet<>();
        metrics.add(StepsRecord.COUNT_TOTAL);
        return new AggregateGroupByDurationRequest(metrics, timeRange, duration, dor);
    }

    public static AggregateRequest prepareAggregateRequest(TimeRangeFilter timeRange, HashSet<DataOrigin> dor) {
        Set<AggregateMetric<Long>> metrics = new HashSet<>();
        metrics.add(StepsRecord.COUNT_TOTAL);
        return new AggregateRequest(metrics, timeRange, dor);
    }


    public static void prepareStoreRecords(JSONObject storeObj, long st, long et, List<Record> data) throws JSONException {
        long steps = storeObj.getLong("value");
        // TODO: we could add meta data when storing, including entry method, client ID and device
        StepsRecord record = new StepsRecord(
                Instant.ofEpochMilli(st), null,
                Instant.ofEpochMilli(et), null,
                steps,
                Metadata.EMPTY
        );
        data.add(record);
    }
}
