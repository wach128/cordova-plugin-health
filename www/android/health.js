var exec = require("cordova/exec");


module.exports = {

  name: "Tester",

  isAvailable (onSuccess, onError) {
    exec(onSuccess, onError, "health", "isAvailable", []);
  },

  launchPrivacyPolicy (onSuccess, onError) {
    exec(onSuccess, onError, "health", "launchPrivacyPolicy", [])
  },

  setPrivacyPolicyURL (url, onSuccess, onError) {
    exec(onSuccess, onError, "Tester", "setPrivacyPolicyURL", [url])
  },

  isAuthorized (authObj, onSuccess, onError) {
    exec(onSuccess, onError, "health", "isAuthorized", [authObj])
  },

  requestAuthorization (authObj, onSuccess, onError) {
    exec(onSuccess, onError, "health", "requestAuthorization", [authObj])
  },

  query (opts, onSuccess, onError) {
    if (opts.startDate && (typeof opts.startDate == 'object'))
      opts.startDate = opts.startDate.getTime()
    if (opts.endDate && (typeof opts.endDate == 'object'))
      opts.endDate = opts.endDate.getTime();
    exec(function (data) {
      for (var i = 0; i < data.length; i++) {
        // convert timestamps to date
        if (data[i].startDate) data[i].startDate = new Date(data[i].startDate)
        if (data[i].endDate) data[i].endDate = new Date(data[i].endDate)
      }
      onSuccess(data);
    }, onError, "health", "query", [opts])
  },

  queryAggregated (opts, onSuccess, onError) {
    if (typeof opts.startDate == 'object') opts.startDate = opts.startDate.getTime()
    if (typeof opts.endDate == 'object') opts.endDate = opts.endDate.getTime()
    exec(function (data) {
      //reconvert the dates back to Date objects
      if (Object.prototype.toString.call(data) === '[object Array]') {
        //it's an array, iterate through each item
        for (var i = 0; i < data.length; i++) {
          data[i].startDate = new Date(data[i].startDate)
          data[i].endDate = new Date(data[i].endDate)
        }
      } else { // not an array
        data.startDate = new Date(data.startDate)
        data.endDate = new Date(data.endDate)
      }

      onSuccess(data)
    }, onError, 'health', 'queryAggregated', [opts])
  }
}
