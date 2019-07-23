package com.jkt.zebra.barcode.plugin;

import android.util.Log;

import com.symbol.emdk.EMDKManager;
import com.symbol.emdk.EMDKResults;
import com.symbol.emdk.barcode.BarcodeManager;
import com.symbol.emdk.barcode.ScanDataCollection;

import com.symbol.emdk.barcode.Scanner;
import com.symbol.emdk.barcode.ScannerException;
import com.symbol.emdk.barcode.ScannerInfo;
import com.symbol.emdk.barcode.ScannerResults;
import com.symbol.emdk.barcode.StatusData;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import java.io.Serializable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class echoes a string called from JavaScript.
 */
public class ZebraBarcodePlugin extends CordovaPlugin implements Serializable, EMDKManager.EMDKListener, Scanner.StatusListener, Scanner.DataListener {

    private static final String LOG_TAG = "ZebraBarcodePlugin";

    private EMDKManager emdkManager = null;             ///<  If the EMDK is available for scanning, this property will be non-null
    private Scanner scanner = null;                         ///<  The scanner currently in use
    private CallbackContext initialisationCallbackContext = null;   ///<  The Cordova callback for our first plugin initialisation
    private CallbackContext scanCallbackContext = null;     ///<  The Cordova callback context for each scan
    private static String SOFT_KEY = "soft_key";
    private static String HARD_KEY = "hard_key";
    private static String START_SOFT_KEY = "startSoftKeyRead";
    private static String START_HARD_KEY = "startHardKeyRead";
    private static String STOP_READING = "stopReading";

    public ZebraBarcodePlugin() {
    }


    //------------------------------------------------------------------------------------------------------------------
    // CORDOVA
    //------------------------------------------------------------------------------------------------------------------

    public void onDestroy() {
        Log.i(LOG_TAG, "Cordova onDestroy");

        if (emdkManager != null) {
            Log.w(LOG_TAG, "Destroy scanner");
            emdkManager.release(EMDKManager.FEATURE_TYPE.BARCODE);
        }
    }

    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        Log.d(LOG_TAG, "JS-Action: " + action);

        if (action.equals("init")) {
            if (scanner != null && scanner.isEnabled()) {
                callbackContext.success();
            } else {
                final ZebraBarcodePlugin me = this;
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        initialisationCallbackContext = callbackContext;

                        try {
                            EMDKResults results = EMDKManager.getEMDKManager(cordova.getActivity().getApplicationContext(), me);

                            if (results.statusCode == EMDKResults.STATUS_CODE.SUCCESS) {
                                Log.i(LOG_TAG, "EMDK manager has been created");
                                callbackContext.success();
                            } else {
                                Log.w(LOG_TAG, "EMDKManager creation failed.  EMDK will not be available");
                                OnScanFailCallback(callbackContext, "EMDKManager creation failed.");
                            }
                        } catch (NoClassDefFoundError e) {
                            Log.w(LOG_TAG, "EMDK is not available on this device");
                            OnScanFailCallback(callbackContext, "EMDK is not available on this device");
                        }
                    }
                });
            }
        } else if (action.equalsIgnoreCase(START_SOFT_KEY)) {
            Log.d(LOG_TAG, "Start soft read");
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    StartReadingBarcode(SOFT_KEY, callbackContext);
                }
            });
        } else if (action.equalsIgnoreCase(START_HARD_KEY)) {
            Log.d(LOG_TAG, "Start hard read");
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    StartReadingBarcode(HARD_KEY, callbackContext);
                }
            });
        } else if (action.equalsIgnoreCase(STOP_READING)) {
            Log.d(LOG_TAG, "Stop reading");
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    StopReadingBarcode();
                }
            });
        } else {
            return false;
        }

        return true;
    }


    //------------------------------------------------------------------------------------------------------------------
    // EMDK MANAGER
    //------------------------------------------------------------------------------------------------------------------

    @Override
    public void onOpened(EMDKManager manager) {
        Log.i(LOG_TAG, "EMDKManager onOpened Method Called");

        if (scanner == null || !scanner.isEnabled()) {
            Log.i(LOG_TAG, "Initializing EMDKManager");

            // managers
            emdkManager = manager;
            BarcodeManager barcodeManager = (BarcodeManager) emdkManager.getInstance(EMDKManager.FEATURE_TYPE.BARCODE);

            // scanner
            List<ScannerInfo> scannersOnDevice = barcodeManager.getSupportedDevicesInfo();
            Iterator<ScannerInfo> it = scannersOnDevice.iterator();
            ScannerInfo scannerToActivate = null;
            while (it.hasNext()) {
                ScannerInfo scnInfo = it.next();
                if (scnInfo.getFriendlyName().equalsIgnoreCase("2D Barcode Imager")) { // always use the "2D Barcode Imager"
                    scannerToActivate = scnInfo;
                    break;
                }
            }
            scanner = barcodeManager.getDevice(scannerToActivate);
            scanner.addDataListener(this);
            scanner.addStatusListener(this);

            try {
                scanner.enable();

                Log.i(LOG_TAG, "Scanner enabled");
                if (initialisationCallbackContext != null) {
                    initialisationCallbackContext.success();
                    initialisationCallbackContext = null;
                }
            } catch (ScannerException e) {
                Log.i(LOG_TAG, "Error in enabling Scanner: " + e.getMessage());
                if (initialisationCallbackContext != null) {
                    OnScanFailCallback(initialisationCallbackContext, "Error in enabling Scanner: " + e.getMessage());
                }
            }
        } else {
            Log.i(LOG_TAG, "Already initialized");
        }
    }

    // necessary to be compliant with the EMDKListener interface
    @Override
    public void onClosed() {
    }


    //------------------------------------------------------------------------------------------------------------------
    // LOCAL METHODS
    //------------------------------------------------------------------------------------------------------------------

    private void StartReadingBarcode(String type, CallbackContext callbackContext) {
        Log.e(LOG_TAG, "StartRead: " + type);
        if (scanner != null) {
            try {
                if (scanner.isReadPending()) {
                    Log.e(LOG_TAG, "Cancel pending read");
                    scanner.cancelRead();
                }
                if (type.equalsIgnoreCase(HARD_KEY)) {
                    scanner.triggerType = Scanner.TriggerType.HARD;
                } else {
                    scanner.triggerType = Scanner.TriggerType.SOFT_ALWAYS;
                }
                Log.e(LOG_TAG, "start");
                scanCallbackContext = callbackContext;
                scanner.read();
            } catch (ScannerException e) {
                Log.e(LOG_TAG, "error: " + e.getMessage());
                OnScanFailCallback(callbackContext, "Error in enabling read: " + e.getMessage());
            }
        } else {
            Log.e(LOG_TAG, "error: Scanner is not enabled");
            OnScanFailCallback(callbackContext, "Scanner is not enabled");
        }
    }

    private void StopReadingBarcode() {
        Log.e(LOG_TAG, "StopReadingBarcode");
        scanCallbackContext = null;
        if (scanner != null && scanner.isReadPending()) {
            try {
                scanner.cancelRead();
            } catch (ScannerException e) {
                Log.e(LOG_TAG, "Error stopping read");
            }
        }
    }


    //------------------------------------------------------------------------------------------------------------------
    // SCANNER
    //------------------------------------------------------------------------------------------------------------------

    @Override
    public void onData(ScanDataCollection scanDataCollection) {
        if ((scanDataCollection != null) && (scanDataCollection.getResult() == ScannerResults.SUCCESS)) {
            ArrayList<ScanDataCollection.ScanData> scanData = scanDataCollection.getScanData();
            if (scanData.size() > 0) {
                Log.d(LOG_TAG, "Data scanned: " + scanData.get(0).getData());
                JSONObject scanDataResponse = new JSONObject();
                try {
                    scanDataResponse.put("data", scanData.get(0).getData());
                    scanDataResponse.put("type", scanData.get(0).getLabelType());
                    scanDataResponse.put("timestamp", scanData.get(0).getTimeStamp());
                } catch (JSONException e) {
                    Log.d(LOG_TAG, "JSON creation failed");
                }
                PluginResult result = new PluginResult(PluginResult.Status.OK, scanDataResponse);
                result.setKeepCallback(true);
                scanCallbackContext.sendPluginResult(result);
                StopReadingBarcode();
            }
        }
    }

    // Scanner gos to IDLE state after some seconds -> restart / revive read process
    @Override
    public void onStatus(StatusData statusData) {
        StatusData.ScannerStates state = statusData.getState();
        Log.d(LOG_TAG, "Scanner State Change: " + state);
        if (state.equals(StatusData.ScannerStates.IDLE) && scanCallbackContext != null && !scanner.isReadPending()) {
            try {
                scanner.read();
            } catch (ScannerException e) {
                Log.e(LOG_TAG, "Cannot revive read: " + e.getMessage());
                OnScanFailCallback(scanCallbackContext, "Exception whilst reviving read: " + e.getMessage());
            }
        }
    }


    //------------------------------------------------------------------------------------------------------------------
    // CALLBACKS
    //------------------------------------------------------------------------------------------------------------------

    private void OnScanFailCallback(CallbackContext callbackContext, String message) {
        if (callbackContext != null) {
            JSONObject failureMessage = new JSONObject();
            try {
                failureMessage.put("message", message);
            } catch (JSONException e) {
                Log.e(LOG_TAG, "JSON Error");
            }
            callbackContext.error(failureMessage);
        }
    }

}
