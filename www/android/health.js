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
    exec(onSuccess, onError, "health", "isAuthorized", [authObj]);
  },

  requestAuthorization (authObj, onSuccess, onError) {
    exec(onSuccess, onError, "health", "requestAuthorization", [authObj]);
  }
}
