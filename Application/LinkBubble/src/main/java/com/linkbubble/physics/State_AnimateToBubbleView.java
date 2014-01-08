package com.linkbubble.physics;

import android.view.animation.OvershootInterpolator;
import com.linkbubble.ui.BubbleView;
import com.linkbubble.ui.CanvasView;
import com.linkbubble.Config;
import com.linkbubble.MainController;
import com.linkbubble.util.Util;

import java.util.Vector;

/**
 * Created by gw on 18/11/13.
 */
public class State_AnimateToBubbleView extends ControllerState {

    private class BubbleInfo {
        public float mPosX;
        public float mPosY;
        public float mDistanceX;
        public float mDistanceY;
        public float mTargetX;
        public float mTargetY;
    }

    private CanvasView mCanvasView;
    private OvershootInterpolator mInterpolator = new OvershootInterpolator(0.5f);
    private float mTime;
    private float mBubblePeriod;
    private float mContentPeriod;
    private Vector<BubbleInfo> mBubbleInfo = new Vector<BubbleInfo>();

    public State_AnimateToBubbleView(CanvasView canvasView) {
        mCanvasView = canvasView;
    }

    @Override
    public void onEnterState() {
        mCanvasView.fadeOutTargets();
        if (mCanvasView.getContentView() != null) {
            mCanvasView.getContentView().onAnimateOffscreen();
        }
        mBubbleInfo.clear();
        mTime = 0.0f;
        mBubblePeriod = 0.3f;
        mContentPeriod = mBubblePeriod * 0.666667f;      // 0.66667 is the normalized t value when f = 1.0f for overshoot interpolator of 0.5 tension

        MainController mainController = MainController.get();
        int bubbleCount = mainController.getBubbleCount();
        for (int i=0 ; i < bubbleCount ; ++i) {
            BubbleInfo bi = new BubbleInfo();
            BubbleView b = mainController.getBubble(i);
            bi.mPosX = (float) b.getXPos();
            bi.mPosY = (float) b.getYPos();

            bi.mTargetX = Config.BUBBLE_HOME_X;
            bi.mTargetY = Config.BUBBLE_HOME_Y;
            bi.mDistanceX = bi.mTargetX - bi.mPosX;
            bi.mDistanceY = bi.mTargetY - bi.mPosY;
            mBubbleInfo.add(bi);
        }

        mCanvasView.setContentViewTranslation(0.0f);

        mainController.endAppPolling();
    }

    @Override
    public boolean onUpdate(float dt) {
        float f = mInterpolator.getInterpolation(mTime / mBubblePeriod);
        mTime += dt;

        MainController mainController = MainController.get();
        int bubbleCount = mainController.getBubbleCount();
        for (int i=0 ; i < bubbleCount ; ++i) {
            BubbleInfo bi = mBubbleInfo.get(i);
            BubbleView b = mainController.getBubble(i);

            float x = bi.mPosX + bi.mDistanceX * f;
            float y = bi.mPosY + bi.mDistanceY * f;

            if (mTime >= mBubblePeriod) {
                x = bi.mTargetX;
                y = bi.mTargetY;
            }

            b.setExactPos((int) x, (int) y);
        }

        float t = Util.clamp(0.0f, mTime / mContentPeriod, 1.0f);
        mCanvasView.setContentViewTranslation(t * (Config.mScreenHeight - Config.mContentOffset));

        if (mTime >= mBubblePeriod && mTime >= mContentPeriod) {
            mainController.switchState(mainController.STATE_BubbleView);
            mainController.hideContentActivity();
        }

        return true;
    }

    @Override
    public void onExitState() {
        mCanvasView.setContentViewTranslation(Config.mScreenHeight - Config.mContentOffset);
        mCanvasView.setContentView(null);
    }

    @Override
    public void onTouchActionDown(DraggableItem sender, DraggableHelper.TouchEvent e) {
    }

    @Override
    public void onTouchActionMove(DraggableItem sender, DraggableHelper.MoveEvent e) {
    }

    @Override
    public void onTouchActionRelease(DraggableItem sender, DraggableHelper.ReleaseEvent e) {
    }

    @Override
    public boolean onNewDraggable(DraggableItem draggableItem) {
        Util.Assert(false);
        return false;
    }

    @Override
    public void onDestroyDraggable(DraggableItem draggableItem) {
        Util.Assert(false);
    }

    @Override
    public boolean onOrientationChanged() {
        MainController mainController = MainController.get();
        mainController.switchState(mainController.STATE_BubbleView);
        return false;
    }

    @Override
    public void onCloseDialog() {
    }

    @Override
    public String getName() {
        return "AnimateToBubbleView";
    }
}
