package bkav.android.speech;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.SparseArray;

import org.chromium.chrome.browser.R;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import static android.os.Environment.isExternalStorageRemovable;

/**
 * QuangNHe: Luồng request
 * <p>
 * ---> Play -> tách từ -> query câu ưu tiên -> tính time đợi và đợi -> check status server (0.5s/lần) -> Download khi có file
 * ...............................^                                                                                  |
 * ...............................|                                                                                  v
 * ---> Seek -> tính câu tiếp --->+---------------------------------------------------------------------------- Download xong
 * ...............................^                                                                                  |
 * ...............................|                                                                                  v
 * ...............................+------------------------------- check câu tiếp sẵn sàng chưa? <- onCompletion <- Play
 */
public class SpeechManageService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, RequestFinishCallback {
    public static final String TAB_ID = "tab_id";
    public static final String URL = "tab_url";
    public static final String TEXT = "speech_text";
    public static final String DURATION = "duration";
    public static final String POSITION = "position";
    public static final String DURATION_SENTENCE = "duration_sentence";
    public static final String START_SENTENCE = "start_sentence";
    public static final String END_SENTENCE = "end_sentence";

    public static final String ACTION_PLAY = "bkav.android.speech.start";
    public static final String ACTION_PAUSE = "bkav.android.speech.pause";
    public static final String ACTION_SEEK = "bkav.android.speech.seek";
    public static final String ACTION_RESET = "bkav.android.speech.reset";
    public static final String ACTION_CLOSE = "bkav.android.speech.close";
    public static final String ACTION_UPDATE = "bkav.android.speech.update";

    public static final int TIME_OUT = 3000;

    private static final String TAG = "SpeechManageService";

    private static final String SERVER_URL = "http://222.254.34.101:2080/api/tts/main";

    private static final int FOREGROUND_ID = 1002;
    private static final String TEXT_TAG = "text";
    private static final String LINK_AUDIO_TAG = "link_audio";
    private static final String STATUS_AUDIO_TAG = "status_audio";
    private static final String STATUS_TAG = "status";

    private static final String EXISTS_VALUE = "exists";

    private static final String DISK_CACHE_SUBDIR = "BITMAP_CACHE";
    static String sCachePath;

    private final IBinder mBinder = new LocalBinder();
    private SparseArray<SpeechInfo> mInfoArray = new SparseArray<>();
    private MediaPlayer mPlayer;
    private SpeechNotificationController mNotificationController;
    private SpeechInfo mCurrentSpeech;
    private Bitmap mDefaultBitmap;
    private RequestSentenceTask mRequestSentenceTask;

    private AudioAttributes mPlaybackAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();
    private AudioManager.OnAudioFocusChangeListener mFocusChangeListener =
            new AudioManager.OnAudioFocusChangeListener() {
                public void onAudioFocusChange(int focusChange) {
                    if (mPlayer == null) return;
                    switch (focusChange) {
                        case (AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK):
                            // Lower the volume while ducking.
                            mPlayer.setVolume(0.2f, 0.2f);
                            break;
                        case (AudioManager.AUDIOFOCUS_LOSS_TRANSIENT):
                        case (AudioManager.AUDIOFOCUS_LOSS):
                            pause();
                            break;

                        case (AudioManager.AUDIOFOCUS_GAIN):
                            // Return the volume to normal and resume if paused.
                            mPlayer.setVolume(1f, 1f);
                            play(new Intent());
                            break;
                        default:
                            break;
                    }
                }
            };
    private AudioFocusRequest mFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(mPlaybackAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(mFocusChangeListener)
            .build();

    public SpeechManageService() {
        super();
    }

    private static boolean requestServer(SentenceInfo info) throws IOException, JSONException {
        OutputStream out;

        java.net.URL url = new URL(SERVER_URL);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("POST");
        urlConnection.setReadTimeout(TIME_OUT);
        urlConnection.setConnectTimeout(TIME_OUT);
        urlConnection.setDoInput(true);
        urlConnection.setDoOutput(true);

        urlConnection.setRequestProperty("Content-Type", "application/json");

        JSONObject root = new JSONObject();
        root.put(TEXT_TAG, info.mString);
        String data = root.toString();

        System.out.println("12 - urlConnection : " + urlConnection);
        out = new BufferedOutputStream(urlConnection.getOutputStream());

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.write(data);
        writer.flush();
        writer.close();
        out.close();

        // QuangNhe: Lấy dữ liệu trả về
        int responseCode = urlConnection.getResponseCode();
        StringBuilder response = new StringBuilder();

        System.out.println("13 - responseCode : " + responseCode);

        if (responseCode == HttpsURLConnection.HTTP_OK) {
            System.out.println("14 - HTTP_OK");

            String line;
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    urlConnection.getInputStream()));
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            br.close();
        }

        if (!response.toString().equalsIgnoreCase("")) {
            System.out.println("6 - response !empty...");
            //
            JSONObject jRoot = new JSONObject(response.toString());

            info.mAudioUrl = jRoot.getString(LINK_AUDIO_TAG);
            System.out.println(info.mAudioUrl + "");

            info.mStatusUrl = jRoot.getString(STATUS_AUDIO_TAG);
            System.out.println(info.mStatusUrl + "");

            info.mFile = getFile(info.mString);
        } else {
            System.out.println("6 - response is empty...");

            info.mAudioUrl = "";
            return false;
        }
        return true;
    }

    private static void waitServerReady(SentenceInfo sentenceInfo) {
        for (int i = 0; i < 30; i++) {
            try {
                java.net.URL url = new URL(sentenceInfo.mStatusUrl);
                HttpURLConnection urlConnection;
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setReadTimeout(TIME_OUT);
                urlConnection.setConnectTimeout(TIME_OUT);
                int responseCode = urlConnection.getResponseCode();
                StringBuilder response = new StringBuilder();

                System.out.println("13 - responseCode : " + responseCode);

                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    System.out.println("14 - HTTP_OK");

                    String line;
                    BufferedReader br = new BufferedReader(new InputStreamReader(
                            urlConnection.getInputStream()));
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    br.close();
                    try {
                        System.out.println("6 - response !empty...");
                        //
                        JSONObject jRoot = new JSONObject(response.toString());

                        String checkValue = jRoot.getString(STATUS_TAG);
                        System.out.println(checkValue + "");

                        if (EXISTS_VALUE.equals(checkValue)) {
                            sentenceInfo.mStatus = SentenceInfo.STATUS.READY;
                            return;
                        }

                    } catch (JSONException e) {
                        // displayLoding(false);
                        // e.printStackTrace();
                        System.out.println("Error " + e.getMessage());
                    }

                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static String getFile(String string) {
        // Check if media is mounted or storage is built-in, if so, try and use external cache dir
        // otherwise use internal cache dir

        return sCachePath + File.separator + DISK_CACHE_SUBDIR + File.separator + string.hashCode();
    }

    private static void downloadAudioFile(SentenceInfo mInfo) {
        InputStream input = null;
        OutputStream output = null;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(mInfo.mAudioUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();
            connection.setReadTimeout(TIME_OUT);

            // expect HTTP 200 OK, so we don't mistakenly save error report
            // instead of the file
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                System.out.println("Server returned HTTP " + connection.getResponseCode()
                        + " " + connection.getResponseMessage());
                return;
            }

            // this will be useful to display download percentage
            // might be -1: server did not report the length
            // int fileLength = connection.getContentLength();

            // download the file
            input = connection.getInputStream();
            File file = new File(mInfo.mFile);
            if (!file.exists())
                file.getParentFile().mkdirs();
            output = new FileOutputStream(mInfo.mFile);

            byte[] data = new byte[4096];
            int count;
            while ((count = input.read(data)) != -1) {
                // publishing the progress....
                output.write(data, 0, count);
            }
            mInfo.mStatus = SentenceInfo.STATUS.DOWNLOADED;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (output != null)
                    output.close();
                if (input != null)
                    input.close();
            } catch (IOException ignored) {
            }

            if (connection != null)
                connection.disconnect();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sCachePath = Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ||
                !isExternalStorageRemovable() ? getApplicationContext().getExternalCacheDir().getPath() :
                getApplicationContext().getCacheDir().getPath();

        initMusicPlayer();

        mNotificationController = new SpeechNotificationController(getApplicationContext());
        mNotificationController.createNotificationChannel();

        mDefaultBitmap = BitmapFactory.decodeResource(getResources(),
                R.drawable.ic_android);


    }

    //phuong thuc khoi tao lop mediaplayer
    public void initMusicPlayer() {
        mPlayer = new MediaPlayer();
        //cau hinh phat nhac bang cach thiet lap thuoc tinh
        mPlayer.setWakeMode(getApplicationContext(),
                PowerManager.PARTIAL_WAKE_LOCK);
        mPlayer.setAudioAttributes(mPlaybackAttributes);
        // thiet lap onprepare khi doi tuong mediaplayre duoc chuan bi
        mPlayer.setOnPreparedListener(this);
        //thiet lap khi bai hat da phat xong
        mPlayer.setOnCompletionListener(this);
        //thiet lap khi say ra loi
        mPlayer.setOnErrorListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction() != null)
            switch (intent.getAction()) {
                case ACTION_PLAY:
                    System.out.println("Started!");
                    play(intent);
                    break;
                case ACTION_PAUSE:
                    System.out.println("ACTION_PAUSE");
                    pause();
                    break;
                case ACTION_SEEK:
                    System.out.println("ACTION_SEEK");
                    seek(intent);
                    break;
                case ACTION_RESET:
                    System.out.println("ACTION_RESET");
                    reset();
                    break;
                case ACTION_CLOSE:
                    System.out.println("ACTION_CLOSE");
                    close();
                    break;
            }

        return START_NOT_STICKY;
    }

    private void seek(Intent intent) {
        int position = intent.getIntExtra(POSITION, -1);
        if (position == -1) {
            System.out.println("seek FAIL -1");
            return;
        }
        ArrayList<SentenceInfo> mArr = mCurrentSpeech.mArr;
        int i;
        boolean check = false;
        for (i = 0; i < mArr.size(); i++) {
            if (position > mArr.get(i).mDuration) {
                position -= mArr.get(i).mDuration;
            } else {
                check = true;
                break;
            }
        }

        // Khi chay het speech ma van khong den duoc postion thi khong tua nua
        if (!check) {
            System.out.println("seek FAIL");
            updateTime();
            return;
        }

        System.out.println("seek to " + i + ": " + position);
        if (mPlayer.isPlaying())
            mPlayer.pause();
        mCurrentSpeech.mCurrentSentence = i;
        mCurrentSpeech.mCurrentTime = position * 1f / mArr.get(i).mDuration;
        queryNextSentence(mCurrentSpeech.current());
    }

    private void close() {
        mPlayer.stop();
        if (mCurrentSpeech != null) {
            mCurrentSpeech.mCurrentTime = 0;
            mCurrentSpeech.mCurrentSentence = 0;
        }
        stopForeground(true);
        stopSelf();
        mNotificationController.cancel(FOREGROUND_ID);
    }

    private void reset() {
        if (mCurrentSpeech == null) {
            System.out.println("reset - ERR null : ");
            return;
        }

        mCurrentSpeech.mCurrentTime = 0;
        if (mCurrentSpeech.mCurrentSentence == 0) {
            mPlayer.seekTo(0);
            mPlayer.start();
        } else {
            mCurrentSpeech.mCurrentSentence = 0;
            SentenceInfo sentenceInfo = mCurrentSpeech.get(0);
            try {
                checkFileAudioReady(sentenceInfo);
            } catch (Exception e) {
                System.out.println("play - ERR : checkFileAudioReady");
                e.printStackTrace();
            }
        }

    }

    private void pause() {
        if (mCurrentSpeech == null) {
            System.out.println("pause - ERR null : ");
            return;
        }

        if (mPlayer == null || !mPlayer.isPlaying()) return;

        mCurrentSpeech.mCurrentTime = mPlayer.getCurrentPosition() * 1f / mPlayer.getDuration();
        mPlayer.pause();
        mNotificationController.updateNotification(mDefaultBitmap, "speech", false, FOREGROUND_ID);
        stopForeground(STOP_FOREGROUND_DETACH);
    }

    private void play(Intent intent) {
        Bundle b = intent.getExtras();

        if (b != null) {
            int tabId = b.getInt(TAB_ID);
            String url = b.getString(URL);
            String text = b.getString(TEXT);

            SpeechInfo speechInfo = mInfoArray.get(tabId);
            if (TextUtils.isEmpty(text) && (speechInfo == null || !speechInfo.url.equals(url)))
                return;

            if (speechInfo == null || !speechInfo.url.equals(url)) {
                ArrayList<SentenceInfo> sentenceInfos = processTextFirstTime(text);
                if (sentenceInfos.size() == 0)
                    return;
                speechInfo = new SpeechInfo(sentenceInfos, url);
                mInfoArray.append(tabId, speechInfo);
//                new RequestServerTask(speechInfo.mArr, this).execute();
            }

            speechInfo.mStatus = SpeechInfo.STATUS.LOADING;
            mCurrentSpeech = speechInfo;
        } else if (mPlayer == null || mCurrentSpeech == null) {
            System.out.println("play - ERR null : ");
            return;
        }

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.requestAudioFocus(mFocusRequest);
        }

        SentenceInfo sentenceInfo = mCurrentSpeech.current();

        queryNextSentence(sentenceInfo);

//        if (sentenceInfo.isNeedRequest()) {
//            System.out.println("play - isNeedRequest : ");
//            return;
//        }
//
//        try {
//            checkFileAudioReady(sentenceInfo);
//        } catch (Exception e) {
//            System.out.println("play - ERR : checkFileAudioReady");
//            e.printStackTrace();
//        }
    }

    private ArrayList<SentenceInfo> processTextFirstTime(String text) {
        String[] arr = text.split(Pattern.quote("\n"));
        ArrayList<SentenceInfo> list = new ArrayList<>();
        for (String s : arr) {
            if (!TextUtils.isEmpty(s)) {
                if (s.length() > 100) {
                    BreakIterator bi = BreakIterator.getSentenceInstance();
                    bi.setText(text);
                    int index = 0;
                    while (bi.next() != BreakIterator.DONE) {
                        String sentence = text.substring(index, bi.current());
                        list.add(new SentenceInfo(sentence, list.size()));
                        index = bi.current();
                    }
                } else {
                    list.add(new SentenceInfo(s, list.size()));
                }
            }
        }
        return list;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        System.out.println("onPrepared mCurrentSpeech.mCurrentSentence: " + mCurrentSpeech.mCurrentSentence);
        mCurrentSpeech.current().mPlayTime = mp.getDuration();
        mCurrentSpeech.mStatus = SpeechInfo.STATUS.READING;

        updateTime();
        mp.start();
        mp.seekTo((int) (mCurrentSpeech.mCurrentTime * mCurrentSpeech.current().mPlayTime));
        startForeground(FOREGROUND_ID, mNotificationController.getSpeechNotification(mDefaultBitmap, "speech", true));
    }

    private void updateTime() {
        int totalPlayTime = 0, position = 0, startDuration = 0;
        ArrayList<SentenceInfo> mArr = mCurrentSpeech.mArr;
        for (int i = 0; i < mArr.size(); i++) {
            SentenceInfo sentenceInfo = mArr.get(i);
            totalPlayTime += sentenceInfo.mPlayTime;
            if (i < mCurrentSpeech.mCurrentSentence) {
                startDuration += sentenceInfo.mDuration;
                position += sentenceInfo.mDuration;
            } else if (i == mCurrentSpeech.mCurrentSentence) {
                position += (mCurrentSpeech.mCurrentTime * mCurrentSpeech.current().mDuration);
            }
        }
        Intent intent = new Intent(ACTION_UPDATE);
        intent.putExtra(DURATION, totalPlayTime);
        intent.putExtra(DURATION_SENTENCE, mCurrentSpeech.current().mPlayTime);
        intent.putExtra(POSITION, position); // vi tri tren progress bar
        intent.putExtra(START_SENTENCE, startDuration); // vi tri tren progress bar
        intent.putExtra(END_SENTENCE, startDuration + mCurrentSpeech.current().mDuration); // vi tri tren progress bar
        sendBroadcast(intent);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (mCurrentSpeech == null)
            return;
        System.out.println("onCompletion mCurrentSpeech.mCurrentSentence: " + mCurrentSpeech.mCurrentSentence);
        mp.reset();
        mCurrentSpeech.mCurrentTime = 0;

        int currentSentence = mCurrentSpeech.mCurrentSentence + 1;
        if (currentSentence == mCurrentSpeech.mArr.size()) {
            mCurrentSpeech.mStatus = SpeechInfo.STATUS.PLAYABLE;
            mCurrentSpeech.mCurrentSentence = 0;
            mNotificationController.updateNotification(mDefaultBitmap, "speech", false, FOREGROUND_ID);
            stopForeground(STOP_FOREGROUND_DETACH);

            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.abandonAudioFocusRequest(mFocusRequest);
            }
        } else {
            mCurrentSpeech.mCurrentSentence = currentSentence;
            SentenceInfo sentenceInfo = mCurrentSpeech.get(currentSentence);
            if (sentenceInfo.mStatus != SentenceInfo.STATUS.DOWNLOADED) {
//                boolean rs = requestServer(sentenceInfo);
//                if (!rs) {
                System.out.println("onCompletion FALSE: " + sentenceInfo);
//                    return;
//                }
                queryNextSentence(sentenceInfo);
                return;
            }
            onAudioFileReady(sentenceInfo);
            System.out.println("onCompletion END ");
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return false;
    }

    private void checkFileAudioReady(SentenceInfo sentenceInfo) {
        if (sentenceInfo.mStatus == SentenceInfo.STATUS.DOWNLOADED) {
            onAudioFileReady(sentenceInfo);
            return;
        }

        queryNextSentence(sentenceInfo);
//        if (mWaitServerReadyTask != null)
//            mWaitServerReadyTask.cancel(true);
//        mWaitServerReadyTask = new WaitServerReadyTask(sentenceInfo, this);
//        mWaitServerReadyTask.execute();
    }

    @Override
    public void onAudioFileReady(SentenceInfo sentenceInfo) {
        if (!sentenceInfo.equals(mCurrentSpeech.current())) {
            System.out.println("onAudioFileReady not equals");
            return;
        }
        System.out.println("onAudioFileReady sentenceInfo " + sentenceInfo.mOrder);
        mPlayer.reset();

        try {

//            String filename = "android.resource://" + this.getPackageName() + "/raw/piano2";
//            mPlayer.setDataSource(this, Uri.parse(filename));

//            mPlayer.setDataSource(sentenceInfo.mAudioUrl);

            FileInputStream inputStream = new FileInputStream(sentenceInfo.mFile);
            mPlayer.setDataSource(inputStream.getFD());
            inputStream.close();

//            mPlayer.setDataSource(sentenceInfo.mFile.getAbsolutePath());

            mPlayer.prepareAsync();
            mNotificationController.updateNotification(mDefaultBitmap, "speech", false, FOREGROUND_ID);

        } catch (IOException e) {
            System.out.println("onAudioFileReady ERR " + sentenceInfo.mFile + " " + sentenceInfo.mFile);
            e.printStackTrace();
        }

        int nextSentence = mCurrentSpeech.mCurrentSentence + 1;
        if (nextSentence >= mCurrentSpeech.mArr.size()) {
            return;
        }
        queryNextSentence(mCurrentSpeech.get(nextSentence));
    }

    void queryNextSentence(SentenceInfo mInfo) {
        if (mRequestSentenceTask != null) {
            if (mRequestSentenceTask.mInfo.equals(mInfo) && !mRequestSentenceTask.isFinish) {
                System.out.println("queryNextSentence not cancel task" + mInfo.mOrder + " / " + mRequestSentenceTask.mInfo.mOrder);
                return;
            }
            System.out.println("queryNextSentence cancel task" + mInfo.mOrder + " / " + mRequestSentenceTask.mInfo.mOrder + " | " + mRequestSentenceTask.isFinish);
            if (!mRequestSentenceTask.isFinish)
                mRequestSentenceTask.cancel(true);
        }
        System.out.println("queryNextSentence new: " + mInfo.mString);
        mRequestSentenceTask = new RequestSentenceTask(mInfo, this);
        mRequestSentenceTask.execute();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPlayer.stop();
        mPlayer.release();
    }

    static class RequestSentenceTask extends
            AsyncTask<Void, Void, Void> {
        private SentenceInfo mInfo;
        private RequestFinishCallback mCallback;
        private boolean isFinish = false;

        RequestSentenceTask(SentenceInfo mInfo, RequestFinishCallback mCallback) {
            this.mInfo = mInfo;
            this.mCallback = mCallback;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (mInfo.mStatus == SentenceInfo.STATUS.DOWNLOADED) {
                isFinish = true;
                return null;
            }
            if (mInfo.isNeedRequest()) {
                System.out.println("RequestSentenceTask mInfo.isNeedRequest " + this);
                for (int i = 0; i < 5; i++) {
                    try {
                        boolean rs = requestServer(mInfo);
                        if (rs) {
                            break;
                        } else {
                            throw new Exception("Waiting 500ms");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
            }
            if (mInfo.isNeedRequest()) {
                System.out.println("RequestSentenceTask mInfo.isNeedRequest still null " + this);
                isFinish = true;
                return null;
            }

            System.out.println("RequestSentenceTask waiting.... timeForServerProcessInMillisecond" + this);
            // QuangNHe: Đợi server chuẩn bị file sẵn sàng
            try {
                Thread.sleep(timeForServerProcessInMillisecond());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("RequestSentenceTask waiting...." + this);
            waitServerReady(mInfo);

            System.out.println("RequestSentenceTask downloading...." + this);
            downloadAudioFile(mInfo);
            System.out.println("RequestSentenceTask DONE " + this);
            isFinish = true;
            return null;
        }

        private int timeForServerProcessInMillisecond() {
            return /*Math.max(mInfo.mString.length() * 1000 / 30, 500)*/ 500;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if (!mInfo.isNeedRequest())
                mCallback.onAudioFileReady(mInfo);
        }
    }

    static class SpeechInfo {
        ArrayList<SentenceInfo> mArr;
        String url;
        int mCurrentSentence = 0;
        float mCurrentTime = 0;
        STATUS mStatus = STATUS.PLAYABLE;

        SpeechInfo(ArrayList<SentenceInfo> mArr, String url) {
            this.mArr = mArr;
            this.url = url;
        }

        SentenceInfo get(int index) {
            return mArr.get(index);
        }

        SentenceInfo current() {
            return mArr.get(mCurrentSentence);
        }

        enum STATUS {
            PLAYABLE, LOADING, READING
        }
    }

    static class SentenceInfo implements Serializable {
        String mString;
        int mDuration;
        int mPlayTime;
        int mOrder;
        String mAudioUrl = "";
        String mStatusUrl = "";
        String mFile;
        STATUS mStatus = STATUS.WAITING;

        SentenceInfo(String mString, int order) {
            this.mString = mString;
            this.mOrder = order;
            mDuration = getDurationFromSentence(mString);
            mPlayTime = mDuration * 1000 / 5; // coi tốc độ đọc là 5 từ/s
            System.out.println("Order " + order + ": " + mString);
        }

        private int getDurationFromSentence(String text) {
            return text.split(Pattern.quote(" ")).length;
        }

        boolean isNeedRequest() {
            return TextUtils.isEmpty(mAudioUrl) || TextUtils.isEmpty(mStatusUrl);
        }

        enum STATUS {
            WAITING, READY, DOWNLOADED
        }
    }

    public class LocalBinder extends Binder {
        public SpeechManageService getService() {
            return SpeechManageService.this;
        }
    }
}

