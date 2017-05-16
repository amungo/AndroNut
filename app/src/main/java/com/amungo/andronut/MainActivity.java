package com.amungo.andronut;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

import static android.hardware.usb.UsbConstants.USB_DIR_IN;
import static android.hardware.usb.UsbConstants.USB_DIR_OUT;
import static android.hardware.usb.UsbConstants.USB_TYPE_VENDOR;

public class MainActivity extends AppCompatActivity {

    private final String TAG = MainActivity.class.getSimpleName();

    private final int MAX_UPLOAD_BLOCK_SIZE8 = 2048;
    private final int CONTROL_TX_TIMEOUT = 500;


    private UsbManager usbManager;
    private UsbDevice bootDevice = null;
    private UsbDevice streamDevice = null;
    private UsbDeviceConnection bootConnection = null;
    private UsbDeviceConnection streamConnection = null;
    private TextView textView;
    private ScrollView scrollView;

    Firmware firmware = null;
    NtConfig ntConfig = null;
    boolean isLoadingConfig = false;
    boolean isStreaming = false;

    private void logOnUi(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.append(s);
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void scanDevices() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        String str = "deviceList: " + deviceList.size() + " devices\n";
        Log.d(TAG, str);

        for (final UsbDevice usbDevice : deviceList.values()) {
            final int vendorId  = usbDevice.getVendorId();
            final int productId = usbDevice.getProductId();
            str += String.format(Locale.ENGLISH, "DEVICE: 0x%04X 0x%04X", vendorId, productId);

            if ( vendorId == 0x04b4 && productId == 0x00f3 ) {
                bootDevice = usbDevice;
                str += " --> BOOT";
            } else if ( vendorId == 0x04b4 && productId == 0x00f1 ) {
                streamDevice = usbDevice;
                str += " --> STREAM";
            }

            str += "\n";
            Log.d(TAG, String.format(Locale.ENGLISH, "DEVICE: 0x%04X 0x%04X", vendorId, productId));
        }
        textView.append(str);


        if (bootConnection == null && bootDevice != null ) {
            bootConnection = usbManager.openDevice(bootDevice);
            if ( bootConnection == null ) {
                askPermissions(bootDevice);
            }
        }

        if (streamConnection == null && streamDevice != null ) {
            streamConnection = usbManager.openDevice(streamDevice);
            if ( streamConnection == null ) {
                askPermissions(streamDevice);
            }
        }

        if ( streamConnection != null ){
            readFirmwareVersion();
        }
    }

    private void flashDevice() {
        textView.append("flashDevice()\n");

        if (bootConnection == null ) {
            textView.append("bootConnection is null\n");
        } else {

            UsbInterface usbInterface = bootDevice.getInterface(0);
            if (!bootConnection.claimInterface(usbInterface, true)) {
                textView.append( "claimInterface error\n");
            } else {
                textView.append( "claimInterface OK\n");
            }

            boolean loadOk = true;
            firmware.splitSections(MAX_UPLOAD_BLOCK_SIZE8);
            ArrayList<FirmwareData> sections = firmware.getSectionsList();
            textView.append( "Loading firmware, " + sections.size() + " transfers\n" );
            for ( int i = 0; i < sections.size(); i++ ) {
                FirmwareData fwData = sections.get(i);

                int requestType = USB_TYPE_VENDOR | USB_DIR_OUT;
                int res = bootConnection.controlTransfer(
                        requestType, // bmRequestType
                        DeviceCommands.CMD_FW_LOAD, // request
                        (fwData.devAddr & 0xFFFF), //value
                        ((fwData.devAddr >> 16) & 0xFFFF), // index
                        fwData.buffer, // buffer
                        fwData.buffer.length, // length
                        CONTROL_TX_TIMEOUT  // timeout (ms)
                );
                String lastLog = String.format(Locale.ENGLISH,
                        "ctrTx( 0x%04X val %06x idx %06x %d ), res %d\n",
                        requestType,
                        (fwData.devAddr & 0xFFFF),
                        ((fwData.devAddr >> 16) & 0xFFFF),
                        fwData.buffer.length,
                        res
                );
                if ( res != fwData.buffer.length ) {
                    textView.append( "Last operation: " + lastLog );
                    textView.append("Bad res. Aborted\n");
                    loadOk = false;
                    break;
                }
            }
            if ( loadOk ) {
                textView.append("Firmware loaded\n");
                bootConnection = null;
            } else {
                textView.append("Firmware was not loaded\n");
            }
        }
        scrollView.fullScroll(View.FOCUS_DOWN);
    }

    private void loadNtConfig() {
        if ( isLoadingConfig ) {
            logOnUi("loadNtConfig() already running\n");
        } else {
            isLoadingConfig = true;
            logOnUi("loadNtConfig() start running\n");

            if ( streamConnection == null ) {
                logOnUi("streamConnection is null\n");
            } else {
                ArrayList<RegisterInfo> config = ntConfig.getConfig();

                logOnUi("Stage1\n");
                int stop_address = 112;
                for ( int i = 0; i < config.size(); i++ ) {
                    setOneConfigRegister(config.get(i));
                    if ( i == stop_address ) {
                        break;
                    }
                }

                logOnUi("\nStage2\n");
                stop_address = 48;
                for ( int i = 0; i < config.size(); i++ ) {
                    setOneConfigRegister(config.get(i));
                    if ( i == stop_address ) {
                        break;
                    }
                }

                logOnUi("\nConfig loaded in nt1065\n");
                logOnUi("Ready to start stream\n");
            }
            isLoadingConfig = false;
        }
    }

    private void setOneConfigRegister(RegisterInfo reg) {
        int requestType = USB_TYPE_VENDOR | USB_DIR_OUT;
        byte[] buffer = {0,0,0,0,  0,0,0,0,  0,0,0,0,  0,0,0,0};
        buffer[0] = (byte)(reg.value & 0xFF);
        buffer[1] = (byte)(reg.address & 0xFF);
        int res = streamConnection.controlTransfer(
                requestType, // bmRequestType
                DeviceCommands.CMD_REG_WRITE, // request
                0, //value
                1, // index
                buffer, // buffer
                buffer.length, // length
                CONTROL_TX_TIMEOUT  // timeout (ms)
        );
        if ( res != buffer.length ) {
            logOnUi("setOneConfigRegister() error, res = " + res + "\n");
        } else {
            logOnUi(".");
        }
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void readFirmwareVersion() {
        if ( streamConnection != null ) {
            FirmwareDescription fwDesc = new FirmwareDescription();
            int res = streamConnection.controlTransfer(
                    USB_TYPE_VENDOR | USB_DIR_IN, // bmRequestType
                    DeviceCommands.CMD_GET_VERSION, // request
                    0, //value
                    1, // index
                    fwDesc.buffer, // buffer
                    fwDesc.buffer.length, // length
                    CONTROL_TX_TIMEOUT  // timeout (ms)
            );
            if ( res == fwDesc.buffer.length ) {
                textView.append(fwDesc.getVersionString() + "\n");
            } else {
                textView.append("controlTransfer() res = " + res + " != " + fwDesc.buffer.length);
            }
        } else {
            textView.append( "readFirmwareVersion() error: streamConnection is null\n");
        }
        scrollView.fullScroll(View.FOCUS_DOWN);
    }

    private void runRead() {
        if ( isStreaming ) {
            logOnUi("Already streaming");
            return;
        }
        isStreaming = true;
        logOnUi("runRead()\n");

        if (streamConnection == null ) {
            logOnUi("streamConnection is null\n");
        } else {
            logOnUi("Interfaces count " + streamDevice.getInterfaceCount() + "\n");
            for (int i = 0; i < streamDevice.getInterfaceCount(); ++i ) {
                UsbInterface usbInterface = streamDevice.getInterface(i);

                logOnUi( "Ifce[" + i + "]:" +
                        " class " + usbInterface.getInterfaceClass() +
                        " ep count " + usbInterface.getEndpointCount() + "\n"
                );

                for ( int ep = 0; ep < usbInterface.getEndpointCount(); ++ep ) {
                    logOnUi( String.format( Locale.ENGLISH,
                            "       Enpoint %d addr=0x%02x maxpacket=%d\n",
                            ep,
                            usbInterface.getEndpoint(ep).getAddress(),
                            usbInterface.getEndpoint(ep).getMaxPacketSize()
                    ));
                }
            }

            UsbInterface usbInterface = streamDevice.getInterface(0);
            if (!streamConnection.claimInterface(usbInterface, true)) {
                logOnUi( "claimInterface error\n");
            } else {
                logOnUi( "claimInterface OK\n");
            }

            UsbEndpoint readEndPoint = usbInterface.getEndpoint(0);

            final int BUF_CNT = 768;
            final int BUF_SIZE = 16 * 1024;
            String filename = "amungo.bin";
            FileOutputStream outputStream = null;
            try {
                outputStream = openFileOutput(filename, Context.MODE_WORLD_READABLE);
            } catch (FileNotFoundException e) {
                logOnUi("File create error: " + filename + "\n" );
                e.printStackTrace();
            }

            for ( int i = 0; i < BUF_CNT; i++ ) {
                ByteBuffer buffer = ByteBuffer.wrap(new byte[BUF_SIZE]);
                UsbRequest request = new UsbRequest();
                request.setClientData(buffer);
                request.initialize(streamConnection, readEndPoint);
                if (!request.queue(buffer, BUF_SIZE)) {
                    logOnUi("Error queueing request.");
                }
            }

            long prevTime = Calendar.getInstance().getTimeInMillis();
            long count = 0;
            while ( isStreaming ) {
                count++;
                UsbRequest response = streamConnection.requestWait();
                if (response == null) {
                    logOnUi("Null response");
                }

                ByteBuffer buffer = (ByteBuffer)response.getClientData();

                final byte[] data = buffer.array();
                try {
                    outputStream.write( data );
                } catch (IOException e) {
                    logOnUi("Failed to write to file " + data.length + " bytes\n");
                    e.printStackTrace();
                }

                //response.initialize(streamConnection, readEndPoint);
                if (!response.queue(buffer, BUF_SIZE)) {
                    logOnUi("Error REqueueing request.\n");
                }

                if ( count == 640 ) {
                    long curTime = Calendar.getInstance().getTimeInMillis();
                    logOnUi( "Speed " + 10000.0 / (double)(curTime - prevTime) + " Mb/s\n" );
                    prevTime = curTime;
                    count = 0;
                }
            }
            logOnUi( "Streaming finished\n");
            try {
                logOnUi( "Closing stream...\n" );
                outputStream.close();
                logOnUi( "Data dumped\n" );
            } catch (IOException e) {
                logOnUi("File close error\n");
                e.printStackTrace();
            }

            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(getFileStreamPath(filename)) );
            shareIntent.setType("*/*");
            startActivity(Intent.createChooser(shareIntent, "Send data to"));

        }
    }

    private void prepareFirmware() {
        firmware = new Firmware(getApplicationContext().getResources());
        textView.append( firmware.parse() );
        textView.append( firmware.getSectionsDescription() );

        ntConfig = new NtConfig(getApplicationContext().getResources());
        textView.append( ntConfig.getConfigInfo() );
    }

    private void askPermissions(UsbDevice device) {
        final String ACTION_USB_PERMISSION = "com.amungo.andronut.USB_PERMISSION";
        PendingIntent mPermissionIntent = PendingIntent.getBroadcast(
                getApplicationContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
        usbManager.requestPermission(device, mPermissionIntent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        usbManager = (UsbManager) getApplicationContext().getSystemService(Context.USB_SERVICE);
        textView = (TextView)findViewById(R.id.mainTextView);
        scrollView = (ScrollView)findViewById(R.id.mainScrollView);


        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        prepareFirmware();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_scan) {
            scanDevices();
            return true;
        } else
        if (id == R.id.action_flash_device) {
            flashDevice();
            return true;
        } else
        if (id == R.id.action_load_nt1065) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    loadNtConfig();
                }
            }).start();
            return true;
        } else
        if (id == R.id.action_start_stream) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    runRead();
                }
            }).start();
            return true;
        } else
        if (id == R.id.action_stop_stream) {
            isStreaming = false;
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
