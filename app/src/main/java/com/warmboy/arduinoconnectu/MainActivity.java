package com.warmboy.arduinoconnectu;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static android.content.ContentValues.TAG;
import static com.warmboy.arduinoconnectu.R.id.add;
import static com.warmboy.arduinoconnectu.R.id.graph;

public class MainActivity extends Activity {
    public final String ACTION_USB_PERMISSION = "com.warmboy.arduinoconnectu.USB_PERMISSION";
    Button startButton, stopButton, clearButton;
    TextView sensorView0, sensorTime;
    UsbManager usbManager;
    UsbDevice device;
    UsbSerialDevice serialPort;
    UsbDeviceConnection connection;
    boolean counter;

    private ArrayList<XYValue> xyValueArray;

    LineGraphSeries<DataPoint> xySeries;

    GraphView mLineGraph;

    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {
        //Defining a Callback which triggers whenever data is read. For this app this is every sec
        @Override
        public void onReceivedData(byte[] arg0) {
            String datay = null;
            try {
                datay = new String(arg0, "UTF-8");  //converts data to readable form
                displayData(sensorView0, datay); //call to method that displays data
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    };

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        //Broadcast Receiver to automatically start and stop the Serial connection.
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) {
                    connection = usbManager.openDevice(device);
                    serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                    if (serialPort != null) {
                        if (serialPort.open()) { //Set Serial Connection Parameters.
                            setUiEnabled(true);
                            serialPort.setBaudRate(9600); //Set baud rate, needs to be same as Arduino
                            serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                            serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            serialPort.read(mCallback);
                            counter = true; //start the counter for the time data

                        } else {
                            Log.d("SERIAL", "PORT NOT OPEN");
                        }
                    } else {
                        Log.d("SERIAL", "PORT IS NULL");
                    }
                } else {
                    Log.d("SERIAL", "PERM NOT GRANTED");
                }
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                onClickStart(startButton); //start connection when connect button is pushed
                counter = true;


            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                onClickStop(stopButton); //stop connection when stop button is pushed

            }
        }

        ;
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        usbManager = (UsbManager) getSystemService(this.USB_SERVICE);
        //Set up UI
        startButton = (Button) findViewById(R.id.buttonStart);
        clearButton = (Button) findViewById(R.id.buttonClear);
        stopButton = (Button) findViewById(R.id.buttonStop);
        sensorView0 = (TextView) findViewById(R.id.sensorView0);
        sensorTime = (TextView) findViewById(R.id.sensorTime);
        setUiEnabled(false);

        //Intent filter specifying that want USB device for broadcast reciever
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);

        mLineGraph = (GraphView) findViewById(R.id.graph);
        //set Scrollable and Scaleable
        mLineGraph.getViewport().setScrollable(true);

        //set manual x bounds
        mLineGraph.getViewport().setYAxisBoundsManual(true);
        mLineGraph.getViewport().setMaxY(5);
        mLineGraph.getViewport().setMinY(0);

        //set manual y bounds
        mLineGraph.getViewport().setXAxisBoundsManual(true);
        mLineGraph.getViewport().setMaxX(110);
        mLineGraph.getViewport().setMinX(0);
        mLineGraph.getGridLabelRenderer().setVerticalAxisTitle("Voltage (V)");
        mLineGraph.getGridLabelRenderer().setHorizontalAxisTitle("Time (s)");

        xyValueArray = new ArrayList<>();
        //counter = true;
        callinit();
    }

    public void init() {
        //declare the xySeries Object
        if (!sensorTime.getText().toString().equals("") && !sensorView0.getText().toString().equals("")) {
            xySeries = new LineGraphSeries<DataPoint>();
            double x = Double.parseDouble(sensorTime.getText().toString());
            double y = Double.parseDouble(sensorView0.getText().toString());
            xyValueArray.add(new XYValue(x, y));
        } else {
            Log.d(TAG, "onCreate: No data to plot.");
        }

        if (xyValueArray.size() != 0) {
            makeGraph();
        } else {
            Log.d(TAG, "onCreate: No data to plot.");
        }
    }

    public void makeGraph() {
        if (counter) {
            count++;
        }
        //add the data to the series
        for (int i = 0; i < xyValueArray.size(); i++) {
            try {
                double x = xyValueArray.get(i).getX();
                double y = xyValueArray.get(i).getY();
                xySeries.appendData(new DataPoint(x, y), true, 1000);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "createScatterPlot: IllegalArgumentException: " + e.getMessage());
            }
        }

        //set Scrollable
        mLineGraph.getViewport().setScrollable(true);
        //set manual y bounds
        mLineGraph.getViewport().setYAxisBoundsManual(true);
        mLineGraph.getViewport().setMaxY(5);
        mLineGraph.getViewport().setMinY(0);
        //set manual x bounds
        mLineGraph.getViewport().setXAxisBoundsManual(true);
        mLineGraph.getViewport().setMaxX(110);
        mLineGraph.getViewport().setMinX(0);
        //Set x and y labels
        mLineGraph.getGridLabelRenderer().setVerticalAxisTitle("Voltage (V)");
        mLineGraph.getGridLabelRenderer().setHorizontalAxisTitle("Time (s)");
        //Add points to the graph
        mLineGraph.addSeries(xySeries);

    }

    public void setUiEnabled(boolean bool) {
        startButton.setEnabled(!bool);
        stopButton.setEnabled(bool);
        clearButton.setEnabled(!bool);
        sensorView0.setEnabled(bool);
        sensorTime.setEnabled(bool);
    }

    public void onClickStart(View view) {
        counter = true;
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();//Arduino Vendor ID, This is specific to Arduino Unos!
                if (deviceVID != 0) //checks that there is a value not equal to zero for Vendor ID
                {
                    PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                    usbManager.requestPermission(device, pi);
                    keep = false;
                } else {
                    connection = null;
                    device = null;
                    Toast.makeText(this,"No device is connected",Toast.LENGTH_LONG).show();
                }
                if (!keep)
                    break;
            }
        }
    }

    public void onClickStop(View view) {
        setUiEnabled(false);
        serialPort.close();
        Toast.makeText(getApplicationContext(), "Stopped Collecting data", Toast.LENGTH_SHORT).show();
        counter = false;
    }

    public void onClickClear(View view) {
        setUiEnabled(true);
        Intent nintent = getIntent();
        finish();
        startActivity(nintent);
        overridePendingTransition(0, 0);
        nintent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
    }


    int count = 0;

    public void displayData(TextView VoltageValue, String vtext) {

        final TextView Voltagebox = VoltageValue;
        final CharSequence Vtext = vtext;

        if (connection != null) {
            final CharSequence time = Integer.toString(count);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Voltagebox.append(Vtext); //displays voltgage values on the UI
                    sensorTime.setText(String.valueOf(time)); //display time values on the UI
                }
            });
        } else
        {
            //Do nothing if not connected
        }
    }

    int onesec = 1000;
    Handler handler = new Handler();

    public void callinit() {
            handler.postDelayed(new Runnable() {

            public void run() {
                init();          // call to init to start pulling data
                sensorTime.setText("");
                sensorView0.setText("");
                handler.postDelayed(this, onesec);
            }
        }, onesec);
    }
}








