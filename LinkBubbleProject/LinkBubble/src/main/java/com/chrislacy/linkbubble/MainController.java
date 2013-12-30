package com.chrislacy.linkbubble;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Choreographer;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.widget.Toast;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.net.MalformedURLException;
import java.util.List;
import java.util.Vector;

/**
 * Created by gw on 2/10/13.
 */
public class MainController implements Choreographer.FrameCallback {

    private static final String TAG = "MainController";

    private static MainController sInstance;
    private static ContentActivity sContentActivity;

    public static MainController get() {
        return sInstance;
    }

    public static void create(Context context, EventHandler eventHandler) {
        if (sInstance != null) {
            new RuntimeException("Only one instance of MainController allowed at any one time");
        }
        sInstance = new MainController(context, eventHandler);
    }

    public static void destroy() {
        if (sInstance == null) {
            new RuntimeException("No instance to destroy");
        }

        MainApplication app = (MainApplication) sInstance.mContext.getApplicationContext();
        Bus bus = app.getBus();
        bus.unregister(sInstance);

        //mWindowManager.removeView(mTextView);
        sInstance.mCanvas.destroy();
        sInstance.mChoreographer.removeFrameCallback(sInstance);
        sInstance.endAppPolling();
        sInstance = null;
    }

    public interface EventHandler {
        public void onDestroy();
    }

    public State_BubbleView STATE_BubbleView;
    public State_SnapToEdge STATE_SnapToEdge;
    public State_AnimateToContentView STATE_AnimateToContentView;
    public State_ContentView STATE_ContentView;
    public State_AnimateToBubbleView STATE_AnimateToBubbleView;
    public State_Flick_ContentView STATE_Flick_ContentView;
    public State_Flick_BubbleView STATE_Flick_BubbleView;
    public State_KillBubble STATE_KillBubble;

    private ControllerState mCurrentState;
    private EventHandler mEventHandler;
    private int mBubblesLoaded;
    private AppPoller mAppPoller;

    private Context mContext;
    private Choreographer mChoreographer;
    private boolean mUpdateScheduled;
    private static Vector<Bubble> mBubbles = new Vector<Bubble>();
    private Canvas mCanvas;
    private Badge mBadge;
    private Bubble mFrontBubble;

    private MainController(Context context, EventHandler eventHandler) {
        Util.Assert(sInstance == null);
        sInstance = this;
        mContext = context;
        mEventHandler = eventHandler;

        mAppPoller = new AppPoller(context);
        mAppPoller.setListener(mAppPollerListener);

        /*
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mTextView = new TextView(mContext);
        mTextView.setTextColor(0xff00ffff);
        mTextView.setTextSize(32.0f);
        mWindowManagerParams.gravity = Gravity.TOP | Gravity.LEFT;
        mWindowManagerParams.x = 500;
        mWindowManagerParams.y = 16;
        mWindowManagerParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowManagerParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowManagerParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        mWindowManagerParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        mWindowManagerParams.format = PixelFormat.TRANSPARENT;
        mWindowManagerParams.setTitle("LinkBubble: Debug Text");
        mWindowManager.addView(mTextView, mWindowManagerParams);*/

        mUpdateScheduled = false;
        mChoreographer = Choreographer.getInstance();
        mCanvas = new Canvas(mContext);
        mBadge = new Badge(mContext);

        MainApplication app = (MainApplication) mContext.getApplicationContext();
        Bus bus = app.getBus();
        bus.register(this);

        STATE_BubbleView = new State_BubbleView(mCanvas, mBadge);
        STATE_SnapToEdge = new State_SnapToEdge(mCanvas);
        STATE_AnimateToContentView = new State_AnimateToContentView(mCanvas);
        STATE_ContentView = new State_ContentView(mCanvas);
        STATE_AnimateToBubbleView = new State_AnimateToBubbleView(mCanvas);
        STATE_Flick_ContentView = new State_Flick_ContentView(mCanvas);
        STATE_Flick_BubbleView = new State_Flick_BubbleView(mCanvas);
        STATE_KillBubble = new State_KillBubble(mCanvas);

        updateIncognitoMode(Settings.get().isIncognitoMode());
        switchState(STATE_BubbleView);
    }

    private void doTargetAction(Config.BubbleAction action, String url) {

        switch (action) {
            case ConsumeRight:
            case ConsumeLeft: {
                MainApplication.handleBubbleAction(mContext, action, url);
                break;
            }
            default:
                break;
        }
    }

    //private TextView mTextView;
    //private WindowManager mWindowManager;
    //private WindowManager.LayoutParams mWindowManagerParams = new WindowManager.LayoutParams();
    //private int mFrameNumber;

    public void onPageLoaded(Bubble bubble) {
        mCurrentState.OnPageLoaded(bubble);
    }

    public boolean destroyBubble(Bubble bubble, Config.BubbleAction action) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean debug = prefs.getBoolean("debug_flick", true);

        if (debug) {
            Toast.makeText(mContext, "HIT TARGET!", 400).show();
        } else {
            String url = bubble.getUrl().toString();

            bubble.destroy();

            //int bubbleCount = mBubbles.size();
            int bubbleIndex = mBubbles.indexOf(bubble);
            Util.Assert(bubbleIndex >= 0 && bubbleIndex < mBubbles.size());

            /*if (bubbleCount > 3) {
                while (bubbleIndex + 2 < mBubbles.size()) {
                    Collections.swap(mBubbles, bubbleIndex, bubbleIndex+2);
                    bubbleIndex += 2;
                }
            }*/
            mBubbles.remove(bubble);
            mBubbles.remove(bubble);

            Settings.get().saveCurrentBubbles(mBubbles);

            for (int i=0 ; i < mBubbles.size() ; ++i) {
                mBubbles.get(i).setBubbleIndex(i);
            }

            if (mBubbles.size() > 0) {
                int nextBubbleIndex = Util.clamp(0, bubbleIndex, mBubbles.size()-1);
                Bubble nextBubble = mBubbles.get(nextBubbleIndex);
                mFrontBubble = nextBubble;
                mBadge.attach(nextBubble);
                mBadge.setBubbleCount(mBubbles.size());

                nextBubble.setVisibility(View.VISIBLE);
            } else {
                hideContentActivity();
                mBadge.attach(null);
                mFrontBubble = null;

                Config.BUBBLE_HOME_X = Config.mBubbleSnapLeftX;
                Config.BUBBLE_HOME_Y = (int) (Config.mScreenHeight * 0.4f);
            }

            mCurrentState.OnDestroyBubble(bubble);

            doTargetAction(action, url);
        }

        return mBubbles.size() > 0;
    }

    public void setAllBubblePositions(Bubble ref) {
        if (ref != null) {
            // Force all bubbles to be where the moved one ended up
            int bubbleCount = mBubbles.size();
            for (int i=0 ; i < bubbleCount ; ++i) {
                Bubble b = mBubbles.get(i);
                if (b != ref) {
                    b.setExactPos(ref.getXPos(), ref.getYPos());
                }
            }
        }
    }

    public void updateIncognitoMode(boolean incognito) {
        CookieSyncManager.createInstance(mContext);
        CookieManager.getInstance().setAcceptCookie(!incognito);

        for (int i=0 ; i < mBubbles.size() ; ++i) {
            mBubbles.get(i).updateIncognitoMode(incognito);
        }
    }

    @Subscribe
    public void onIncognitoModeChanged(SettingsFragment.IncognitoModeChangedEvent event) {
        updateIncognitoMode(event.mIncognito);
    }

    public void scheduleUpdate() {
        if (!mUpdateScheduled) {
            mUpdateScheduled = true;
            mChoreographer.postFrameCallback(this);
        }
    }

    public void switchState(ControllerState newState) {
        //Util.Assert(newState != sMainController.mCurrentState);
        if (mCurrentState != null) {
            mCurrentState.OnExitState();
        }
        mCurrentState = newState;
        mCurrentState.OnEnterState();
        scheduleUpdate();
    }

    public int getBubbleCount() {
        return mBubbles.size();
    }

    public Bubble getBubble(int index) {
        return mBubbles.get(index);
    }

    public List<Bubble> getBubbles() {
        return mBubbles;
    }

    public void doFrame(long frameTimeNanos) {
        mUpdateScheduled = false;

        float dt = 1.0f / 60.0f;

        int bubbleCount = mBubbles.size();
        for (int i=0 ; i < bubbleCount ; ++i) {
            Bubble b = mBubbles.get(i);
            b.update(dt, mCurrentState == STATE_ContentView);
        }

        Bubble frontBubble = null;
        if (mBubbles.size() > 0) {
            frontBubble =  getActiveBubble();
        }
        mCanvas.update(dt, frontBubble);

        if (mCurrentState.OnUpdate(dt)) {
            scheduleUpdate();
        }

        //mTextView.setText("S=" + mCurrentState.getName() + " F=" + mFrameNumber++);

        if (mCurrentState == STATE_BubbleView && mBubbles.size() == 0 &&
                mBubblesLoaded > 0 && !mUpdateScheduled) {
            mEventHandler.onDestroy();
        }
    }

    public void updateBackgroundColor(int color) {
        if (sContentActivity != null) {
            sContentActivity.updateBackgroundColor(color);
        }
    }

    private void showContentActivity() {
        if (sContentActivity == null && Config.USE_CONTENT_ACTIVITY) {
            Intent intent = new Intent(mContext, ContentActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            mContext.startActivity(intent);
        }
    }

    public void hideContentActivity() {
        if (sContentActivity != null) {
            long startTime = System.currentTimeMillis();
            sContentActivity.finish();
            long endTime = System.currentTimeMillis();
            Log.d(TAG, "sContentActivity.finish() time=" + (endTime - startTime));
            sContentActivity = null;
        }
    }

    public void onCloseSystemDialogs() {
        if (mCurrentState != null) {
            mCurrentState.OnCloseDialog();
            switchState(STATE_AnimateToBubbleView);
        }
    }

    public void onOrientationChanged() {
        Config.init(mContext);
        mCanvas.onOrientationChanged();
        boolean contentView = mCurrentState.OnOrientationChanged();
        for (int i=0 ; i < mBubbles.size() ; ++i) {
            mBubbles.get(i).OnOrientationChanged(contentView);
        }
    }

    public Bubble getActiveBubble() {
        Util.Assert(mFrontBubble != null);
        return mFrontBubble;
    }

    public void setActiveBubble(Bubble b) {
        mFrontBubble = b;
        Util.Assert(mFrontBubble != null);
    }

    public void onOpenUrl(final String url, long startTime) {
        if (Settings.get().redirectUrlToBrowser(url)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (MainApplication.loadInBrowser(mContext, intent, false)) {
                if (mBubbles.size() == 0) {
                    mEventHandler.onDestroy();
                }
                return;
            }
        }

        final List<ResolveInfo> resolveInfos = Settings.get().getAppsThatHandleUrl(url);
        if (resolveInfos != null && resolveInfos.size() > 0 && Settings.get().getAutoContentDisplayAppRedirect()) {
            if (resolveInfos.size() == 1) {
                ResolveInfo resolveInfo = resolveInfos.get(0);
                if (resolveInfo != Settings.get().mLinkBubbleEntryActivityResolveInfo
                    && MainApplication.loadResolveInfoIntent(mContext, resolveInfo, url, startTime)) {
                    if (mBubbles.size() == 0) {
                        mEventHandler.onDestroy();
                    }
                    return;
                }
            } else {
                AlertDialog dialog = ActionItem.getActionItemPickerAlert(mContext, resolveInfos, R.string.pick_default_app,
                        new ActionItem.OnActionItemSelectedListener() {
                            @Override
                            public void onSelected(ActionItem actionItem) {
                                boolean loaded = false;
                                String appPackageName = mContext.getPackageName();
                                for (ResolveInfo resolveInfo : resolveInfos) {
                                    if (resolveInfo.activityInfo.packageName.equals(actionItem.mPackageName)
                                            && resolveInfo.activityInfo.name.equals(actionItem.mActivityClassName)) {
                                        Settings.get().setDefaultApp(url, resolveInfo);

                                        // Jump out of the loop and load directly via a Bubble below
                                        if (resolveInfo.activityInfo.packageName.equals(appPackageName)) {
                                            break;
                                        }

                                        loaded = MainApplication.loadIntent(mContext, actionItem.mPackageName,
                                                actionItem.mActivityClassName, url, -1);
                                        break;
                                    }
                                }

                                if (loaded == false) {
                                    openUrlInBubble(url, System.currentTimeMillis());
                                }
                            }
                        });
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                dialog.show();
                if (mBubbles.size() == 0) {
                    mEventHandler.onDestroy();
                }
                return;
            }
        }

        openUrlInBubble(url, startTime);
    }

    private void openUrlInBubble(String url, long startTime) {
        if (mBubbles.size() < Config.MAX_BUBBLES) {

            int x, targetX, y, targetY;
            float time;

            int bubbleIndex = mBubbles.size();

            if (mCurrentState == STATE_ContentView) {
                x = (int) Config.getContentViewX(bubbleIndex, MainController.get().getBubbleCount()+1);
                y = (int) -Config.mBubbleHeight;
                targetX = x;
                targetY = Config.mContentViewBubbleY;
                time = 0.4f;
            } else {
                if (bubbleIndex == 0) {
                    x = (int) (Config.mBubbleSnapLeftX - Config.mBubbleWidth);
                    y = Config.BUBBLE_HOME_Y;
                    targetX = Config.BUBBLE_HOME_X;
                    targetY = y;
                    time = 0.4f;
                } else {
                    x = Config.BUBBLE_HOME_X;
                    y = Config.BUBBLE_HOME_Y;
                    targetX = x;
                    targetY = y;
                    time = 0.0f;
                }
            }

            Bubble bubble = null;
            try {
                bubble = new Bubble(mContext, url, x, y, targetX, targetY, time, startTime,
                        new Bubble.EventHandler() {
                    @Override
                    public void onMotionEvent_Touch(Bubble sender, Bubble.TouchEvent e) {
                        mCurrentState.OnMotionEvent_Touch(sender, e);
                        showContentActivity();
                    }

                    @Override
                    public void onMotionEvent_Move(Bubble sender, Bubble.MoveEvent e) {
                        mCurrentState.OnMotionEvent_Move(sender, e);
                    }

                    @Override
                    public void onMotionEvent_Release(Bubble sender, Bubble.ReleaseEvent e) {
                        mCurrentState.OnMotionEvent_Release(sender, e);
                        if (mCurrentState instanceof State_SnapToEdge) {
                            hideContentActivity();
                        }
                    }

                    @Override
                    public void onSharedLink(Bubble sender) {
                        if (mBubbles.size() > 1) {
                            int bubbleIndex = sender.getBubbleIndex();
                            destroyBubble(sender, Config.BubbleAction.Destroy);
                            int nextBubbleIndex = Util.clamp(0, bubbleIndex, mBubbles.size()-1);
                            Bubble nextBubble = mBubbles.get(nextBubbleIndex);
                            STATE_ContentView.setActiveBubble(nextBubble);
                        } else {
                            STATE_KillBubble.init(sender);
                            switchState(STATE_KillBubble);
                        }
                    }
                });
            } catch (MalformedURLException e) {
                // TODO: Inform the user somehow?
                return;
            }

            mCurrentState.OnNewBubble(bubble);
            mBubbles.add(bubble);
            ++mBubblesLoaded;

            for (int i=0 ; i < mBubbles.size() ; ++i) {
                mBubbles.get(i).setBubbleIndex(i);
            }

            Settings.get().saveCurrentBubbles(mBubbles);

            int bubbleCount = mBubbles.size();

            mBadge.attach(bubble);
            mBadge.setBubbleCount(bubbleCount);

            if (mCurrentState == STATE_ContentView) {
                bubble.setVisibility(View.VISIBLE);
                for (int i=0 ; i < bubbleCount ; ++i) {
                    Bubble b = mBubbles.get(i);
                    if (b != bubble) {
                        b.setTargetPos((int)Config.getContentViewX(b.getBubbleIndex(), MainController.get().getBubbleCount()), b.getYPos(), 0.2f, false);
                    }
                }
            } else {
                mFrontBubble = bubble;
                for (int i=0 ; i < bubbleCount ; ++i) {
                    Bubble b = mBubbles.get(i);
                    int vis = View.VISIBLE;
                    if (b != mFrontBubble)
                        vis = View.GONE;
                    b.setVisibility(vis);
                }
            }
        }
    }

    void beginAppPolling() {
        mAppPoller.beginAppPolling();
    }

    void endAppPolling() {
        mAppPoller.endAppPolling();
    }

    AppPoller.AppPollerListener mAppPollerListener = new AppPoller.AppPollerListener() {
        @Override
        public void onAppChanged() {
            if (mCurrentState != null && mCurrentState instanceof State_AnimateToBubbleView == false) {
                switchState(STATE_AnimateToBubbleView);
            }
        }
    };

    boolean showPreviousBubble() {
        if (mCurrentState instanceof State_ContentView) {
            State_ContentView contentViewState = (State_ContentView)mCurrentState;
            Bubble activeBubble = getActiveBubble();
            if (activeBubble != null) {
                int index = activeBubble.getBubbleIndex();
                if (index > 0) {
                    for (Bubble bubble : mBubbles) {
                        if (index-1 == bubble.getBubbleIndex()) {
                            contentViewState.setActiveBubble(bubble);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    boolean showNextBubble() {
        if (mCurrentState instanceof State_ContentView) {
            State_ContentView contentViewState = (State_ContentView)mCurrentState;
            Bubble activeBubble = getActiveBubble();
            if (activeBubble != null) {
                int index = activeBubble.getBubbleIndex();
                for (Bubble bubble : mBubbles) {
                    if (index+1 == bubble.getBubbleIndex()) {
                        contentViewState.setActiveBubble(bubble);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onContentActivityResumed(ContentActivity.ContentActivityResumedEvent event) {
        sContentActivity = event.mActivity;
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onContentActivityPaused(ContentActivity.ContentActivityPausedEvent event) {
        sContentActivity = null;
    }
}
