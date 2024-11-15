package org.apache.cordova.health;

import androidx.health.connect.client.records.BloodPressureRecord;
//import androidx.health.connect.client.records.PositionType;
import androidx.health.connect.client.records.Record;
import androidx.health.connect.client.records.metadata.Metadata;
import androidx.health.connect.client.units.Pressure;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.List;

import kotlin.reflect.KClass;

public class BloodPressureFunctions {

    public static KClass<? extends Record> dataTypeToClass() {
        return kotlin.jvm.JvmClassMappingKt.getKotlinClass(BloodPressureRecord.class);
    }

    public static void populateFromQuery(Record datapoint, JSONObject obj) throws JSONException {
        BloodPressureRecord bloodPressure = (BloodPressureRecord) datapoint;
        obj.put("startDate", bloodPressure.getTime().toEpochMilli());
        obj.put("endDate", bloodPressure.getTime().toEpochMilli());

        double diasto = bloodPressure.getDiastolic().getMillimetersOfMercury();
        double systo = bloodPressure.getSystolic().getMillimetersOfMercury();

        JSONObject bpressjson = new JSONObject();
        bpressjson.put("diastolic", diasto);
        bpressjson.put("systolic", systo);

        int temp_body_pos = bloodPressure.getBodyPosition();
        String body_position = "unknown";
        if (temp_body_pos == BloodPressureRecord.BODY_POSITION_STANDING_UP) {
            body_position = "standing_up";
        } else if (temp_body_pos == BloodPressureRecord.BODY_POSITION_SITTING_DOWN) {
            body_position = "sitting_down";
        } else if (temp_body_pos == BloodPressureRecord.BODY_POSITION_LYING_DOWN) {
            body_position = "lying_down";
        } else if (temp_body_pos == BloodPressureRecord.BODY_POSITION_RECLINING) {
            body_position = "reclining";
        } else {
            body_position = "unknown";
        }

        bpressjson.put("body_position", body_position);

        String location = "unknown";
        int sourceInt = bloodPressure.getMeasurementLocation();
        if (sourceInt == BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_WRIST) {
            location = "left_wrist";
        } else if (sourceInt == BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_WRIST) {
            location = "right_wrist";
        } else if (sourceInt == BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM) {
            location = "left_upper_arm";
        } else if (sourceInt == BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_UPPER_ARM) {
            location = "right_upper_arm";
        } else {
            location = "unknown";
        }

        bpressjson.put("location", location);

        obj.put("value", bpressjson);
        obj.put("unit", "mmHg");
    }

    public static void prepareStoreRecords(JSONObject bloodpressobj, long st, List<Record> data) throws JSONException {
        double diastolic = bloodpressobj.getDouble("diastolic");
        double systolic = bloodpressobj.getDouble("systolic");

        Pressure pressure_dia = Pressure.millimetersOfMercury(diastolic);
        Pressure pressure_sys = Pressure.millimetersOfMercury(systolic);

        int body_position = BloodPressureRecord.BODY_POSITION_UNKNOWN;
        int location = BloodPressureRecord.MEASUREMENT_LOCATION_UNKNOWN;

        if (bloodpressobj.has("body_position")) {
            String bodyPosition = bloodpressobj.getString("body_position");
            if (bodyPosition.equalsIgnoreCase("standing_up")) {
                body_position = BloodPressureRecord.BODY_POSITION_STANDING_UP;
            } else if (bodyPosition.equalsIgnoreCase("sitting_down")) {
                body_position = BloodPressureRecord.BODY_POSITION_SITTING_DOWN;
            } else if (bodyPosition.equalsIgnoreCase("lying_down")) {
                body_position = BloodPressureRecord.BODY_POSITION_LYING_DOWN;
            } else if (bodyPosition.equalsIgnoreCase("reclining")) {
                body_position = BloodPressureRecord.BODY_POSITION_RECLINING;
            }
        }

        if (bloodpressobj.has("location")) {
            String strlocation = bloodpressobj.getString("location");
            if (strlocation.equalsIgnoreCase("left_wrist")) {
                location = BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_WRIST;
            } else if (strlocation.equalsIgnoreCase("right_wrist")) {
                location = BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_WRIST;
            } else if (strlocation.equalsIgnoreCase("left_upper_arm")) {
                location = BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM;
            } else if (strlocation.equalsIgnoreCase("right_upper_arm")) {
                location = BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_UPPER_ARM;
            }
        }

        BloodPressureRecord record = new BloodPressureRecord(
                Instant.ofEpochMilli(st), null,
                pressure_sys,
                pressure_dia,
                body_position,
                location,
                Metadata.EMPTY);

        data.add(record);
    }
}
