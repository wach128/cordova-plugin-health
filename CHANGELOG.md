Log of changes
==============

## v 1.0.4

* updated README with better documentation
* minor bug fixes


## v 1.0.5

* updated README
* added `filtered` flag to steps also for iOS (but only in aggregatedQuery)
* added `appleExerciseTime` in datatypes (store and query) only on iOS
* added support for blood pressure store and query on both Android and iOS


### v1.1.0

* support for latest Google Fit auth mechanism for health data
* added support for 'limit' in query() on Android too
* Android and iOS return the same object when queried for activity

### v1.1.1

* do not ask for read permission when storing workouts on iOS
* added heart rate variability (iOS only)
* added stairs (iOS only)

### v1.1.2

* minor bugfix

### v1.1.3

* some bugfixes
* Google services version locked to specific version but changeable

### v1.1.4

* added mindfulness, heart_rate.resting, resp_rate, vo2max, temperature, iOS only for now

### v1.1.5

* allows longer durations of buckets in Android
* remove Java annotations

### v2.0.0

* BREAKS API: the date of birth and gender have been removed from GoogleFit custom datatypes, you need to store these using your app's persistent mechanism
* BREAKS API: sourceName has been removed from GoogleFit
* bumped Google Fit API version to 19.0.0
* fixed bug with water consumption unit in Android

### v2.0.1

* added read scope to write scope in Android
* added packages queries for Android
* updated README

### v2.0.2

* increased Google APIs to Play: 19.0.0 and Fit: 20.0.0


### v2.0.3

* added separate sleep data from Google Fit
* updated activity types in iOS (removed depecrated ones and added new ones)
* fixed energy in activity when > 1000 Kcal

### v2.0.4

* added oxygen saturation for Android and iOS
* added weight circumference for iOS
* added blood pressure for iOS
* added BMI for iOS
* enable HealthKit automatically in iOS


### v2.0.5

* bugfixes

### v2.1.0

* bumped version of Google Play APIs
* added separate sleep datatype for Android and iOS (**API change**)
* added sleep sessions in simple query for "sleep" data type for Android
* distance and calories are optional when querying activity (**API change**)
* bugfixing



### v3.0.0

* [Breaking change] plugin is now accessed from `cordova.plugins.healt`
* [Breaking change] authorization is asked using the `read` and `write` properties only
* With Health Connect several activities have disappeared.
* Added following activities:
 * bootcamp
 * stretching
 * dancing.cardio
 * exercise_class
 * strength_training.functional
 * guided_breathing

* Removed following activities:
 * aerobics
 * biathlon
 * biking.hand
 * biking.mountain
 * biking.road
 * biking.spinning
 * biking.utility
 * circuit_training
 * diving
 * elevator
 * ergometer
 * escalator
 * functional_strength
 * gardening
 * housework
 * in_vehicle
 * interval_training
 * kayaking
 * kettlebell_training
 * kick_scooter
 * kitesurfing
 * martial_arts.mixed
 * on_foot
 * p90x
 * polo
 * running.jogging
 * running.sand
 * skateboarding
 * skating.cross
 * skating.indoor
 * skating.inline
 * skiing.back_country
 * skiing.kite
 * skiing.roller
 * sledding
 * sleep (all types)
 * snowmobile
 * standup_paddleboarding
 * still
 * stairs
 * team_sports
 * tilting
 * unknown
 * volleyball.beach
 * volleyball.indoor
 * wakeboarding
 * walking.fitness
 * walking.nordic
 * walking.treadmill
 * walking.stroller
 * treadmill
 * windsurfing
 * zumba

