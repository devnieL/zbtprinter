package it.zenitlab.cordova.plugins.zbtprinter;

import java.io.IOException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import android.util.Log;
import com.zebra.sdk.comm.BluetoothConnectionInsecure;
import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.printer.PrinterStatus;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException;

import com.zebra.android.discovery.*;

import java.util.List;
import java.util.ArrayList;

public class ZebraBluetoothPrinter extends CordovaPlugin {

    private static final String LOG_TAG = "ZebraBluetoothPrinter";

    public List<DiscoveredPrinter> printers = new ArrayList<DiscoveredPrinter>();

    public ZebraBluetoothPrinter() {}

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if (action.equals("print")) {
            try {
                String mac = args.getString(0);
                String msg = args.getString(1);
                sendData(callbackContext, mac, msg);
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }

        if(action.equals("find")){
          try{
            findPrinter(callbackContext);
          }catch(Exception e){
            Log.e(LOG_TAG, e.getMessage());
            e.printStackTrace();
          }
          return true;
        }

        return false;
    }

    public void findPrinter(final CallbackContext callbackContext){

      try{
        BluetoothDiscovery.findPrinter(this.cordova.getActivity().getApplicationContext(), new DiscoveryHandler(){

          public void foundPrinter(DiscoveredPrinter printer) {
              printers.push(printer);
          }

          public void discoveryFinished() {

              String macs[] = new String[printers.size()];

              for(int i=0; i<printers.size; i++){
                macs[i] = printers.get(i);
              }

              callbackContext.success(macs);
              printers = new ArrayList<DiscoveredPrinter>();
          }

          public void discoveryError(String message) {
              //Error during discovery
              callbackContext.error(message);
          }

        });
      }catch(Exception e){
        e.printStackTrace();
      }

    }

    /*
     * This will send data to be printed by the bluetooth printer
     */
    void sendData(final CallbackContext callbackContext, final String mac, final String msg) throws IOException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Instantiate insecure connection for given Bluetooth MAC Address.
                    Connection thePrinterConn = new BluetoothConnectionInsecure(mac);

                    // Verify the printer is ready to print
                    if (isPrinterReady(thePrinterConn)) {

                        // Open the connection - physical connection is established here.
                        thePrinterConn.open();

                        // Send the data to printer as a byte array.
                        // thePrinterConn.write("^XA^FO0,20^FD^FS^XZ".getBytes());
                        thePrinterConn.write(msg.getBytes());


                        // Make sure the data got to the printer before closing the connection
                        Thread.sleep(500);

                        // Close the insecure connection to release resources.
                        thePrinterConn.close();
                        callbackContext.success("Done");
                    } else {
          						callbackContext.error("Printer is not ready");
          					}
                } catch (Exception e) {
                    // Handle communications error here.
                    callbackContext.error(e.getMessage());
                }
            }
        }).start();
    }

    private Boolean isPrinterReady(Connection connection) throws ConnectionException, ZebraPrinterLanguageUnknownException {
        Boolean isOK = false;
        connection.open();
        // Creates a ZebraPrinter object to use Zebra specific functionality like getCurrentStatus()
        ZebraPrinter printer = ZebraPrinterFactory.getInstance(connection);
        PrinterStatus printerStatus = printer.getCurrentStatus();
        if (printerStatus.isReadyToPrint) {
            isOK = true;
        } else if (printerStatus.isPaused) {
            throw new ConnectionException("Cannot Print because the printer is paused.");
        } else if (printerStatus.isHeadOpen) {
            throw new ConnectionException("Cannot Print because the printer media door is open.");
        } else if (printerStatus.isPaperOut) {
            throw new ConnectionException("Cannot Print because the paper is out.");
        } else {
            throw new ConnectionException("Cannot Print.");
        }
        return isOK;
    }
}
