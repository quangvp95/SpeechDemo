package bkav.android.speech;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.widget.RemoteViews;

import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.R;


class SpeechNotificationController {
    private static final String SPEECH_CHANNEL_ID = "Chim Lac Speech Channel ID";
    private static final String SPEECH_CHANNEL_NAME = "Chim Lac Speech Channel";
    private static final String ACTION_SPEECH_REPLAY = SpeechManageService.ACTION_RESET;
    private static final String ACTION_SPEECH_PAUSE = SpeechManageService.ACTION_PAUSE;
    private static final String ACTION_SPEECH_PLAY = SpeechManageService.ACTION_PLAY;
    private static final String ACTION_SPEECH_CLOSE = SpeechManageService.ACTION_CLOSE;

    private Context mContext;

    SpeechNotificationController(Context context) {
        mContext = context;
    }

    void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    SPEECH_CHANNEL_ID,
                    SPEECH_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.enableVibration(false);
            channel.enableLights(false);
            NotificationManager manager = mContext.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

    }

    Notification getSpeechNotification(Bitmap favicon, String title, boolean isPlay) {
        RemoteViews smallNotification = new RemoteViews(mContext.getPackageName(), R.layout.bkav_speech_notification_small);
        setNotification(smallNotification, favicon, title, isPlay);
        RemoteViews largeNotification = new RemoteViews(mContext.getPackageName(), R.layout.bkav_speech_notification_large);
        setNotification(largeNotification, favicon, title, isPlay);
        Intent intent = new Intent(mContext, ChromeActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
        Notification notification = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notification = new Notification.Builder(mContext, SPEECH_CHANNEL_ID)
                    .setCustomContentView(smallNotification)
                    .setSmallIcon(R.drawable.ic_android)
                    .setCustomBigContentView(largeNotification)
                    .setStyle(new Notification.DecoratedMediaCustomViewStyle())
                    .setContentIntent(pendingIntent)
                    .setContentTitle("content title")
                    .build();
        }
        return notification;
    }

    private void setNotification(RemoteViews notification, Bitmap favicon, String title, boolean isPlay) {
        Intent intentPlay;
        Intent intentReplay = new Intent(ACTION_SPEECH_REPLAY);
        Intent intentClose = new Intent(ACTION_SPEECH_CLOSE);

        if (isPlay)
            intentPlay = new Intent(ACTION_SPEECH_PAUSE);
        else
            intentPlay = new Intent(ACTION_SPEECH_PLAY);

        notification.setTextViewText(R.id.title_speech_notification, title);
        notification.
                setOnClickPendingIntent(
                        R.id.replay_speech_btn,
                        PendingIntent.getService(mContext, 0, intentReplay, 0)
                );

        notification.
                setOnClickPendingIntent(
                        R.id.play_speech_btn,
                        PendingIntent.getService(mContext, 0, intentPlay, 0)
                );
        notification.
                setOnClickPendingIntent(
                        R.id.close_speech_btn,
                        PendingIntent.getService(mContext, 0, intentClose, 0)
                );
        if (favicon != null) {
            notification.setImageViewBitmap(
                    R.id.thumbnail_speech_notification
                    , favicon
            );
        } else {
            notification.setImageViewResource(
                    R.id.thumbnail_speech_notification
                    , R.drawable.ic_android
            );
        }

        if (isPlay) {
            notification.setImageViewResource(
                    R.id.play_speech_btn,
                    R.drawable.ic_action_pause);
        }
    }

    void updateNotification(Bitmap favicon, String title, boolean isPlay, int id) {
        NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = getSpeechNotification(favicon, title, isPlay);
        if (manager != null) {
            manager.notify(id, notification);
        }
    }

    void cancel(int foregroundId) {
        NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(foregroundId);
        }
    }
}
