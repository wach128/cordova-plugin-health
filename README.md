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
* Health Connect SDK requires targeting Android API level 34. The current cordova-android package (12.0.1) targets version 33 and uses a version of Gradle that is incompatible with API level 34, so this plugin implements a temporary workaround.
The workaround consists in fixing gradle version (to 8.4), gradle plugin (to 8.1.1), target SDK version (to 34) and minimum SDK version (to 26), see plugin.xml. Additionally, there are issues with some kotlin depenendencies which are fixed in `src/android/build-extras.gradle`. All these hacks will hopefully be removed with future versions of the cordova-android platform.
* Download a recent version of gradle (8.4 or later).
* If you use Android Studio, download at least version Hedgehog.
* Be aware that Health Connect requires the user to have screen lock enabled with a PIN, pattern, or password.
* When publishing the app, you need to comply to [these requests from Google](https://developer.android.com/health-and-fitness/guides/health-connect/publish/request-access).
* You need to include a Privacy Policy in HTML format. This can be done either by adding a file with the name privacypolicy.html inside your root www folder, or by using the `setPrivacyPolicyURL()` function (see below).
* This plugin uses AndroidX. You may need to [activate AndroidX](https://cordova.apache.org/announcements/2020/06/29/cordova-android-9.0.0.html) in the Android platform and make sure all other plugins you use are AndroidX compatible.

## Mandatory Privacy Policy on Android

A Privacy Policy must be present on Android in order for the app to be approved for distribution. The plugin includes a simple webview, with no JS activated, to show the Privacy Policy when requested. The Privacy Policy must be formatted as an HTML page (no JS) and placed as a file with name: `privacypolicy.html` under the `www` folder of the project (in other words, the webview loads the following URL: `file:///android_asset/www/privacypolicy.html`). It is possible to change that URL using the function setPrivacyPolicyURL(). To test the Privacy Policy view, you can call launchPrivacyPolicy().


## Supported data types

As HealthKit does not allow adding custom data types, only a subset of data types supported by HealthKit has been chosen.

| Data type       | Unit  |    HealthKit equivalent                       |  Health Connect equivalent               |
|-----------------|-------|-----------------------------------------------|------------------------------------------|
| steps           | count | HKQuantityTypeIdentifierStepCount             |                     |


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


## Methods

### isAvailable()

Tells if either HealthKit of Health Connect are available.

```
cordova.plugins.health.isAvailable(successCallback, errorCallback)
```

- successCallback: if available a true is passed as argument, false otherwise
- errorCallback: called if something went wrong, err contains a textual description of the problem

### setPrivacyPolicyURL() Android only

Sets an alternative privacy policy URL to load. By default it loads `file:///android_asset/www/privacypolicy.html`.

```
cordova.plugins.health.setPrivacyPolicyURL(url, successCallback, errorCallback)
```

- url: URL of the privacy policy
- successCallback: called if screen has been launched
- errorCallback: called if something went wrong


### launchPrivacyPolicy() Android only

Launches the Privacy Policy screen needed by Health Connect. Use it for testing how it appears.

```
cordova.plugins.health.launchPrivacyPolicy(successCallback, errorCallback)
```

- successCallback: screen has been launched
- errorCallback: called if something went wrong

### requestAuthorization()

Requests read and/pr write access to a set of data types.
It is recommendable to always explain why the app needs access to the data before asking the user to authorize it.

**Important:** this method must be called before using the query, store and delete methods, even if the authorization has already been given at some point in the past. Failure to do so may cause your app to crash, or in the case of Android, Health Connect may not be initialized.

```
cordova.plugins.requestAuthorization(datatypes, successCallback, errorCallback)
```

- authObj: an object containing data types you want to be granted access to. Example:
```javascript
{
  read : ['steps'],            // Read permission
  write : ['steps', 'weight']  // Write permission
}
```
- successCallback: called if permission process completed, called independently of if the user has granted permissions or not
- errorCallback: called if something went wrong, the argument contains a textual description of the problem

#### Android quirks

- It will try to get authorization from the Google fitness APIs. It is necessary that the app's package name and the signing key are registered in the Google API console (see [here](https://developers.google.com/fit/android/get-api-key)).
- Be aware that if the activity is destroyed (e.g. after a rotation) or is put in background, the connection to Google Fit may be lost without any callback. Going through the authorization will ensure that the app is connected again.
- Be aware that if you want to fetch activities you also have to request permission for 'calories' and 'distance'.
- In Android 6 and over, this function will also ask for some dynamic permissions if needed (e.g. in the case of "distance" or "activity", it will need access to ACCESS_FINE_LOCATION).

#### iOS quirks

- HealthKit does never reveal if the user has actually granted permission.
- Once the user has allowed (or not allowed) the app, this function will not prompt the user again, but will call the callback immediately. See [this](https://developer.apple.com/documentation/healthkit/hkhealthstore/1614152-requestauthorization) for further explanation.


### isAuthorized() 
Check if the app has authorization to read/write a set of datatypes.

```
cordova.plugins.health.isAuthorized(authObj, successCallback, errorCallback)
```

- authObj: an object containing data types you want to be granted access to. Example:
```javascript
{
  read : ['steps'],            // Read permission
  write : ['steps', 'weight']  // Write permission
}
```
- successCallback: if the argument is true, the app is authorized
- errorCallback: called if something went wrong, the argument contains a textual description of the problem

#### iOS quirks

- This method will only check authorization status for writeable data. Read-only data will always be considered as not authorized.
This is [an intended behaviour of HealthKit](https://developer.apple.com/reference/healthkit/hkhealthstore/1614154-authorizationstatus).


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
