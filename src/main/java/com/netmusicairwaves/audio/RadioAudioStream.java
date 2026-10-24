package com.netmusicairwaves.audio;

import com.github.tartaricacid.netmusic.api.resolver.MusicPlayResolverManager;
import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import com.netmusicairwaves.Config;
import com.netmusicairwaves.NetMusicAirwaves;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RadioAudioStream implements AutoCloseable {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "NetMusic-Airwaves-Audio");
        thread.setDaemon(true);
        return thread;
    });

    public enum State {
        RESOLVING,
        DOWNLOADING,
        DECODING,
        READY,
        FAILED,
        CANCELLED
    }

    private final ItemMusicCD.SongInfo song;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile State state = State.RESOLVING;
    private volatile String error;
    private volatile byte[] frames;
    @Nullable
    private volatile Future<?> task;

    public RadioAudioStream(ItemMusicCD.SongInfo song) {
        this.song = song;
    }

    public void start() {
        if (task == null) {
            task = EXECUTOR.submit(this::run);
        }
    }

    public String getSongName() {
        return song.songName == null ? "" : song.songName;
    }

    public boolean isReady() {
        return frames != null && state == State.READY;
    }

    public boolean isFailed() {
        return state == State.FAILED;
    }

    @Nullable
    public String getError() {
        return error;
    }

    public int getFrameCount() {
        byte[] data = frames;
        return data == null ? 0 : data.length / AudioDecoder.SAMPLES_PER_FRAME;
    }

    @Nullable
    public byte[] getFrame(int index) {
        byte[] data = frames;
        if (data == null || index < 0 || index >= getFrameCount()) {
            return null;
        }
        int offset = index * AudioDecoder.SAMPLES_PER_FRAME;
        return Arrays.copyOfRange(data, offset, offset + AudioDecoder.SAMPLES_PER_FRAME);
    }

    private void run() {
        try {
            ItemMusicCD.SongInfo resolved = MusicPlayResolverManager.resolve(song.clone())
                    .get(30, TimeUnit.SECONDS);
            if (cancelled.get()) {
                return;
            }
            if (resolved == null || resolved.songUrl == null || resolved.songUrl.isBlank()) {
                throw new IOException("NetMusic returned no audio URL");
            }

            state = State.DOWNLOADING;
            URI uri = URI.create(resolved.songUrl.trim());
            int maxBytes = Config.MAX_DOWNLOAD_MB.get() * 1024 * 1024;
            byte[] audio = SafeAudioDownloader.download(uri, maxBytes);
            if (cancelled.get()) {
                return;
            }

            state = State.DECODING;
            byte[] encoded = AudioDecoder.decode(audio, Config.MAX_TRACK_SECONDS.get());
            if (cancelled.get()) {
                return;
            }
            if (encoded.length < AudioDecoder.SAMPLES_PER_FRAME) {
                throw new IOException("Decoded audio was empty or too short");
            }

            frames = encoded;
            state = State.READY;
            NetMusicAirwaves.LOGGER.info("Decoded '{}' ({} frames) for Airwaves", song.songName,
                    getFrameCount());
        } catch (Throwable throwable) {
            if (cancelled.get()) {
                state = State.CANCELLED;
                return;
            }
            state = State.FAILED;
            error = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
            NetMusicAirwaves.LOGGER.error("Failed to prepare NetMusic audio for Airwaves", throwable);
        }
    }

    @Override
    public void close() {
        cancelled.set(true);
        state = State.CANCELLED;
        Future<?> currentTask = task;
        if (currentTask != null) {
            currentTask.cancel(true);
        }
    }
}
