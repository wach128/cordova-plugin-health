# Cordova Health Plugin

![cordova-plugin-health](https://badgers.space/badge/npm/cordova-plugin-health/cyan) 
![MIT license](https://badgers.space/badge/license/mit/cyan)  



A plugin that abstracts fitness and health repositories like Apple HealthKit or Google Health Connect.

This work is based on [cordova healthkit plugin](https://github.com/Telerik-Verified-Plugins/HealthKit). This plugin is kept up to date and requires a recent version of cordova (12 and on) as well as recent iOS and Android SDKs.
For bugs and improvements use the [issues tracker](https://github.com/dariosalvi78/cordova-plugin-health/issues).
For general question or small issue, please use the [gitter channel](https://gitter.im/cordova-plugin-health/Lobby).

## Warning about Health Connect

This is a complete rewrite of the Android version of the plugin to support the new [HealthConnect API](https://developer.android.com/health-and-fitness/guides/health-connect). Google Fit APIs are deprecated and [should be made obsolete in 2024](https://developer.android.com/health-and-fitness/guides/health-connect/migrate/comparison-guide#turn-down-fit-android).

Google Fit is no longer supported by this plugin. If, for any masochistic reason, you want to use Google Fit, you need to use an older version of this pluign (2.X.X).


## Installation

In Cordova:

```
cordova plugin add cordova-plugin-health --variable HEALTH_READ_PERMISSION='App needs read access' --variable HEALTH_WRITE_PERMISSION='App needs write access'
```

`HEALTH_READ_PERMISSION` and `HEALTH_WRITE_PERMISSION` are shown when the app tries to grant access to data in HealthKit.

## iOS requirements

* Make sure your app id has the 'HealthKit' entitlement when this plugin is installed (see iOS dev center).
* Also, make sure your app and App Store description comply with the [Apple review guidelines](https://developer.apple.com/app-store/review/guidelines/#healthkit).
* There are [two keys](https://developer.apple.com/library/content/documentation/General/Reference/InfoPlistKeyReference/Articles/CocoaKeys.html#//apple_ref/doc/uid/TP40009251-SW48) to be added to the info.plist file: `NSHealthShareUsageDescription` and `NSHealthUpdateUsageDescription`. These are assigned with a default string by the plugin, but you may want to contextualise them for your app.

## Android requirements

* HealthConnect is made standard on (Google versions of) Android [from version 14 (API level 34)](https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started#step-1). On older versions of Android, the user has to install the Health Connect app from the Play Store.
* Health Connect SDK supports Android 8 (API level 26) or higher, while the Health Connect app is only compatible with Android 9 (API level 28) or higher see [this](https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started#step-2).
* Health Connect SDK requires targeting Android API level 34. The current cordova-android package targets version 33 and has 24 as miníumum version. Temporary workaround: 
  * download a recent version of gradle (8.4 or later)
  * if you use Android Studio, download at least version Hedgehog
  * in your config.xml file add these two to upgrade the Gradle version
  ```xml
    <preference name="GradleVersion" value="8.4" />
    <preference name="AndroidGradlePluginVersion" value="8.1.1" />
    <preference name="android-minSdkVersion" value="26" />
    <preference name="android-targetSdkVersion" value="34" />
  ```
  alternatively, you can change these variables in the file `cdv-gradle-config.json` in the project's Android platform directory `<project-root>/platforms/android`
  * add the following lines into `<project-root>/platforms/android/app/build.gradle` to fix some nasty dependencies issues
```
dependencies {
    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22") {
            because("kotlin-stdlib-jdk7 is now a part of kotlin-stdlib")
        }
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22") {
            because("kotlin-stdlib-jdk8 is now a part of kotlin-stdlib")
        }
    }
}
```
* Be aware that Health Connect requires the user to have screen lock enabled with a PIN, pattern, or password.
* When publishing the app, you need to comply to [these requests from Google](https://developer.android.com/health-and-fitness/guides/health-connect/publish/request-access).
* You need to include a Privacy Policy in HTML format. This can be done either by adding a file with the name privacypolicy.html inside your root www folder, or by using the `setPrivacyPolicyURL()` function (see below).
* This plugin uses AndroidX. You will need to [activate AndroidX](https://cordova.apache.org/announcements/2020/06/29/cordova-android-9.0.0.html) in the Android platform and make sure all other plugins you use are AndroidX compatible.


## Phonegap

Phonegap Build `config.xml`:

```
<!-- Health plugin -->
<plugin name="cordova-plugin-health" source="npm">
  <variable name="HEALTH_READ_PERMISSION" value="App needs read access"/>
  <variable name="HEALTH_WRITE_PERMISSION" value="App needs write access"/>
</plugin>

<!-- Only if iOS -->

<!-- Read access -->
<config-file platform="ios" parent="NSHealthShareUsageDescription">
  <string>App needs read access</string>
</config-file>
<!-- Write access -->
<config-file platform="ios" parent="NSHealthUpdateUsageDescription">
  <string>App needs write access</string>
</config-file>
```

If, for some reason, the Info.plist loses the HEALTH_READ_PERMISSION and HEALTH_WRITE_PERMISSION, you probably need to add the following to your project's package.json:

```
{
  "cordova": {
    "plugins": {
     "cordova-plugin-health": {
        "HEALTH_READ_PERMISSION": "App needs read access",
        "HEALTH_WRITE_PERMISSION": "App needs write access"
      },
    },
  }
}
```

This is known to happen when using the Ionic Package cloud service.

## Supported data types

As HealthKit does not allow adding custom data types, only a subset of data types supported by HealthKit has been chosen.

| Data type       | Unit  |    HealthKit equivalent                       |  Health Connect equivalent               |
|-----------------|-------|-----------------------------------------------|------------------------------------------|
| steps           | count | HKQuantityTypeIdentifierStepCount             |                     |
| stairs          | count | HKQuantityTypeIdentifierFlightsClimbed        |                     |
| distance        | m     | HKQuantityTypeIdentifierDistanceWalkingRunning + HKQuantityTypeIdentifierDistanceCycling | TYPE_DISTANCE_DELTA |
| appleExerciseTime | min | HKQuantityTypeIdentifierAppleExerciseTime     | NA                                       |
| calories        | kcal  | HKQuantityTypeIdentifierActiveEnergyBurned + HKQuantityTypeIdentifierBasalEnergyBurned | TYPE_CALORIES_EXPENDED |
| calories.active | kcal  | HKQuantityTypeIdentifierActiveEnergyBurned    | TYPE_CALORIES_EXPENDED - (TYPE_BASAL_METABOLIC_RATE * time window) |
| calories.basal  | kcal  | HKQuantityTypeIdentifierBasalEnergyBurned     | TYPE_BASAL_METABOLIC_RATE * time window  |
| activity        | activityType | HKWorkoutTypeIdentifier                | TYPE_ACTIVITY_SEGMENT |
| sleep           | sleepType | HKCategoryTypeIdentifierSleepAnalysis | TYPE_ACTIVITY_SEGMENT (and/or sleep sessions) |
| height          | m     | HKQuantityTypeIdentifierHeight                | TYPE_HEIGHT                              |
| weight          | kg    | HKQuantityTypeIdentifierBodyMass              | TYPE_WEIGHT                              |
| bmi             | count | HKQuantityTypeIdentifierBodyMassIndex         | NA                                       |
| heart_rate      | count/min | HKQuantityTypeIdentifierHeartRate         | TYPE_HEART_RATE_BPM                      |
| heart_rate.resting | count/min | HKQuantityTypeIdentifierRestingHearRate | TBD                      |
| heart_rate.variability      | ms | HKQuantityTypeIdentifierHeartRateVariabilitySDNN         | NA                   |
| resp_rate       | count/min | HKQuantityTypeIdentifierRespiratoryRate   | TBD                      |
| oxygen_saturation | %       | HKQuantityTypeIdentifierOxygenSaturation  | TYPE_OXYGEN_SATURATION                   |
| vo2max          | ml/(kg * min) | HKQuantityTypeIdentifierVO2Max   | TBD                      |
| temperature     | Celsius | HKQuantityTypeIdentifierBodyTemperature       | TBD                      |
| fat_percentage  | %     | HKQuantityTypeIdentifierBodyFatPercentage     | TYPE_BODY_FAT_PERCENTAGE                 |
| waist_circumference | m     | HKQuantityTypeIdentifierWaistCircumference     | NA                    |
| blood_glucose   | mmol/L | HKQuantityTypeIdentifierBloodGlucose         | TYPE_BLOOD_GLUCOSE                       |
| insulin         | IU    | HKQuantityTypeIdentifierInsulinDelivery       | NA                                       |
| blood_pressure  | mmHg  | HKCorrelationTypeIdentifierBloodPressure      | TYPE_BLOOD_PRESSURE                      |
| blood_pressure_systolic | mmHg     | HKQuantityTypeIdentifierBloodPressureSystolic     | NA                    |
| blood_pressure_diastolic | mmHg     | HKQuantityTypeIdentifierBloodPressureDiastolic     | NA                    |
| gender          |       | HKCharacteristicTypeIdentifierBiologicalSex   | NA        |
| date_of_birth   |       | HKCharacteristicTypeIdentifierDateOfBirth     | NA        |
| mindfulness     | sec   | HKCategoryTypeIdentifierMindfulSession        | NA                                       |
| nutrition       |       | HKCorrelationTypeIdentifierFood               | TYPE_NUTRITION                           |
| UVexposure      | count | HKQuantityTypeIdentifierUVExposure            | NA        |
| nutrition.calories | kcal | HKQuantityTypeIdentifierDietaryEnergyConsumed | TYPE_NUTRITION, NUTRIENT_CALORIES      |
| nutrition.fat.total | g | HKQuantityTypeIdentifierDietaryFatTotal       | TYPE_NUTRITION, NUTRIENT_TOTAL_FAT       |
| nutrition.fat.saturated | g | HKQuantityTypeIdentifierDietaryFatSaturated | TYPE_NUTRITION, NUTRIENT_SATURATED_FAT |
| nutrition.fat.unsaturated | g | NA                                      | TYPE_NUTRITION, NUTRIENT_UNSATURATED_FAT |
| nutrition.fat.polyunsaturated | g | HKQuantityTypeIdentifierDietaryFatPolyunsaturated | TYPE_NUTRITION, NUTRIENT_POLYUNSATURATED_FAT |
| nutrition.fat.monounsaturated | g | HKQuantityTypeIdentifierDietaryFatMonounsaturated | TYPE_NUTRITION, NUTRIENT_MONOUNSATURATED_FAT |
| nutrition.fat.trans | g | NA                                            | TYPE_NUTRITION, NUTRIENT_TRANS_FAT (g)   |
| nutrition.cholesterol | mg | HKQuantityTypeIdentifierDietaryCholesterol | TYPE_NUTRITION, NUTRIENT_CHOLESTEROL     |
| nutrition.sodium | mg   | HKQuantityTypeIdentifierDietarySodium         | TYPE_NUTRITION, NUTRIENT_SODIUM          |
| nutrition.potassium | mg | HKQuantityTypeIdentifierDietaryPotassium     | TYPE_NUTRITION, NUTRIENT_POTASSIUM       |
| nutrition.carbs.total | g | HKQuantityTypeIdentifierDietaryCarbohydrates | TYPE_NUTRITION, NUTRIENT_TOTAL_CARBS    |
| nutrition.dietary_fiber | g | HKQuantityTypeIdentifierDietaryFiber      | TYPE_NUTRITION, NUTRIENT_DIETARY_FIBER   |
| nutrition.sugar | g     | HKQuantityTypeIdentifierDietarySugar          | TYPE_NUTRITION, NUTRIENT_SUGAR           |
| nutrition.protein | g   | HKQuantityTypeIdentifierDietaryProtein        | TYPE_NUTRITION, NUTRIENT_PROTEIN         |
| nutrition.vitamin_a | mcg (HK), IU (GF) | HKQuantityTypeIdentifierDietaryVitaminA | TYPE_NUTRITION, NUTRIENT_VITAMIN_A |
| nutrition.vitamin_c | mg | HKQuantityTypeIdentifierDietaryVitaminC | TYPE_NUTRITION, NUTRIENT_VITAMIN_C            |
| nutrition.calcium | mg  | HKQuantityTypeIdentifierDietaryCalcium        | TYPE_NUTRITION, NUTRIENT_CALCIUM         |
| nutrition.iron  | mg    | HKQuantityTypeIdentifierDietaryIron           | TYPE_NUTRITION, NUTRIENT_IRON            |
| nutrition.water | ml    | HKQuantityTypeIdentifierDietaryWater          | TYPE_HYDRATION                           |
| nutrition.caffeine | g  | HKQuantityTypeIdentifierDietaryCaffeine       | NA                                       |

**Note**: units of measurement are fixed!

Returned objects contain a set of fixed fields:

- startDate: {type: Date} a date indicating when the data point starts
- endDate: {type: Date} a date indicating when the data point ends
- unit: {type: String} the unit of measurement
- value: the actual value
- sourceBundleId: {type: String} the identifier of the app that produced the data. It can be the "stream identifier" when the app is Google Fit
- sourceName: {type: String} (only on iOS) the name of the app that produced the data (as it appears to the user)
- id: {type: String} (only on iOS) the unique identifier of that measurement

Example values:

| Data type      | Value                             |
|----------------|-----------------------------------|
| steps          | 34                                |
| distance       | 101.2                             |
| appleExerciseTime | 24 <br/>**Notes**: only available on iOS|
| calories       | 245.3                             |
| activity       | "walking"<br />**Notes**: recognized activities and their mappings in Google Fit / HealthKit can be found [here](activities_map.md) <br /> the query also returns calories (kcal) and distance (m)<br />**Warning** If you want to fetch activities you also have to request permission for 'calories' and 'distance' (Android). |
| sleep       | 'sleep.light' <br />**Notes**: recognized sleep stages and their mappings in Google Fit / HealthKit can be found [here](sleep_map.md) |
| height         | 1.85                              |
| weight         | 83.3                              |
| heart_rate     | 66                                |
| heart_rate.resting | 63                            |
| heart_rate.variability | 100                       |
| resp_rate      | 12                                |
| vo2max         | 34                                |
| temperature    | 36.2                              |
| fat_percentage | 0.312                             |
| waist_circumference | 0.65                         |
| blood_glucose  | { glucose: 5.5, meal: 'breakfast', sleep: 'fully_awake', source: 'capillary_blood' }<br />**Notes**: <br />to convert to mg/dL, multiply by `18.01559` ([The molar mass of glucose is 180.1559](http://www.convertunits.com/molarmass/Glucose))<br />`meal` can be: 'before_meal' (iOS only), 'after_meal' (iOS only), 'fasting', 'breakfast', 'dinner', 'lunch', 'snack', 'unknown', 'before_breakfast', 'before_dinner', 'before_lunch', 'before_snack', 'after_breakfast', 'after_dinner', 'after_lunch', 'after_snack'<br />`sleep` can be: 'fully_awake', 'before_sleep', 'on_waking', 'during_sleep'<br />`source` can be: 'capillary_blood' ,'interstitial_fluid', 'plasma', 'serum', 'tears', whole_blood' |
| insulin        | { insulin: 2.3, reason: 'bolus' }<br />**Notes**: only available on iOS<br />`reason` can be 'bolus' or 'basal' |
| blood_pressure | { systolic: 110, diastolic: 70 }  |
| blood_pressure_systolic | 110  |
| blood_pressure_diastolic | 70  |
| gender         | "male" <br/>**Notes**: only available on iOS |
| date_of_birth  | { day: 3, month: 12, year: 1978 } <br/>**Notes**: only available on iOS |
| mindfulness     | 1800 <br/>**Notes**: only available on iOS |
| nutrition      | { item: "cheese", meal_type: "lunch", brand_name: "McDonald's", nutrients: { nutrition.fat.saturated: 11.5, nutrition.calories: 233.1 } } <br/>**Note**: the `brand_name` property is only available on iOS |
| nutrition.X    | 12.4                              |

## Methods

### isAvailable()

Tells if either HealthKit of Health Connect are available.

```
navigator.health.isAvailable(successCallback, errorCallback)
```

- successCallback: {type: function(available)}, if available a true is passed as argument, false otherwise
- errorCallback: {type: function(err)}, called if something went wrong, err contains a textual description of the problem



## External resources

* The official Apple documentation for HealthKit [can be found here](https://developer.apple.com/library/ios/documentation/HealthKit/Reference/HealthKit_Framework/index.html#//apple_ref/doc/uid/TP40014707).
* For functions that require the `unit` attribute, you can find the comprehensive list of possible units from the [Apple Developers documentation](https://developer.apple.com/library/ios/documentation/HealthKit/Reference/HKUnit_Class/index.html#//apple_ref/doc/uid/TP40014727-CH1-SW2).
* [HealthKit constants](https://developer.apple.com/library/ios/documentation/HealthKit/Reference/HealthKit_Constants/index.html), used throughout the code.
* Health Connect [supported data types](https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/package-summary).


## Contributions

Any help is more than welcome!

I don't know Objective C and I am not interested in learning it now, so I would particularly appreciate someone who could give me a hand with the iOS part.
Also, I would love to know from you if the plugin is currently used in any app actually available online.
Just send me an email to my_username at gmail.com.

Thanks!
