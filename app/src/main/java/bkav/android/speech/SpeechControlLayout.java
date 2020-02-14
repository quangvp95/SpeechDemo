package bkav.android.speech;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.chromium.chrome.browser.R;

public class SpeechControlLayout extends LinearLayout implements View.OnClickListener {
    public interface OnControlSpeechListener {

        void onPlay();

        void onPause();

        void onReset();

        void onSeek(int position);
    }

    private OnControlSpeechListener mListener;

    private SeekBar mSeekBar;
    private TextView mDurationTextView;
    private ImageView mPlayButton, mResetButton;
    private String mAboutMinuteString, mAboutSecondString;

    private boolean isPlaying;

    public SpeechControlLayout(Context context) {
        super(context);
    }

    public SpeechControlLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        isPlaying = false;
    }

    public SpeechControlLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        isPlaying = false;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSeekBar = findViewById(R.id.bkav_speech_controller_seek_bar);
        mDurationTextView = findViewById(R.id.bkav_speech_controller_show_time_view);
        mPlayButton = findViewById(R.id.bkav_speech_controller_play_btn);
        mResetButton = findViewById(R.id.bkav_speech_controller_replay_btn);
        mAboutMinuteString = getResources().getString(R.string.about_minute);
        mAboutSecondString = getResources().getString(R.string.about_second);

        isPlaying = false;
        mPlayButton.setOnClickListener(this);
        mResetButton.setOnClickListener(this);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mListener != null) mListener.onSeek(seekBar.getProgress());
            }
        });
    }

    public void setListener(OnControlSpeechListener mListener) {
        this.mListener = mListener;
    }

    public void setPlayTime(int totalTime) {
        int minutes = totalTime / 60000;
        if (minutes > 0)
            mDurationTextView.setText(String.format(mAboutMinuteString, minutes));
        else if (totalTime > 0)
            mDurationTextView.setText(String.format(mAboutSecondString, totalTime / 1000));
    }

    public void setMax(int duration) {
        mSeekBar.setMax(duration);
    }

    public void setPosition(int position) {
        mSeekBar.setProgress(position);
    }

    public void setPlaying(boolean playing) {
        isPlaying = playing;
        if (isPlaying) {
            mPlayButton.setImageResource(R.drawable.ic_action_pause);
        } else {
            mPlayButton.setImageResource(R.drawable.ic_action_play);
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mPlayButton) {
            if (isPlaying) {
                if (mListener != null) mListener.onPause();
            } else {
                if (mListener != null) mListener.onPlay();
            }
            setPlaying(!isPlaying);
        } else if (v == mResetButton) {
            if (mListener != null) mListener.onReset();
            mSeekBar.setProgress(0);
        }
    }
}
