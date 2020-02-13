package org.chromium.chrome.browser;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.lang.ref.WeakReference;
import java.util.regex.Pattern;

import bkav.android.speech.SpeechManageService;

import static bkav.android.speech.SpeechManageService.ACTION_PAUSE;
import static bkav.android.speech.SpeechManageService.ACTION_PLAY;
import static bkav.android.speech.SpeechManageService.ACTION_RESET;
import static bkav.android.speech.SpeechManageService.ACTION_SEEK;
import static bkav.android.speech.SpeechManageService.ACTION_UPDATE;
import static bkav.android.speech.SpeechManageService.DURATION;
import static bkav.android.speech.SpeechManageService.DURATION_SENTENCE;
import static bkav.android.speech.SpeechManageService.END_SENTENCE;
import static bkav.android.speech.SpeechManageService.POSITION;
import static bkav.android.speech.SpeechManageService.START_SENTENCE;
import static bkav.android.speech.SpeechManageService.TAB_ID;
import static bkav.android.speech.SpeechManageService.TEXT;
import static bkav.android.speech.SpeechManageService.URL;

public class ChromeActivity extends AppCompatActivity {
    private static final String ARTICLE = "“Sau khi được Giải nhất Nhân tài Đất Việt năm 2020 và nhận được sự động viên, khích lệ của lãnh đạo cấp trên nên Viện đã tiếp tục giới thiệu công trình nghiên cứu của mình ra Hội chợ sáng chế khoa học và công nghệ quốc tế. Rất vinh dự cho Viện là công trình được đánh giá rất cao về tính ứng dụng, khả năng thương mại hoá nên đã được nhận huy chương vàng”. Tiến sĩ Lý cũng cho hay, Hội chợ sáng chế khoa học và công nghệ quốc tế là một trong những hội chợ phát minh sáng chế lớn nhất thế giới, nơi các nhà sáng chế giới thiệu các ý tưởng mới và sản phẩm sáng chế. Đây cũng là dịp để đại diện đến từ các nước được tư vấn quy trình cấp bằng sáng chế quốc tế và chuyển giao công nghệ, qua đó, mở ra nhiều cơ hội cho các nhà sáng chế thương mại hóa các công trình của mình.Trước đó, từ nhiệm vụ được giao, các cán bộ của Viện kỹ thuật cơ giới quân sự, đứng đầu là Đại tá, Tiến sĩ Trần Hữu Lý đã vượt qua mọi khó khăn để cải hoán xe thiết giáp bánh lốp BTR-152 thành xe thiết giáp cứu thương phục vụ nhiệm vụ gìn giữ hòa bình tại Nam Xu Đăng. Giải pháp độc đáo này đã tiết kiệm hàng triệu USD cho ngân sách Bộ Quốc phòng và đã được nhận Giải nhất Giải thưởng Nhân tài Đất Việt năm 2019 lĩnh vực khoa học công nghệ.\"";

    public static final int MSG_UPDATE_SEEKBAR = 3000;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has
            // been established, giving us the service object we can use
            // to interact with the service.  Because we have bound to a
            // explicit service that we know is running in our own
            // process, we can cast its IBinder to a concrete class and
            // directly access it.

            // Tell the user about this for our demo.
//            Toast.makeText(FullscreenWebPlayer.this,
//                    R.string.done,
//                    Toast.LENGTH_SHORT).show();
            mBoundService = ((SpeechManageService.LocalBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has
            // been unexpectedly disconnected -- that is, its process
            // crashed. Because it is running in our same process, we
            // should never see this happen.
            mBoundService = null;
        }
    };
    private SpeechManageService mBoundService;
    private SeekBar mSeekBar;
    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        handler = new PrivateHandler(this);

        mSeekBar = findViewById(R.id.seekbar);
        mTextView = findViewById(R.id.text);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                System.out.println("onProgressChanged " + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                System.out.println("onStartTrackingTouch " + seekBar.getProgress());
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                System.out.println("onStopTrackingTouch" + seekBar.getProgress());
                Intent mPlayIntent = new Intent(ChromeActivity.this, SpeechManageService.class);
                mPlayIntent.setAction(ACTION_SEEK);
                mPlayIntent.putExtra(POSITION, seekBar.getProgress());
                startService(mPlayIntent);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to LocalService
        doBindService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerKeyboardReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterKeyboardReceiver();
    }

    @Override
    protected void onStop() {
        super.onStop();
        doUnbindService();
    }

    private boolean mIsBound;

    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation
        // that we know will be running in our own process (and thus
        // won't be supporting component replacement by other
        // applications).
        bindService(new Intent(this, SpeechManageService.class),
                mConnection,
                Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    public void click(View view) {
        Intent mPlayIntent = new Intent(this, SpeechManageService.class);
        mPlayIntent.setAction(ACTION_PLAY);

        Bundle bundle = new Bundle();
        bundle.putInt(TAB_ID, 0);
        bundle.putString(URL, "https://vnreview.vn/");
        bundle.putString(TEXT, ARTICLE);
        mPlayIntent.putExtras(bundle);

        mSeekBar.setMax(ARTICLE.split(Pattern.quote(" ")).length);

        startService(mPlayIntent);
        bindService(mPlayIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    public void pause(View view) {
        Intent mPlayIntent = new Intent(this, SpeechManageService.class);
        mPlayIntent.setAction(ACTION_PAUSE);
        startService(mPlayIntent);
        handler.removeMessages(MSG_UPDATE_SEEKBAR);
    }

    public void reset(View view) {
        Intent mPlayIntent = new Intent(this, SpeechManageService.class);
        mPlayIntent.setAction(ACTION_RESET);
        startService(mPlayIntent);
    }

    private IntentFilter mKeyboardFilter;
    private KeyboardReceiver mKeyboardReceiver;

    private void registerKeyboardReceiver() {
        if (mKeyboardFilter == null)
            mKeyboardFilter = new IntentFilter(ACTION_UPDATE);
        if (mKeyboardReceiver == null)
            mKeyboardReceiver = new KeyboardReceiver();
        registerReceiver(mKeyboardReceiver, mKeyboardFilter);
    }

    private void unregisterKeyboardReceiver() {
        if (mKeyboardFilter == null || mKeyboardReceiver == null)
            return;
        unregisterReceiver(mKeyboardReceiver);
    }

    private int currentStart, currentEnd, currentDuration, currentPosition;

    class KeyboardReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            currentDuration = intent.getIntExtra(DURATION_SENTENCE, 100);
            currentStart = intent.getIntExtra(START_SENTENCE, 0);
            currentEnd = intent.getIntExtra(END_SENTENCE, 100);

            int position = intent.getIntExtra(POSITION, 0);
            mSeekBar.setProgress(position);

            currentPosition = (int) (1f * (position - currentStart) / (currentEnd - currentStart) * currentDuration);
            int duration = intent.getIntExtra(DURATION, 100);
            int minutes = duration / 60000;
            if (minutes > 0)
                mTextView.setText("Khoang " + minutes + " phut");
            else if (duration > 0)
                mTextView.setText("Khoang " + duration / 1000 + " giay");
            handler.removeMessages(MSG_UPDATE_SEEKBAR);
            handler.sendEmptyMessageDelayed(MSG_UPDATE_SEEKBAR, 500);
        }
    }

    private void updateSeekbar() {
        currentPosition += 500;
        float speed = 1f * currentPosition / currentDuration;
        if (speed >= 1)
            handler.removeMessages(MSG_UPDATE_SEEKBAR);
        int progress = (int) interpolate(currentStart, currentEnd, speed);
        System.out.println("ChromeActivity updateSeekbar " + progress + " - " + currentEnd);
        mSeekBar.setProgress(progress);
    }

    public static float interpolate(float value, float target, float speed) {
        return (value + (target - value) * speed);
    }

    PrivateHandler handler;

    private static class PrivateHandler extends Handler {
        private final WeakReference<ChromeActivity> mWeakReference;

        PrivateHandler(ChromeActivity tab) {
            mWeakReference = new WeakReference<>(tab);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (mWeakReference.get() == null)
                return;
            // QuangNHe: Neu khi mo la readermode thi chuyen ve url goc
//            if (msg.what == MSG_ID_RESTORE_ORIGIN_URL_AFTERLOAD) {
//                mWeakReference.get().loadUrl(new LoadUrlParams(
//                        DomDistillerUrlUtils.getOriginalUrlFromDistillerUrl(mWeakReference.get().getUrl())));
//            }
//            // QuangNHe: Neu khi mo la readermode thi chuyen ve url goc
//            if (msg.what == MSG_ID_RESTORE_ORIGIN_URL_AFTERLOAD) {
//                mWeakReference.get().loadUrl(new LoadUrlParams(
//                        DomDistillerUrlUtils.getOriginalUrlFromDistillerUrl(mWeakReference.get().getUrl())));
//            }
            // QuangNHe: Neu khi mo la readermode thi chuyen ve url goc
//            if (msg.what == MSG_ID_OUT_OF_LOADING_TIME) {
//                mWeakReference.get().checkLoadingOutOfTime();
//            }
            removeMessages(MSG_UPDATE_SEEKBAR);
            sendEmptyMessageDelayed(MSG_UPDATE_SEEKBAR, 500);
            mWeakReference.get().updateSeekbar();
        }
    }


}
