package org.chromium.chrome.browser;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import bkav.android.speech.SpeechManageService;

import static bkav.android.speech.SpeechManageService.ACTION_PAUSE;
import static bkav.android.speech.SpeechManageService.ACTION_PLAY;
import static bkav.android.speech.SpeechManageService.ACTION_RESET;
import static bkav.android.speech.SpeechManageService.TAB_ID;
import static bkav.android.speech.SpeechManageService.TEXT;
import static bkav.android.speech.SpeechManageService.URL;

public class ChromeActivity extends AppCompatActivity {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
    }

    @Override
    protected void onPause() {
        super.onPause();
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
        bundle.putString(TEXT, "“Sau khi được Giải nhất Nhân tài Đất Việt năm 2020 và nhận được sự động viên, khích lệ của lãnh đạo cấp trên nên Viện đã tiếp tục giới thiệu công trình nghiên cứu của mình ra Hội chợ sáng chế khoa học và công nghệ quốc tế. Rất vinh dự cho Viện là công trình được đánh giá rất cao về tính ứng dụng, khả năng thương mại hoá nên đã được nhận huy chương vàng”. Tiến sĩ Lý cũng cho hay, Hội chợ sáng chế khoa học và công nghệ quốc tế là một trong những hội chợ phát minh sáng chế lớn nhất thế giới, nơi các nhà sáng chế giới thiệu các ý tưởng mới và sản phẩm sáng chế. Đây cũng là dịp để đại diện đến từ các nước được tư vấn quy trình cấp bằng sáng chế quốc tế và chuyển giao công nghệ, qua đó, mở ra nhiều cơ hội cho các nhà sáng chế thương mại hóa các công trình của mình.Trước đó, từ nhiệm vụ được giao, các cán bộ của Viện kỹ thuật cơ giới quân sự, đứng đầu là Đại tá, Tiến sĩ Trần Hữu Lý đã vượt qua mọi khó khăn để cải hoán xe thiết giáp bánh lốp BTR-152 thành xe thiết giáp cứu thương phục vụ nhiệm vụ gìn giữ hòa bình tại Nam Xu Đăng. Giải pháp độc đáo này đã tiết kiệm hàng triệu USD cho ngân sách Bộ Quốc phòng và đã được nhận Giải nhất Giải thưởng Nhân tài Đất Việt năm 2019 lĩnh vực khoa học công nghệ.\"");
        mPlayIntent.putExtras(bundle);

        startService(mPlayIntent);
        bindService(mPlayIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    public void pause(View view) {
        Intent mPlayIntent = new Intent(this, SpeechManageService.class);
        mPlayIntent.setAction(ACTION_PAUSE);
        startService(mPlayIntent);
    }

    public void reset(View view) {
        Intent mPlayIntent = new Intent(this, SpeechManageService.class);
        mPlayIntent.setAction(ACTION_RESET);
        startService(mPlayIntent);
    }
}
