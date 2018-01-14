/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.ipronto.nofilterisgreatvpn;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class NoFilterVpnService extends android.net.VpnService implements Handler.Callback {
    private static final String TAG = "VpnConnection";

    public static final String ACTION_CONNECT = "com.example.android.toyvpn.START";
    public static final String ACTION_DISCONNECT = "com.example.android.toyvpn.STOP";

    private Handler mHandler;

    private static class Connection extends Pair<Thread, ParcelFileDescriptor> {
        public Connection(Thread thread, ParcelFileDescriptor pfd) {
            super(thread, pfd);
        }
    }

    private final AtomicReference<Thread> mConnectingThread = new AtomicReference<>();
    private final AtomicReference<Connection> mConnection = new AtomicReference<>();

    private AtomicInteger mNextConnectionId = new AtomicInteger(1);

    private PendingIntent mConfigureIntent;

    boolean disconnect_requested;

    @Override
    public void onCreate() {
        // The handler is only used to show messages.
        if (mHandler == null) {
            mHandler = new Handler(this);
        }

        // Create the intent to "configure" the connection (just start VpnClient).
        mConfigureIntent = PendingIntent.getActivity(this, 0, new Intent(this, VpnClient.class),
                PendingIntent.FLAG_UPDATE_CURRENT);

    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            disconnect_requested = true;
            disconnect();
            return START_NOT_STICKY;
        } else {
            disconnect_requested = false;
            connect();
            return START_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        disconnect();
    }

    @Override
    public boolean handleMessage(Message message) {
        Log.i(TAG, "handleMessage: " + getString(message.what) + ", disconnect_requested: " + disconnect_requested);
        if (message.what == R.string.disconnected && !disconnect_requested) {
            Log.i(TAG, "Reconnecting . . .");
            connect();
            return true;
        }

        Toast.makeText(this, message.what, Toast.LENGTH_SHORT).show();
        if (message.what != R.string.disconnected) {
            updateForegroundNotification(message.what);
        }

        Activity currentActivity = ((MyApp)this.getApplicationContext()).getCurrentActivity();
        if (currentActivity != null) {
            ((VpnClient) currentActivity).handleMessage(message);
        } else {
            Log.e(TAG, "Could not find activity object");
        }

        return true;
    }

    String server;
    byte[] secret;
    int port;

    private void connect() {
        // Become a foreground service. Background services can be VPN services too, but they can
        // be killed by background check before getting a chance to receive onRevoke().
        updateForegroundNotification(R.string.connecting);
        mHandler.sendEmptyMessage(R.string.connecting);

        Activity currentActivity = ((MyApp)this.getApplicationContext()).getCurrentActivity();
        VpnClient client = ((VpnClient) currentActivity);
        if (client != null) {
            server = client.getSelectedServer().address;
            secret = client.getSelectedServer().secret.getBytes();
            port = client.getSelectedServer().port;

        } else {
            server = getString(R.string.connection_address);
            secret = getString(R.string.connection_secret).getBytes();
            try {
                port = Integer.parseInt(getString(R.string.connection_port));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Bad port: " + getString(R.string.connection_port), e);
                return;
            }
        }


        // Extract information from the shared preferences.
//        final SharedPreferences prefs = getSharedPreferences(VpnClient.Prefs.NAME, MODE_PRIVATE);

        // Kick off a connection.
        startConnection(new VpnConnection(
                this, mNextConnectionId.getAndIncrement(), server, port, secret));
    }

    private void startConnection(final VpnConnection connection) {
        // Replace any existing connecting thread with the  new one.
        final Thread thread = new Thread(connection, "ToyVpnThread");
        setConnectingThread(thread);

        // Handler to mark as connected once onEstablish is called.
        connection.setConfigureIntent(mConfigureIntent);
        connection.setOnEstablishListener(new VpnConnection.OnEstablishListener() {
            public void onEstablish(ParcelFileDescriptor tunInterface) {
                mHandler.sendEmptyMessage(R.string.connected);

                mConnectingThread.compareAndSet(thread, null);
                setConnection(new Connection(thread, tunInterface));
            }
            public void onDisconnected() {
                mHandler.sendEmptyMessage(R.string.disconnected);
            }
        });
        thread.start();
    }

    private void setConnectingThread(final Thread thread) {
        final Thread oldThread = mConnectingThread.getAndSet(thread);
        if (oldThread != null) {
            oldThread.interrupt();
        }
    }

    private void setConnection(final Connection connection) {
        final Connection oldConnection = mConnection.getAndSet(connection);
        if (oldConnection != null) {
            try {
                oldConnection.first.interrupt();
                oldConnection.second.close();
            } catch (IOException e) {
                Log.e(TAG, "Closing VPN interface", e);
            }
        }
    }

    private void disconnect() {
        mHandler.sendEmptyMessage(R.string.disconnected);
        setConnectingThread(null);
        setConnection(null);
        stopForeground(true);
    }

    private void updateForegroundNotification(final int message) {
        startForeground(1, new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_vpn)
                .setContentText(getString(message))
                .setContentIntent(mConfigureIntent)
                .build());
    }
}
