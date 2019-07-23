var argscheck = require('cordova/argscheck'),
	channel = require('cordova/channel'),
	utils = require('cordova/utils'),
	exec = require('cordova/exec'),
	cordova = require('cordova');

channel.createSticky('onZebraBarcodePluginReady');
// Tell cordova channel to wait on the CordovaInfoReady event
channel.waitForInitialization('onZebraBarcodePluginReady');

/**
 * This represents the mobile device, and provides properties for inspecting the model, version, UUID of the
 * phone, etc.
 * @constructor
 */
function ZebraBarcodePlugin () {
	this.available = false;
	var me = this;

	channel.onCordovaReady.subscribe(function () {
		exec(function () {
			me.available = true;
			channel.onZebraBarcodePluginReady.fire();
		}, function () {
			me.available = false;
		}, "ZebraBarcodePlugin", "init", []);
	});
}

ZebraBarcodePlugin.prototype.startHardKeyRead = function (successCallback, errorCallback) {
	argscheck.checkArgs('fF', 'ZebraBarcodePlugin.startHardRead', arguments);
	exec(successCallback, errorCallback, "ZebraBarcodePlugin", "startHardKeyRead", []);
};

ZebraBarcodePlugin.prototype.startSoftKeyRead = function (successCallback, errorCallback) {
	argscheck.checkArgs('fF', 'ZebraBarcodePlugin.startSoftRead', arguments);
	exec(successCallback, errorCallback, "ZebraBarcodePlugin", "startSoftKeyRead", []);
};

ZebraBarcodePlugin.prototype.stopReading = function () {
	exec(function () {}, function () {}, "ZebraBarcodePlugin", "stopReading", []);
};

module.exports = new ZebraBarcodePlugin();
