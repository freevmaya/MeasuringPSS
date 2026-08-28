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

    /**
     * Загружает звук из ресурсов
     */
    private void loadSound() {
        try {
            // Пытаемся загрузить из res/raw/click.mp3
            // ID ресурса будет сгенерирован автоматически как R.raw.click
            int soundResId = context.getResources().getIdentifier(
                    "click",  // имя файла без расширения
                    "raw",    // тип ресурса
                    context.getPackageName()
            );

            // Если файл не найден через getIdentifier, пробуем прямой доступ
            if (soundResId == 0) {
                // Пробуем получить ресурс через R класс (если доступен)
                try {
                    // Это сработает если файл есть в res/raw/
                    soundResId = context.getResources().getIdentifier(
                            "click",
                            "raw",
                            "vmaya.para.measuringpss"  // ваш package name
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (soundResId != 0) {
                loadSoundFromResource(soundResId);
            } else {
                // Если файл не найден, пробуем системный звук
                loadSystemSound();
            }

        } catch (Exception e) {
            e.printStackTrace();
            loadSystemSound();
        }
    }

    /**
     * Загружает звук из ресурса по ID
     */
    private void loadSoundFromResource(int resId) {
        try {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                soundPool = new SoundPool.Builder()
                        .setMaxStreams(1)
                        .setAudioAttributes(audioAttributes)
                        .build();
            } else {
                soundPool = new SoundPool(1, AudioManager.STREAM_NOTIFICATION, 0);
            }

            soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
                if (status == 0) {
                    isLoaded = true;
                    soundId = sampleId;
                }
            });

            soundId = soundPool.load(context, resId, 1);

            // Ждем загрузки (максимум 500 мс)
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

    /**
     * Загружает системный звук как fallback
     */
    private void loadSystemSound() {
        try {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                soundPool = new SoundPool.Builder()
                        .setMaxStreams(1)
                        .setAudioAttributes(audioAttributes)
                        .build();
            } else {
                soundPool = new SoundPool(1, AudioManager.STREAM_NOTIFICATION, 0);
            }

            // Пробуем разные системные звуки
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
                    // Ждем загрузки
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

            // Если ничего не загрузилось, помечаем как не загруженный
            isLoaded = false;

        } catch (Exception e) {
            e.printStackTrace();
            isLoaded = false;
        }
    }

    /**
     * Воспроизводит звук
     */
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

    /**
     * Освобождает ресурсы
     */
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
                // Проверяем громкость
                int volume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
                int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
                android.util.Log.d("SoundManager", "Notification volume: " + volume + "/" + maxVolume);

                if (volume == 0) {
                    android.util.Log.d("SoundManager", "Volume is ZERO!");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}