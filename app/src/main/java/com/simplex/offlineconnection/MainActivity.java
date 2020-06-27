package com.simplex.offlineconnection;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.io.FileNotFoundException;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private ConnectionLifecycleCallback connectionLifecycleCallback;
    private EndpointDiscoveryCallback endpointDiscoveryCallback;
    private PayloadCallback payloadCallback;
    private EditText username, msg;
    private boolean sender;
    private Uri uri;
    private boolean isfile = false;
    private TextView msgTv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button send = findViewById(R.id.send);
        Button sendFile = findViewById(R.id.sendFile);
        Button receive = findViewById(R.id.receive);
        username = findViewById(R.id.username);
        msg = findViewById(R.id.msg);
        msgTv = findViewById(R.id.msgTv);

        checkPermission(true);
        setPayloadCallback();

        endpointDiscoveryCallback =
                new EndpointDiscoveryCallback() {
                    @Override
                    public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                        // An endpoint was found. We request a connection to it.
                        Nearby.getConnectionsClient(MainActivity.this)
                                .requestConnection(getUserName(), endpointId, connectionLifecycleCallback)
                                .addOnSuccessListener(
                                        (Void unused) -> {
                                            // We successfully requested a connection. Now both sides
                                            // must accept before the connection is established.
                                            Log.d(TAG, "onEndpointFound: success");
                                            Toast.makeText(MainActivity.this, "onEndpointFound: success", Toast.LENGTH_LONG).show();
                                        })
                                .addOnFailureListener(
                                        (Exception e) -> {
                                            // Nearby Connections failed to request the connection.
                                            Log.d(TAG, "onEndpointFound: error" + e.getMessage());
                                            Toast.makeText(MainActivity.this, "onEndpointFound: error" + e.getMessage(), Toast.LENGTH_LONG).show();

                                        });
                    }

                    @Override
                    public void onEndpointLost(String endpointId) {
                        // A previously discovered endpoint has gone away.
                        Log.d(TAG, "onEndpointLost: ");
                        Toast.makeText(MainActivity.this, "onEndpointLost: ", Toast.LENGTH_LONG).show();

                    }
                };

        connectionLifecycleCallback =
                new ConnectionLifecycleCallback() {
                    @Override
                    public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                        // Automatically accept the connection on both sides.
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Accept connection to " + connectionInfo.getEndpointName())
                                .setMessage("Confirm the code matches on both devices: " + connectionInfo.getAuthenticationToken())
                                .setPositiveButton(
                                        "Accept",
                                        (DialogInterface dialog, int which) ->
                                                // The user confirmed, so we can accept the connection.
                                                Nearby.getConnectionsClient(MainActivity.this).acceptConnection(endpointId, payloadCallback))
                                .setNegativeButton(
                                        android.R.string.cancel,
                                        (DialogInterface dialog, int which) ->
                                                // The user canceled, so we should reject the connection.
                                                Nearby.getConnectionsClient(MainActivity.this).rejectConnection(endpointId))
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .show();
                        Toast.makeText(MainActivity.this, "connection intiated", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onConnectionResult(String endpointId, ConnectionResolution result) {
                        switch (result.getStatus().getStatusCode()) {
                            case ConnectionsStatusCodes.STATUS_OK:
                                // We're connected! Can now start sending and receiving data.
                                Log.d(TAG, "onConnectionResult: ok");
                                Toast.makeText(MainActivity.this, "onConnectionResult: ok", Toast.LENGTH_LONG).show();
                                if (isfile) {
                                    ParcelFileDescriptor pfd = null;
                                    try {
                                        pfd = getContentResolver().openFileDescriptor(uri, "r");
                                        Payload filePayload = Payload.fromFile(pfd);
                                        Nearby.getConnectionsClient(MainActivity.this).sendPayload(endpointId, filePayload);

                                    } catch (FileNotFoundException e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    Payload bytesPayload = Payload.fromBytes(msg.getText().toString().getBytes());
                                    Nearby.getConnectionsClient(MainActivity.this).sendPayload(endpointId, bytesPayload);
                                }
                                break;
                            case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                                // The connection was rejected by one or both sides.
                                Log.d(TAG, "onConnectionResult: reject");
                                Toast.makeText(MainActivity.this, "onConnectionResult: reject ", Toast.LENGTH_LONG).show();

                                break;
                            case ConnectionsStatusCodes.STATUS_ERROR:
                                // The connection broke before it was able to be accepted.
                                Log.d(TAG, "onConnectionResult: error");
                                Toast.makeText(MainActivity.this, "onConnectionResult: error", Toast.LENGTH_LONG).show();

                                break;
                            default:
                                // Unknown status code
                                Log.d(TAG, "onConnectionResult: unknown");
                                Toast.makeText(MainActivity.this, "onConnectionResult: unknown", Toast.LENGTH_LONG).show();

                        }
                    }

                    @Override
                    public void onDisconnected(String endpointId) {
                        // We've been disconnected from this endpoint. No more data can be
                        // sent or received.
                        Log.d(TAG, "onDisconnected: ");
                        Toast.makeText(MainActivity.this, "onDisconnected: ", Toast.LENGTH_LONG).show();

                    }
                };


        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sender = true;
                startDiscovery();
            }
        });

        sendFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sender = true;
                if (VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    checkPermission(false);
                } else {
                    fileIntent();
                }
            }
        });

        receive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sender = false;
                startAdvertising();
            }
        });
    }

    private void fileIntent() {
        Intent intent = new Intent();
        //sets the select file to all types of files
        intent.setType("*/*");
        //allows to select data and return it
        intent.setAction(Intent.ACTION_GET_CONTENT);
        //starts new activity to select file and return data
        startActivityForResult(Intent.createChooser(intent, "Choose File to Upload.."), 10);
    }

    private void startAdvertising() {
        AdvertisingOptions advertisingOptions =
                new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build();
        Nearby.getConnectionsClient(this)
                .startAdvertising(
                        getUserName(), "com.simplex.offlineconnection", connectionLifecycleCallback, advertisingOptions)
                .addOnSuccessListener(
                        (Void unused) -> {
                            // We're advertising!
                            Toast.makeText(MainActivity.this, "startAdvertising: we are adevrtising", Toast.LENGTH_LONG).show();
                            Log.d(TAG, "startAdvertising: we are adevrtising");
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            // We were unable to start advertising.
                            Toast.makeText(MainActivity.this, "startAdvertising: error", Toast.LENGTH_LONG).show();
                            Log.d(TAG, "startAdvertising: error" + e.getMessage());
                        });
    }

    private void startDiscovery() {
        DiscoveryOptions discoveryOptions =
                new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build();
        Nearby.getConnectionsClient(this)
                .startDiscovery("com.simplex.offlineconnection", endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener(
                        (Void unused) -> {
                            // We're discovering!
                            Log.d(TAG, "startDiscovery: we are discovering");
                            Toast.makeText(MainActivity.this, "startDiscovery: we are discovering", Toast.LENGTH_LONG).show();
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            // We're unable to start discovering.
                            Log.d(TAG, "startDiscovery: error" + e.getMessage());
                            Toast.makeText(MainActivity.this, "startDiscovery: error" + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
    }

    private void checkPermission(boolean location) {
        if (location) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "permission granted !!", Toast.LENGTH_LONG).show();
            } else {
                // Permission to access the location is missing. Show rationale and request permission
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        1052);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                // file intent
                fileIntent();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        1053);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1052: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted.
                    Toast.makeText(this, "Permission granted", Toast.LENGTH_LONG).show();
                    //file intent or image intent
                } else {
                    // Permission denied - Show a message to inform the user that this app only works
                    // with these permissions granted
                    Toast.makeText(this, "Should agree", Toast.LENGTH_LONG).show();
                }
                return;
            }
            case 1053: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted.
                    //file intent
                    fileIntent();
                } else {
                    // Permission denied - Show a message to inform the user that this app only works
                    // with these permissions granted
                    Toast.makeText(this, "Should agree", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private String getUserName() {
        if (username.getText().toString().isEmpty()) {
            Toast.makeText(this, "", Toast.LENGTH_LONG).show();
            return "no name";
        } else {
            return username.getText().toString();
        }
    }

    private void setPayloadCallback() {
        payloadCallback = new PayloadCallback() {
            @Override
            public void onPayloadReceived(@NonNull String s, @NonNull Payload payload) {

                if (payload.getType() == 1) {

                    byte[] receivedBytes = payload.asBytes();
                    String value = new String(receivedBytes);
                    if (sender) {
                        Toast.makeText(MainActivity.this, "Msg started to send", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Msg is : " + value, Toast.LENGTH_LONG).show();
                        msgTv.setText(value);
                    }

                } else if (payload.getType() == 2) {
                    if (sender) {
                        Toast.makeText(MainActivity.this, "File started to send", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this, "File started to Download with size : " + payload.asFile().getSize(), Toast.LENGTH_LONG).show();
                    }
                }

            }

            @Override
            public void onPayloadTransferUpdate(@NonNull String s, @NonNull PayloadTransferUpdate payloadTransferUpdate) {
                if (isfile) {
                    if (payloadTransferUpdate.getStatus() == PayloadTransferUpdate.Status.SUCCESS) {
                        if (sender) {
                            Toast.makeText(MainActivity.this, "File sent successfully", Toast.LENGTH_LONG).show();
                        } else {
                            long payloadId = payloadTransferUpdate.getPayloadId();
                            Toast.makeText(MainActivity.this, "File downloaded successfully !! " + payloadId, Toast.LENGTH_LONG).show();
                        }
                    }
                } else {
                    if (sender)
                        Toast.makeText(MainActivity.this, "Msg had been sent SUCCESSFULLY !!", Toast.LENGTH_LONG).show();
                }
            }
        };
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 10 && resultCode == RESULT_OK && data != null) {
            isfile = true;
            uri = data.getData();
            startDiscovery();
        }
    }
}