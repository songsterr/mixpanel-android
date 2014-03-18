package com.mixpanel.android.mpmetrics;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import com.mixpanel.android.R;
import com.mixpanel.android.util.ActivityImageUtils;

@SuppressLint("NewApi")
public class InAppFragment extends Fragment implements View.OnClickListener {

    public InAppFragment setNotification(InAppNotification notif) {
        // It would be better to pass in the InAppNotification to the only constructor, but
        // Fragments require a default constructor that is called when Activities recreate them.
        // This is not an issue since we kill notifications as soon as the Activity is onStopped.
        // Android docs recommend passing any state a Fragment needs through a Bundle, but
        // Bundle's have a 1MB limit on size, which is not good for Bitmaps.
        mNotification = notif;
        return this;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // We have to hold these references because the Activity does not clear it's Handler
        // of messages when it disappears, so we have to manually clear these Runnables in onStop
        // in case they exist
        mParent = activity;
        mHandler = new Handler();
        mRemover = new Runnable() {
            public void run() {
                InAppFragment.this.remove();
            }
        };
        mDisplayMini = new Runnable() {
            @Override
            public void run() {
                mInAppView.setVisibility(View.VISIBLE);
                final int highlightColor = ActivityImageUtils.getHighlightColorFromBackground(mParent);
                mInAppView.setBackgroundColor(highlightColor);

                final ImageView notifImage = (ImageView) mInAppView.findViewById(R.id.com_mixpanel_android_notification_image);

                final float heightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 75, mParent.getResources().getDisplayMetrics());
                final TranslateAnimation translate = new TranslateAnimation(0, 0, heightPx, 0);
                translate.setInterpolator(new DecelerateInterpolator());
                translate.setDuration(200);
                mInAppView.startAnimation(translate);

                final ScaleAnimation scale = new ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f, heightPx / 2, heightPx / 2);
                scale.setInterpolator(new SineBounceInterpolator());
                scale.setDuration(400);
                scale.setStartOffset(200);
                notifImage.startAnimation(scale);
            }
        };
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mKill = false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        if (null == mNotification) {
            Log.e(LOGTAG, "setNotification was not called with a valid InAppNotification, not creating view");
            return null;
        }
        mInAppView = inflater.inflate(R.layout.com_mixpanel_android_activity_notification_mini, container, false);
        final TextView titleView = (TextView) mInAppView.findViewById(R.id.com_mixpanel_android_notification_title);
        final ImageView notifImage = (ImageView) mInAppView.findViewById(R.id.com_mixpanel_android_notification_image);

        titleView.setText(mNotification.getTitle());
        notifImage.setImageBitmap(mNotification.getImage());

        mHandler.postDelayed(mRemover, MINI_REMOVE_TIME);

        mInAppView.setOnClickListener(this);

        return mInAppView;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mKill) {
            mParent.getFragmentManager().beginTransaction().remove(this).commit();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // getHighlightColorFromBackground doesn't seem to work on onResume because the view
        // has not been fully rendered, so try and delay a little bit
        mHandler.postDelayed(mDisplayMini, 500);
    }

    @Override
    public void onStop() {
        super.onStop();

        mHandler.removeCallbacks(mRemover);
        mHandler.removeCallbacks(mDisplayMini);

        // This Fragment when registered on the Activity is part of its state, and so gets
        // restored / recreated when the Activity goes away and comes back. We prefer to just not
        // keep the notification around, especially in the case of mini, so we have to remember to kill it.
        // If the Activity object fully dies, then it is not remembered, so onSaveInstanceState is not necessary.
        mKill = true;
    }

    @Override
    public void onClick(View clicked) {
        final String uriString = mNotification.getCallToActionUrl();
        if (uriString != null && uriString.length() > 0) {
            Uri uri = null;
            try {
                uri = Uri.parse(uriString);
            } catch (IllegalArgumentException e) {
                Log.i(LOGTAG, "Can't parse notification URI, will not take any action", e);
                return;
            }

            assert(uri != null);
            try {
                Intent viewIntent = new Intent(Intent.ACTION_VIEW, uri);
                mParent.startActivity(viewIntent);
            } catch (ActivityNotFoundException e) {
                Log.i(LOGTAG, "User doesn't have an activity for notification URI");
            }
        }

        remove();
    }

    private void remove() {
        if (mParent != null) {
            final FragmentManager fragmentManager = mParent.getFragmentManager();

            // setCustomAnimations works on a per transaction level, so the animations set
            // when this fragment was created do not apply
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.setCustomAnimations(0, R.anim.slide_down).remove(this).commit();
        }
    }

    private class SineBounceInterpolator implements Interpolator {
        public SineBounceInterpolator() { }
        public float getInterpolation(float t) {
            return (float) -(Math.pow(Math.E, -8*t) * Math.cos(12*t)) + 1;
        }
    }

    private Activity mParent;
    private Handler mHandler;
    private InAppNotification mNotification;
    private Runnable mRemover, mDisplayMini;
    private View mInAppView;

    private boolean mKill;

    private static final String LOGTAG = "InAppFragment";
    private static final int MINI_REMOVE_TIME = 6000;
}
