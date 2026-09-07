// app/src/main/java/vmaya/para/measuringpss/SoundManager.java
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
    private int soundIdClick = 0;    // ID для click_short_soft.mp3
    private int soundIdRecord = 0;   // ID для short_toy_squeak.mp3
    private boolean isClickLoaded = false;
    private boolean isRecordLoaded = false;

    private SoundManager(Context context) {
        this.context = context.getApplicationContext();
        initSoundPool();
        loadSounds();
    }

    public static synchronized SoundManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundManager(context);
        }
        return instance;
    }

    private void initSoundPool() {
        try {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                soundPool = new SoundPool.Builder()
                        .setMaxStreams(2)
                        .setAudioAttributes(audioAttributes)
                        .build();
            } else {
                soundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadSounds() {
        if (soundPool == null) {
            return;
        }

        // Загружаем click_short_soft.mp3
        int clickResId = context.getResources().getIdentifier(
                "click_short_soft", "raw", context.getPackageName());
        if (clickResId != 0) {
            soundIdClick = soundPool.load(context, clickResId, 1);
            if (soundIdClick != 0) {
                isClickLoaded = true;
            }
        }

        // Загружаем short_toy_squeak.mp3
        int recordResId = context.getResources().getIdentifier(
                "squeak_echo", "raw", context.getPackageName());
        if (recordResId != 0) {
            soundIdRecord = soundPool.load(context, recordResId, 1);
            if (soundIdRecord != 0) {
                isRecordLoaded = true;
            }
        }

        // Если звуки не загружены, пробуем системные
        if (!isClickLoaded && !isRecordLoaded) {
            loadSystemSounds();
        }
    }

    private void loadSystemSounds() {
        if (soundPool == null) {
            return;
        }

        String[] soundPaths = {
                "/system/media/audio/ui/KeypressStandard.ogg",
                "/system/media/audio/ui/KeypressSpacebar.ogg",
                "/system/media/audio/ui/KeypressDelete.ogg",
                "/system/media/audio/ui/KeypressReturn.ogg"
        };

        for (String path : soundPaths) {
            int id = soundPool.load(path, 1);
            if (id != 0) {
                if (soundIdClick == 0) {
                    soundIdClick = id;
                    isClickLoaded = true;
                }
                if (soundIdRecord == 0) {
                    soundIdRecord = id;
                    isRecordLoaded = true;
                }
                if (isClickLoaded && isRecordLoaded) {
                    break;
                }
            }
        }
    }

    /**
     * Воспроизводит звук клика (первый замер)
     */
    public void playClickSound() {
        // Проверяем, загружен ли звук
        if (soundPool != null && isClickLoaded && soundIdClick != 0) {
            try {
                soundPool.play(soundIdClick, 0.8f, 0.8f, 0, 0, 1.0f);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        vibrate();
    }

    /**
     * Воспроизводит звук записи в таблицу (основной звук)
     */
    public void playRecordSound() {
        // Проверяем, загружен ли звук
        if (soundPool != null && isRecordLoaded && soundIdRecord != 0) {
            try {
                soundPool.play(soundIdRecord, 0.8f, 0.8f, 0, 0, 1.0f);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Если звук не загружен, пробуем воспроизвести системный
            playSystemSound();
        }
        vibrate();
    }

    /**
     * Запасной вариант - системный звук
     */
    private void playSystemSound() {
        if (soundPool != null && soundIdClick != 0) {
            try {
                soundPool.play(soundIdClick, 0.8f, 0.8f, 0, 0, 1.0f);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void release() {
        if (soundPool != null) {
            try {
                if (soundIdClick != 0) {
                    soundPool.unload(soundIdClick);
                }
                if (soundIdRecord != 0) {
                    soundPool.unload(soundIdRecord);
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
                vibrator.vibrate(50);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void testSound() {
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
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