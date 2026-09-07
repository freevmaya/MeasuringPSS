package vmaya.para.measuringpss;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;

public class SoundManager {
    private static SoundManager instance;
    private Context context;
    private SoundPool soundPool;
    private int soundId = 0;
    private boolean isLoaded = false;

    private SoundManager(Context context) {
        this.context = context.getApplicationContext();
        loadSound();
    }

    public static synchronized SoundManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundManager(context);
        }
        return instance;
    }

    private void loadSound() {
        try {
            int soundResId = context.getResources().getIdentifier(
                    "click",
                    "raw",
                    context.getPackageName()
            );

            if (soundResId == 0) {
                try {
                    soundResId = context.getResources().getIdentifier(
                            "click",
                            "raw",
                            "vmaya.para.measuringpss"
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (soundResId != 0) {
                loadSoundFromResource(soundResId);
            } else {
                loadSystemSound();
            }

        } catch (Exception e) {
            e.printStackTrace();
            loadSystemSound();
        }
    }

    private void loadSoundFromResource(int resId) {
        try {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)  // Изменено с USAGE_NOTIFICATION
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                soundPool = new SoundPool.Builder()
                        .setMaxStreams(1)
                        .setAudioAttributes(audioAttributes)
                        .build();
            } else {
                soundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);  // Изменено с STREAM_NOTIFICATION
            }

            soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
                if (status == 0) {
                    isLoaded = true;
                    soundId = sampleId;
                }
            });

            soundId = soundPool.load(context, resId, 1);

            int waitCount = 0;
            while (!isLoaded && waitCount < 50) {
                try {
                    Thread.sleep(10);
                    waitCount++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadSystemSound() {
        try {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)  // Изменено с USAGE_NOTIFICATION
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                soundPool = new SoundPool.Builder()
                        .setMaxStreams(1)
                        .setAudioAttributes(audioAttributes)
                        .build();
            } else {
                soundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);  // Изменено с STREAM_NOTIFICATION
            }

            String[] soundPaths = {
                    "/system/media/audio/ui/KeypressStandard.ogg",
                    "/system/media/audio/ui/KeypressSpacebar.ogg",
                    "/system/media/audio/ui/KeypressDelete.ogg",
                    "/system/media/audio/ui/KeypressReturn.ogg"
            };

            for (String path : soundPaths) {
                soundId = soundPool.load(path, 1);
                if (soundId != 0) {
                    soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
                        if (status == 0) {
                            isLoaded = true;
                            soundId = sampleId;
                        }
                    });
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (isLoaded) {
                        return;
                    }
                }
            }

            isLoaded = false;

        } catch (Exception e) {
            e.printStackTrace();
            isLoaded = false;
        }
    }

    public void playClickSound() {
        if (soundPool != null && isLoaded && soundId != 0) {
            try {
                soundPool.play(soundId, 0.8f, 0.8f, 0, 0, 1.0f);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        vibrate();
    }

    public void release() {
        if (soundPool != null) {
            try {
                if (soundId != 0) {
                    soundPool.unload(soundId);
                }
                soundPool.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            soundPool = null;
        }
    }

    private void vibrate() {
        try {
            android.os.Vibrator vibrator = (android.os.Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(100);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void testSound() {
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                // Изменено на STREAM_MUSIC
                int volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                android.util.Log.d("SoundManager", "Music volume: " + volume + "/" + maxVolume);

                if (volume == 0) {
                    android.util.Log.d("SoundManager", "Music volume is ZERO!");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}