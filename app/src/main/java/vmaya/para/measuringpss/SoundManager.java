// app/src/main/java/vmaya/para/measuringpss/SoundManager.java
package vmaya.para.measuringpss;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Vibrator;

public class SoundManager {
    private static SoundManager instance;
    private Context context;
    private SoundPool soundPool;
    private int soundIdClick = 0;    // ID для click_short_soft.mp3
    private int soundIdRecord = 0;   // ID для squeak_echo.mp3
    private boolean isClickLoaded = false;
    private boolean isRecordLoaded = false;
    private boolean isInitialized = false;

    private SoundManager(Context context) {
        this.context = context.getApplicationContext();
        initSoundPool();
        loadSounds();
    }

    public static synchronized SoundManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundManager(context);
        } else {
            // Если экземпляр уже существует, но SoundPool был уничтожен — пересоздаем
            if (!instance.isInitialized) {
                instance.reinitialize();
            }
        }
        return instance;
    }

    /**
     * Полная переинициализация SoundManager
     */
    private void reinitialize() {
        release();
        initSoundPool();
        loadSounds();
    }

    /**
     * Проверяет, инициализирован ли SoundManager и загружены ли звуки
     */
    private boolean isReady() {
        if (soundPool == null) {
            return false;
        }
        // Проверяем, не был ли soundPool уничтожен
        try {
            // Пробуем получить информацию о звуке
            if (soundIdClick != 0) {
                // SoundPool работает
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return isClickLoaded || isRecordLoaded;
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

            // Устанавливаем слушатель загрузки, чтобы отслеживать состояние
            if (soundPool != null) {
                soundPool.setOnLoadCompleteListener((soundPool1, sampleId, status) -> {
                    if (status == 0) {
                        if (sampleId == soundIdClick) {
                            isClickLoaded = true;
                        }
                        if (sampleId == soundIdRecord) {
                            isRecordLoaded = true;
                        }
                        if (isClickLoaded || isRecordLoaded) {
                            isInitialized = true;
                        }
                    }
                });
            }

            isInitialized = false;
        } catch (Exception e) {
            e.printStackTrace();
            soundPool = null;
            isInitialized = false;
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
                // Не устанавливаем isClickLoaded = true здесь, ждем onLoadComplete
            }
        }

        // Загружаем squeak_echo.mp3
        int recordResId = context.getResources().getIdentifier(
                "squeak_echo", "raw", context.getPackageName());
        if (recordResId != 0) {
            soundIdRecord = soundPool.load(context, recordResId, 1);
            if (soundIdRecord != 0) {
                // Не устанавливаем isRecordLoaded = true здесь, ждем onLoadComplete
            }
        }

        // Если загрузка не удалась, пробуем системные звуки
        if (soundIdClick == 0 && soundIdRecord == 0) {
            loadSystemSounds();
            // Если системные звуки загружены, считаем инициализацию успешной
            if (isClickLoaded || isRecordLoaded) {
                isInitialized = true;
            }
        }

        // Если звуки загружаются асинхронно, они будут помечены как загруженные
        // через onLoadCompleteListener. Устанавливаем таймаут, чтобы не ждать вечно.
        handler.postDelayed(() -> {
            if (!isInitialized) {
                // Если звуки все еще не загружены, пробуем системные
                if (!isClickLoaded && !isRecordLoaded) {
                    loadSystemSounds();
                    if (isClickLoaded || isRecordLoaded) {
                        isInitialized = true;
                    }
                }
                // Если все равно не загружены, считаем, что инициализация не удалась
                if (!isInitialized) {
                    // Создаем заглушку — будем использовать вибрацию
                    isInitialized = true; // Чтобы не пытаться перезагружать постоянно
                }
            }
        }, 2000);
    }

    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

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
        ensureReady();
        if (soundPool != null && isClickLoaded && soundIdClick != 0) {
            try {
                soundPool.play(soundIdClick, 0.8f, 0.8f, 0, 0, 1.0f);
            } catch (Exception e) {
                e.printStackTrace();
                // Если произошла ошибка, пробуем перезагрузить
                reloadSounds();
            }
        } else {
            // Если звук не загружен, пробуем перезагрузить и воспроизвести
            reloadSounds();
            if (soundPool != null && isClickLoaded && soundIdClick != 0) {
                try {
                    soundPool.play(soundIdClick, 0.8f, 0.8f, 0, 0, 1.0f);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        vibrate();
    }

    /**
     * Воспроизводит звук записи в таблицу (основной звук)
     */
    public void playRecordSound() {
        ensureReady();
        if (soundPool != null && isRecordLoaded && soundIdRecord != 0) {
            try {
                soundPool.play(soundIdRecord, 0.8f, 0.8f, 0, 0, 1.0f);
            } catch (Exception e) {
                e.printStackTrace();
                reloadSounds();
            }
        } else {
            // Если звук не загружен, пробуем перезагрузить и воспроизвести
            reloadSounds();
            if (soundPool != null && isRecordLoaded && soundIdRecord != 0) {
                try {
                    soundPool.play(soundIdRecord, 0.8f, 0.8f, 0, 0, 1.0f);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        vibrate();
    }

    /**
     * Проверяет, что SoundManager готов к работе
     */
    private void ensureReady() {
        if (soundPool == null || !isInitialized) {
            reinitialize();
        }
    }

    /**
     * Перезагружает звуки
     */
    private void reloadSounds() {
        if (soundPool == null) {
            return;
        }

        // Перезагружаем только если звуки не загружены
        if (!isClickLoaded || !isRecordLoaded) {
            try {
                if (soundIdClick != 0) {
                    soundPool.unload(soundIdClick);
                }
                if (soundIdRecord != 0) {
                    soundPool.unload(soundIdRecord);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            isClickLoaded = false;
            isRecordLoaded = false;
            loadSounds();
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
        isInitialized = false;
        isClickLoaded = false;
        isRecordLoaded = false;
    }

    private void vibrate() {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(50);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void testSound() {
        ensureReady();
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