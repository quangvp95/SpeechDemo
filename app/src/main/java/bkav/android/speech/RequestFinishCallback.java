package bkav.android.speech;

interface RequestFinishCallback {
    void onAudioFileReady(SpeechManageService.SentenceInfo sentenceInfo);
}
