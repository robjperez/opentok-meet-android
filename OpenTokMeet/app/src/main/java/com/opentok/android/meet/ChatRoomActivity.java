package com.opentok.android.meet;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;


import com.opentok.android.OpenTokConfig;
import com.opentok.android.meet.fragments.PublisherControlFragment;
import com.opentok.android.meet.services.ClearNotificationService;
import com.opentok.android.meet.services.ClearNotificationService.ClearBinder;

import meet.android.opentok.com.opentokmeet.BuildConfig;
import meet.android.opentok.com.opentokmeet.R;

import static meet.android.opentok.com.opentokmeet.R.color.black;


public class ChatRoomActivity extends Activity implements PublisherControlFragment.PublisherCallbacks {

    private static final String LOGTAG = "opentok-meet-chat-room";

    public static final String ARG_ROOM_ID = "roomId";
    public static final String ARG_USERNAME_ID = "usernameId";
    public static final String PUB_SIMULCAST = "PUB_SIMULCAST";

    private String serverURL = null;
    private String mRoomName;
    private Room mRoom;
    private String mUsername = null;

    private int mSimulcastPub = 0;

    private ProgressDialog mConnectingDialog;
    private AlertDialog mErrorDialog;

    protected Handler mHandler = new Handler();
    private NotificationCompat.Builder mNotifyBuilder;
    private NotificationManager mNotificationManager;
    private ServiceConnection mConnection;
    private boolean mIsBound = false;

    private ViewGroup mPreview;
    private ViewGroup mLastParticipantView;
    private LinearLayout mParticipantsView;
    private ProgressBar mLoadingSub; // Spinning wheel for loading subscriber view

    public ArrayList<String> statsInfo = new ArrayList<String>() ;

    protected PublisherControlFragment mPublisherFragment;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.chat_room_layout);

        //Custom title bar
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowCustomEnabled(true);

        View cView = getLayoutInflater().inflate(R.layout.custom_title, null);
        actionBar.setCustomView(cView);

        mPreview = (ViewGroup) findViewById(R.id.publisherview);
        mParticipantsView = (LinearLayout) findViewById(R.id.gallery);
        mLastParticipantView = (ViewGroup) findViewById(R.id.mainsubscriberView);
        mLoadingSub = (ProgressBar) findViewById(R.id.loadingSpinner);

        Uri url = getIntent().getData();
        serverURL = getResources().getString(R.string.serverURL);

        if (url == null) {
            mRoomName = getIntent().getStringExtra(ARG_ROOM_ID);
            mUsername = getIntent().getStringExtra(ARG_USERNAME_ID);
        } else {
            if (url.getScheme().equals("otmeet")) {
                mRoomName = url.getHost();
            } else {
                mRoomName = url.getPathSegments().get(0);
            }
        }

        TextView title = (TextView) findViewById(R.id.title);
        title.setText(mRoomName);

        statsInfo.add("CPU info stats are not available");
        statsInfo.add("Memory info stats are not available");
        statsInfo.add("Battery info stats are not available");

        mSimulcastPub = getIntent().getIntExtra(PUB_SIMULCAST, 0);
        if (savedInstanceState == null) {
            initPublisherFragment();
        }

        mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        initializeRoom();

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Remove publisher & subscriber views because we want to reuse them
        if (mRoom != null && mRoom.getParticipants().size() > 0) {
            mRoom.getParticipantsViewContainer()
                    .removeAllViews();
            mRoom.getLastParticipantView().removeAllViews();
        }
        reloadInterface();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        //Pause implies go to audio only mode
        if (mRoom != null) {
            mRoom.onPause();

            // Remove publisher & subscriber views because we want to reuse them
            if (mRoom != null && mRoom.getParticipants().size() > 0) {
                mRoom.getParticipantsViewContainer()
                        .removeAllViews();
                mRoom.getLastParticipantView().removeAllViews();
            }
        }

        //Add notification to status bar which gets removed if the user force kills the application.
        mNotifyBuilder = new NotificationCompat.Builder(this)
                .setContentTitle("Meet TokBox")
                .setContentText("Ongoing call")
                .setSmallIcon(R.mipmap.ic_launcher).setOngoing(true);

        Intent notificationIntent = new Intent(this, ChatRoomActivity.class);
        notificationIntent
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notificationIntent.putExtra(ChatRoomActivity.ARG_ROOM_ID, mRoomName);
        PendingIntent intent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        mNotifyBuilder.setContentIntent(intent);

        //Creates a service which removes the notification after application is forced closed.
        if (mConnection == null) {
            mConnection = new ServiceConnection() {

                public void onServiceConnected(ComponentName className, IBinder binder) {
                    ((ClearBinder) binder).service.startService(
                            new Intent(ChatRoomActivity.this, ClearNotificationService.class));
                    NotificationManager mNotificationManager
                            = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    mNotificationManager.notify(ClearNotificationService.NOTIFICATION_ID,
                            mNotifyBuilder.build());
                }

                public void onServiceDisconnected(ComponentName className) {
                    mConnection = null;
                }

            };
        }
        if (!mIsBound) {
            bindService(new Intent(ChatRoomActivity.this,
                            ClearNotificationService.class), mConnection,
                    Context.BIND_AUTO_CREATE);
            mIsBound = true;
            startService(notificationIntent);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        super.onResume();
        //Resume implies restore video mode if it was enable before pausing app

        //If service is binded remove it, so that the next time onPause can bind service.
        if (mIsBound) {
            unbindService(mConnection);
            stopService(new Intent(ClearNotificationService.MY_SERVICE));
            mIsBound = false;
        }

        if (mRoom != null) {
            mRoom.onResume();
        }

        mNotificationManager.cancel(ClearNotificationService.NOTIFICATION_ID);
        reloadInterface();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mIsBound) {
            unbindService(mConnection);
            mIsBound = false;
        }

        if (this.isFinishing()) {
            mNotificationManager.cancel(ClearNotificationService.NOTIFICATION_ID);
            if (mRoom != null) {
                mRoom.disconnect();
            }
        }
    }

    @Override
    public void onDestroy() {
        mNotificationManager.cancel(ClearNotificationService.NOTIFICATION_ID);

        if (mIsBound) {
            unbindService(mConnection);
            mIsBound = false;
        }

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    public void reloadInterface() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {

                if (mRoom != null && mRoom.getParticipants().size() > 0) {
                    for(int i = 0; i< mRoom.getParticipants().size()-1; i++) {
                        mRoom.getParticipantsViewContainer()
                                .addView(mRoom.getParticipants().get(i).getView());
                    }
                    mRoom.getLastParticipantView().addView(mRoom.getLastParticipant().getView());
                }
            }
        }, 500);
    }

    private void initializeRoom() {
        Log.i(LOGTAG, "initializing chat room fragment for room: " + mRoomName);
        setTitle(mRoomName);

        //Show connecting dialog
        mConnectingDialog = new ProgressDialog(this);
        mConnectingDialog.setTitle("Joining Room...");
        mConnectingDialog.setMessage("Please wait.");
        mConnectingDialog.setCancelable(false);
        mConnectingDialog.setIndeterminate(true);
        mConnectingDialog.show();

        GetRoomDataTask task = new GetRoomDataTask();
        task.execute(mRoomName, mUsername);
    }

    private class GetRoomDataTask extends AsyncTask<String, Void, Room> {

        protected HttpClient mHttpClient;

        protected HttpGet mHttpGet;

        protected boolean mDidCompleteSuccessfully;

        public GetRoomDataTask() {
            mHttpClient = new DefaultHttpClient();
        }

        @Override
        protected Room doInBackground(String... params) {
            String sessionId = null;
            String token = null;
            String apiKey = null;
            initializeGetRequest(params[0]);
            try {
                HttpResponse roomResponse = mHttpClient.execute(mHttpGet);
                HttpEntity roomEntity = roomResponse.getEntity();
                String temp = EntityUtils.toString(roomEntity);
                Log.i(LOGTAG, "retrieved room response: " + temp);
                JSONObject roomJson = new JSONObject(temp);
                sessionId = roomJson.getString("sessionId");
                token = roomJson.getString("token");
                apiKey = roomJson.getString("apiKey");
                mDidCompleteSuccessfully = true;
            } catch (Exception exception) {
                Log.e(LOGTAG,
                        "could not get room data: " + exception.getMessage());
                mDidCompleteSuccessfully = false;
                return null;
            }

            try {
                OpenTokConfig.setAPIRootURL(BuildConfig.MEET_ENVIRONMENT, true);
               // OpenTokConfig.setOTKitLogs(true);
               // OpenTokConfig.setJNILogs(true);
                //OpenTokConfig.setWebRTCLogs(true);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }

            return new Room(ChatRoomActivity.this, params[0], sessionId, token,
                    apiKey, params[1]);
        }

        @Override
        protected void onPostExecute(final Room room) {
            if (mDidCompleteSuccessfully) {
                mConnectingDialog.dismiss();
                mRoom = room;
                mRoom.setPreviewView(mPreview);
                mRoom.setParticipantsViewContainer(mParticipantsView, mLastParticipantView, null);
                mRoom.setMessageView((TextView) findViewById(R.id.messageView),
                        (ScrollView) findViewById(R.id.scroller));
                mRoom.connect();
            } else {
                mConnectingDialog.dismiss();
                mConnectingDialog = null;
                showErrorDialog();
            }
        }

        protected void initializeGetRequest(String room) {
            URI roomURI;
            URL url;

            String urlStr = getResources().getString(R.string.serverURL) + room;
            try {
                url = new URL(urlStr);
                roomURI = new URI(url.getProtocol(), url.getUserInfo(),
                        url.getHost(), url.getPort(), url.getPath(),
                        url.getQuery(), url.getRef());
            } catch (URISyntaxException exception) {
                Log.e(LOGTAG,
                        "the room URI is malformed: " + exception.getMessage());
                return;
            } catch (MalformedURLException exception) {
                Log.e(LOGTAG,
                        "the room URI is malformed: " + exception.getMessage());
                return;
            }
            mHttpGet = new HttpGet(roomURI);
            mHttpGet.addHeader("Accept", "application/json, text/plain, */*");
        }
    }

    private DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {
            finish();
        }
    };

    private void showErrorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.error_title);
        builder.setMessage(R.string.error);
        builder.setCancelable(false);
        builder.setPositiveButton("OK", errorListener);
        mErrorDialog = builder.create();
        mErrorDialog.show();
    }

    public void onClickShareLink(View v) {
        String roomUrl = serverURL + mRoomName;
        String text = getString(R.string.sharingLink) + " " + roomUrl;
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        sendIntent.setType("text/plain");
        startActivity(sendIntent);
    }

    public Room getRoom() {
        return mRoom;
    }

    public Handler getHandler() {
        return this.mHandler;
    }

    public void updateLoadingSub() {
        mRoom.loadSubscriberView();
    }

    //Show audio only icon when video quality changed and it is disabled for the last subscriber
    public void setAudioOnlyViewLastParticipant(boolean audioOnlyEnabled, Participant participant, View.OnClickListener clickLastParticipantListener) {
        boolean subscriberVideoOnly = audioOnlyEnabled;

        if (audioOnlyEnabled) {
            this.mRoom.getLastParticipantView().removeView(participant.getView());
            View audioOnlyView = getAudioOnlyIcon();
            this.mRoom.getLastParticipantView().addView(audioOnlyView);
            audioOnlyView.setOnClickListener(clickLastParticipantListener);
            //TODO add audiometer
        } else {
            if (!subscriberVideoOnly) {
                this.mRoom.getLastParticipantView().removeAllViews();
                this.mRoom.getLastParticipantView().addView(participant.getView());
            }
        }
    }

    public void setAudioOnlyViewListPartcipants (boolean audioOnlyEnabled, Participant participant, int index , View.OnClickListener clickListener) {

        final LinearLayout.LayoutParams lp = getQVGALayoutParams();

        if (audioOnlyEnabled) {
            this.mRoom.getParticipantsViewContainer().removeViewAt(index);
            View audioOnlyView = getAudioOnlyIcon();
            audioOnlyView.setTag(participant.getStream());
            audioOnlyView.setId(index);
            audioOnlyView.setOnClickListener(clickListener);
            this.mRoom.getParticipantsViewContainer().addView(audioOnlyView, index, lp);

        } else {
            this.mRoom.getParticipantsViewContainer().removeViewAt(index);
            this.mRoom.getParticipantsViewContainer().addView(participant.getView(), index, lp);

        }

    }

    public ProgressBar getLoadingSub() {
        return mLoadingSub;
    }

    //Convert dp to real pixels, according to the screen density.
    public int dpToPx(int dp) {
        double screenDensity = this.getResources().getDisplayMetrics().density;
        return (int) (screenDensity * (double) dp);
    }

    public ImageView getAudioOnlyIcon() {

        ImageView imageView = new ImageView(this);

        imageView.setLayoutParams(new GridView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        imageView.setBackgroundResource(R.drawable.avatar_borders);
        imageView.setImageResource(R.mipmap.avatar);

        return imageView;
    }

    protected LinearLayout.LayoutParams getVGALayoutParams(){
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                640, 480);
        return lp;
    }

    protected LinearLayout.LayoutParams getQVGALayoutParams(){
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                480, 320);
        return lp;
    }

    protected LinearLayout.LayoutParams getMainLayoutParams(){
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        return lp;
    }

    //Initialize fragments
    public void initPublisherFragment() {
        mPublisherFragment = new PublisherControlFragment();
        getFragmentManager().beginTransaction()
                .add(R.id.fragment_pub_container, mPublisherFragment)
                .commit();
    }

    @Override
    public void onMutePublisher() {
        if (mRoom.getPublisher() != null) {
            mRoom.getPublisher().setPublishAudio(
                    !mRoom.getPublisher().getPublishAudio());
        }
    }

    @Override
    public void onSwapCamera() {
        if (mRoom.getPublisher() != null) {
            mRoom.getPublisher().swapCamera();
        }
    }

    @Override
    public void onEndCall() {
        finish();
    }

    public View.OnLongClickListener onPubStatusClick = new View.OnLongClickListener() {

        @Override
        public boolean onLongClick(View v) {
            boolean visible = false;

            if (mRoom.getPublisher() != null) {
                // check visibility of bars
                if (!mPublisherFragment.isPublisherWidgetVisible()) {
                    visible = true;
                }
                mPublisherFragment.publisherClick();
            }
            return visible;
        }

    };


    public View.OnClickListener onPubViewClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            if (mRoom.getPublisher() != null) {
                if (mRoom.getPublisher().getPublishVideo()) {
                    mRoom.getPublisher().setPublishVideo(false);
                    View audioOnlyView = getAudioOnlyIcon();
                    audioOnlyView.setOnClickListener(this);
                    audioOnlyView.setOnLongClickListener(onPubStatusClick);
                    mPreview.removeAllViews();
                    mPreview.addView(audioOnlyView);
                }
                else {
                    mRoom.getPublisher().setPublishVideo(true);
                    mRoom.getPublisher().getView().setOnClickListener(this);
                    mRoom.getPublisher().getView().setOnLongClickListener(onPubStatusClick);
                    mPreview.addView(mRoom.getPublisher().getView());
                }

                }
            }

    };

    public void onStatsInfoClick(View v) {

            new AlertDialog.Builder(ChatRoomActivity.this)
                    .setTitle("Stats info")
                    .setMessage(statsInfo.get(0) + "\n" + statsInfo.get(1) + "\n" +statsInfo.get(2))
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // continue with delete
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
    }

    public int getSimulcastPub() {
        return mSimulcastPub;
    }

}