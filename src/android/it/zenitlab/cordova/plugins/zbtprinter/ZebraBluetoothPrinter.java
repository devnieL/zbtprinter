package it.zenitlab.cordova.plugins.zbtprinter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import com.zebra.sdk.comm.BluetoothConnectionInsecure;
import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.comm.ConnectionException;

import com.zebra.sdk.graphics.internal.ZebraImageAndroid;

import com.zebra.sdk.printer.PrinterStatus;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException;
import com.zebra.sdk.printer.ZebraPrinterLinkOs;
import com.zebra.sdk.printer.discovery.BluetoothDiscoverer;
import com.zebra.sdk.printer.discovery.DiscoveredPrinter;
import com.zebra.sdk.printer.discovery.DiscoveredPrinterBluetooth;
import com.zebra.sdk.printer.discovery.DiscoveryHandler;

import java.util.List;
import java.util.ArrayList;

public class ZebraBluetoothPrinter extends CordovaPlugin {

    private static final String LOG_TAG = "ZebraBluetoothPrinter";
    public List<DiscoveredPrinter> printers = new ArrayList<DiscoveredPrinter>();
    String mac = "AC:3F:A4:4E:F9:F3";

    public ZebraBluetoothPrinter() {
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if (action.equals("print")) {
            try {
                String mac = args.getString(0);
                JSONArray imageBase64Parts = args.getJSONArray(1);
                sendData(callbackContext, mac, imageBase64Parts);
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
            BluetoothDiscoverer.findPrinters(this.cordova.getActivity().getApplicationContext(), new DiscoveryHandler(){

                public void foundPrinter(DiscoveredPrinter printer) {
                    printers.add(printer);
                }

                public void discoveryFinished() {

                    try {
                        JSONArray macs = new JSONArray();

                        for (int i = 0; i < printers.size(); i++) {
                            DiscoveredPrinterBluetooth _printer = (DiscoveredPrinterBluetooth) printers.get(i);
                            JSONObject _printerObj = new JSONObject();
                            _printerObj.put("name", _printer.friendlyName);
                            _printerObj.put("address", _printer.address);

                            macs.put(i, _printerObj);
                        }

                        callbackContext.success(macs);
                        printers = new ArrayList<DiscoveredPrinter>();

                    }catch(JSONException exception){
                        callbackContext.error(exception.getMessage());
                    }
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
    void sendData(final CallbackContext callbackContext, final String mac, final JSONArray imageBase64Parts) throws IOException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Instantiate insecure connection for given Bluetooth MAC Address.
                    Connection connection = new BluetoothConnectionInsecure(mac);

                    // Verify the printer is ready to print
                    if (isPrinterReady(connection)) {

                        // Open the connection - physical connection is established here.
                        connection.open();

                        ZebraPrinter printer = ZebraPrinterFactory.getInstance(connection);
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                        for(int i = 0; i<imageBase64Parts.length(); i++){

                            byte[] b = imageBase64Parts.getString(i).getBytes();
                            Log.d("ZEBRA PRINTING", " ==========================> LENGTH OF PART " + i + " : " + String.valueOf(b.length));
                            outputStream.write(b);
                        }

                        byte bytes[] = outputStream.toByteArray();

                        byte[] decodedString = Base64.decode(bytes, Base64.DEFAULT);

                        Log.d("ZEBRA PRINTING", "DECODED STRING LENGTH : " + decodedString.length);

                        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

                        Log.d("ZEBRA PRINTING", "BITMAP HEIGHT : " + decodedByte.getHeight());
                        Log.d("ZEBRA PRINTING", "BITMAP WIDTH : " + decodedByte.getWidth());

                        int width = decodedByte.getWidth();
                        int height = decodedByte.getHeight();

                        connection.write(("^XA^LL" + (height + 100) + "^XZ").getBytes());

                        printer.printImage(new ZebraImageAndroid(decodedByte), 0, 0, width, height, false);

                        // Send the data to printer as a byte array.
                        //connection.write(bytes);


                        // Make sure the data got to the printer before closing the connection
                        //Thread.sleep(5000);

                        // Close the insecure connection to release resources.
                        connection.close();
                        callbackContext.success("Impresi�n realizada correctamente.");
                    } else {
						callbackContext.error("Impresora no lista.");
					}
                } catch (Exception e) {
                    // Handle communications error here;
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
        PrinterStatus printerStatus = null;

        try {
            printerStatus = printer.getCurrentStatus();
        }catch(ConnectionException exp){
            ZebraPrinterLinkOs linkOsPrinter = ZebraPrinterFactory.createLinkOsPrinter(printer);
            printerStatus = (linkOsPrinter != null) ? linkOsPrinter.getCurrentStatus() : printer.getCurrentStatus();
        }

        if(printerStatus == null){
            throw new ConnectionException("No se puede obtener el estado de la impresora.");
        }

        if (printerStatus.isReadyToPrint) {
            isOK = true;
        } else if (printerStatus.isPaused) {
            throw new ConnectionException("No se puede imprimir porque la impresora está pausada.");
        } else if (printerStatus.isHeadOpen) {
            throw new ConnectionException("La impresora no está cerrada correctamente.");
        } else if (printerStatus.isPaperOut) {
            throw new ConnectionException("La impresora no cuenta con suficiente papel para realizar la impresión.");
        } else {
            throw new ConnectionException("No se puede imprimir.");
        }
        return isOK;
    }
}
