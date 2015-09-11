package com.opentok.android.meet;

import java.util.ArrayList;
import java.util.HashMap;

import android.content.Context;
import android.os.Handler;
import android.provider.Telephony;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.OpentokError;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.profiler.PerformanceProfiler;

import meet.android.opentok.com.opentokmeet.R;

public class Room extends Session implements PerformanceProfiler.CPUStatListener, PerformanceProfiler.MemStatListener {

    private static final String LOGTAG = "opentok-meet-room";

    private Context mContext;

    private String apikey;
    private String sessionId;
    private String token;

    private Publisher mPublisher;
    private Participant mLastParticipant;
    private int mCurrentPosition;
    private String mPublisherName = null;
    private HashMap<Stream, Participant> mParticipantStream = new HashMap<Stream, Participant>();
    private HashMap<String, Participant> mParticipantConnection
            = new HashMap<String, Participant>();
     private ArrayList<Participant> mParticipants = new ArrayList<Participant>();

    private ViewGroup mPreview;
    private TextView mMessageView;
    private ScrollView mMessageScroll;
    private LinearLayout mParticipantsViewContainer;

    private ViewGroup mLastParticipantView;
    private OnClickListener onSubscriberUIClick;

    private Handler mHandler;

    private ChatRoomActivity mActivity;

    PerformanceProfiler mProfiler;

    public Room(Context context, String roomName, String sessionId, String token, String apiKey,
                String username) {
        super(context, apiKey, sessionId);
        this.apikey = apiKey;
        this.sessionId = sessionId;
        this.token = token;
        this.mContext = context;
        this.mPublisherName = username;
        this.mHandler = new Handler(context.getMainLooper());
        this.mActivity = (ChatRoomActivity) this.mContext;

        mProfiler = new PerformanceProfiler(mContext);
        mProfiler.setCPUListener(this);
        mProfiler.setMemoryStatListener(this);
    }

    public void setParticipantsViewContainer(LinearLayout container, ViewGroup lastParticipantView,
                                             OnClickListener onSubscriberUIClick) {
        this.mParticipantsViewContainer = container;
        this.mLastParticipantView = lastParticipantView;
        this.onSubscriberUIClick = onSubscriberUIClick;
    }

    public void setMessageView(TextView et, ScrollView scroller) {
        this.mMessageView = et;
        this.mMessageScroll = scroller;
    }

    public void setPreviewView(ViewGroup preview) {
        this.mPreview = preview;
    }

    public void connect() {
        this.connect(token);
    }



    public Publisher getPublisher() {
        return mPublisher;
    }

    public Participant getLastParticipant() {
        return mLastParticipant;
    }

    public ArrayList<Participant> getParticipants() {
        return mParticipants;
    }

    public LinearLayout getParticipantsViewContainer() {
        return mParticipantsViewContainer;
    }

    public ViewGroup getLastParticipantView() {
        return mLastParticipantView;
    }

    //Callbacks
    @Override
    public void onPause() {
        super.onPause();
        if (mPublisher != null) {
            mPreview.setVisibility(View.GONE);
        }

        stopGetMetrics();
    }

    @Override
    public void onResume() {
        super.onResume();
        mHandler.postDelayed(new Runnable() {

            @Override
            public void run() {
                if (mPublisher != null) {
                    mPreview.setVisibility(View.VISIBLE);
                    mPreview.removeView(mPublisher.getView());
                    RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                    mPreview.addView(mPublisher.getView(), lp);
                }
            }
        }, 500);
    }

    private float[][] getTemLayersAdj (){
        float tempLadj [][];
        tempLadj = new float[4][4];

        for(int i=0; i<4; i++) {

            for(int j=0; j<4; j++) {
                if (j == 0 ){
                    tempLadj[i][j] = 0.1f;
                }
                else {
                    tempLadj[i][j] = 1.0f;
                }

            }
        }
        return tempLadj;
    }
    private Publisher getSimulcastPublisher(){

        int simulcastLevel = mActivity.getSimulcastPub();
        Publisher publisher = null;

        switch (simulcastLevel) {
            case 0: {
                Log.d(LOGTAG, "Publisher simulcast is disabled");
                publisher = new Publisher(mContext, "Android", true, true, PublisherKit.PublisherKitSimulcastLevel.PublisherKitSimulcastLevelNone, 0, null);

                break;
            }
            case 1: {
                Log.d(LOGTAG, "Publisher simulcast: Default VGA");
                publisher = new Publisher(mContext, "Android", true, true, PublisherKit.PublisherKitSimulcastLevel.PublisherKitSimulcastLevelVGA, 0, null);
                break;
            }
            case 2: {
                Log.d(LOGTAG, "Publisher simulcast: Default 720p");
                publisher = new Publisher(mContext, "Android", true, true, PublisherKit.PublisherKitSimulcastLevel.PublisherKitSimulcastLevel720p, 0, null);
                break;
            }

            case 3: {
                Log.d(LOGTAG, "Publisher simulcast: Custom VGA");
                publisher = new Publisher(mContext, "Android", true, true, PublisherKit.PublisherKitSimulcastLevel.PublisherKitSimulcastLevelVGA, 1, getTemLayersAdj());
                break;
            }

            case 4: {
                Log.d(LOGTAG, "Publisher simulcast: Custom 720p");
                publisher = new Publisher(mContext, "Android", true, true, PublisherKit.PublisherKitSimulcastLevel.PublisherKitSimulcastLevel720p, 1, getTemLayersAdj());
                break;
            }
        }
        return publisher;
    }

    @Override
    protected void onConnected() {
        //check simulcast case for publisher

        //mPublisher = new Publisher(mContext, "Android");
        mPublisher = getSimulcastPublisher();
        mPublisher.setName(mPublisherName);
        mPublisher.setAudioFallbackEnabled(true);
        mPublisher.setPublisherListener(new PublisherKit.PublisherListener() {
            @Override
            public void onStreamCreated(PublisherKit publisherKit, Stream stream) {
                Log.d(LOGTAG, "onStreamCreated!!");
            }

            @Override
            public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {
                Log.d(LOGTAG, "onStreamDestroyed!!");
            }

            @Override
            public void onError(PublisherKit publisherKit, OpentokError opentokError) {
                Log.d(LOGTAG, "onError!!");
            }
        });
        publish(mPublisher);

        // Add video preview
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        SurfaceView v = (SurfaceView) mPublisher.getView();
        v.setZOrderOnTop(true);

        mPreview.addView(v, lp);
        mPublisher.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE,
                BaseVideoRenderer.STYLE_VIDEO_FILL);

        startGetMetrics();

    }

    @Override
    protected void onStreamReceived(Stream stream) {

        Participant p = new Participant(mContext, stream);

        //We can use connection data to obtain each user id
        p.setUserId(stream.getConnection().getData());

        if (mParticipants.size() > 0) {
            final Participant lastParticipant = mParticipants.get(mParticipants.size() - 1);
            this.mLastParticipantView.removeView(lastParticipant.getView());

            final LinearLayout.LayoutParams lp = mActivity.getQVGALayoutParams();
            lastParticipant.setPreferredResolution(Participant.QVGA_VIDEO_RESOLUTION);
            lastParticipant.setPreferredFrameRate(Participant.MID_FPS);
            this.mParticipantsViewContainer.addView(lastParticipant.getView(), lp);
            lastParticipant.setSubscribeToVideo(true);
            lastParticipant.getView().setOnLongClickListener(longClickListener);
            lastParticipant.getView().setOnClickListener(clickListener);
        }

        mActivity.getLoadingSub().setVisibility(View.VISIBLE);
        p.setPreferredResolution(Participant.VGA_VIDEO_RESOLUTION);
        p.setPreferredFrameRate(Participant.MAX_FPS);
        p.getView().setOnClickListener(clickLastParticipantListener);
        mLastParticipant = p;

        //Subscribe to this participant
        this.subscribe(p);

        mParticipants.add(p);
        p.getView().setTag(stream);

        mParticipantStream.put(stream, p);
        mParticipantConnection.put(stream.getConnection().getConnectionId(), p);
    }

    @Override
    protected void onStreamDropped(Stream stream) {
        Participant p = mParticipantStream.get(stream);
        if (p != null) {

            mParticipants.remove(p);
            mParticipantStream.remove(stream);
            mParticipantConnection.remove(stream.getConnection().getConnectionId());

            mLastParticipant = null;

            int index = mParticipantsViewContainer.indexOfChild(p.getView());
            if ( index != -1 ) {
                mParticipantsViewContainer.removeViewAt(index);
            }
            else {
                mLastParticipantView.removeView(p.getView());
                if (mParticipants.size() > 0 ) {
                    //add last participant to this view
                    Participant currentLast = mParticipants.get(mParticipants.size() - 1);
                    mParticipantsViewContainer.removeView(currentLast.getView());
                    mLastParticipantView.addView(currentLast.getView(), mActivity.getMainLayoutParams());
                }
            }
        }

    }


    @Override
    protected void onError(OpentokError error) {
        super.onError(error);
        Toast.makeText(this.mContext, error.getMessage(), Toast.LENGTH_SHORT).show();
    }

    public void loadSubscriberView() {
        //stop loading spinning
        if (mActivity.getLoadingSub().getVisibility() == View.VISIBLE) {
            mActivity.getLoadingSub().setVisibility(View.GONE);

            this.mLastParticipantView.addView(mLastParticipant.getView());
        }
    }

    private void swapSubPriority(View view){
        int index = this.mParticipantsViewContainer.indexOfChild(view);

        //the last participant view will go to the index
        this.mParticipantsViewContainer.removeView(view);
        this.mLastParticipantView.removeView(mLastParticipant.getView());

        //update lastParticipant view
        LinearLayout.LayoutParams lp = mActivity.getMainLayoutParams();
        Participant currentSelected = mParticipantStream.get(view.getTag());
        currentSelected.setPreferredResolution(Participant.VGA_VIDEO_RESOLUTION);
        currentSelected.setPreferredFrameRate(Participant.MAX_FPS);
        currentSelected.getView().setOnClickListener(clickLastParticipantListener);
        currentSelected.getView().setOnLongClickListener(null);
        this.mLastParticipantView.addView(currentSelected.getView(), lp);

        lp = mActivity.getQVGALayoutParams();
        mLastParticipant.getView().setOnClickListener(clickListener);
        mLastParticipant.getView().setOnLongClickListener(longClickListener);
        mLastParticipant.setPreferredResolution(Participant.QVGA_VIDEO_RESOLUTION);
        mLastParticipant.setPreferredFrameRate(Participant.MID_FPS);
        this.mParticipantsViewContainer.addView(mLastParticipant.getView(), index, lp);

        mLastParticipant = currentSelected;

    }

    View.OnLongClickListener longClickListener = new View.OnLongClickListener() {
        public boolean onLongClick(View view) {
            if (!view.equals(mLastParticipantView)){
                swapSubPriority(view);
            }

            return true;
        }
    };

    View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {

            Participant participant = mParticipantStream.get(view.getTag());

            boolean enableAudioOnly = participant.getSubscribeToVideo();

            if (enableAudioOnly) {
                participant.setSubscribeToVideo(false);
            } else {
                participant.setSubscribeToVideo(true);
            }
        int index = mParticipantsViewContainer.indexOfChild(participant.getView());
        if (index == -1) {
            index = (int) view.getId();
        }
        mActivity.setAudioOnlyViewListPartcipants(enableAudioOnly, participant, index, this);

        }
    };

    View.OnClickListener clickLastParticipantListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            boolean enableAudioOnly = mLastParticipant.getSubscribeToVideo();
            if (enableAudioOnly) {
                mLastParticipant.setSubscribeToVideo(false);
            } else {
                mLastParticipant.setSubscribeToVideo(true);
            }
            mActivity.setAudioOnlyViewLastParticipant(enableAudioOnly, mLastParticipant, this);
        }
    };


    private void startGetMetrics(){
       // mProfiler.startBatteryMetrics();

        //start cpu profiling
       // mProfiler.startCPUMetrics();

        //start mem profiling
        mProfiler.startMemMetrics();

    }

    private void stopGetMetrics(){
       // mProfiler.stopBatteryMetrics();

        //start cpu profiling
       // mProfiler.stopCPUMetrics();

        //start mem profiling
        mProfiler.stopMemMetrics();

    }

    @Override
    public void onCPU(float v, float v1) {
        Log.d("MARINAS", "cpu values total "+v + "process:" + v1);
    }

    @Override
    public void onMemoryStat(double v, double v1, double v2, double v3) {

    }
}
