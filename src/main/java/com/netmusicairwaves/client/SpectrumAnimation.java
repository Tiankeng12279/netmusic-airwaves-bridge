package com.netmusicairwaves.client;

import com.netmusicairwaves.network.DeckSnapshot;
import net.minecraft.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side smoother for the 2 Hz server spectrum updates: bars rise fast
 * and fall slowly, like a real analyzer's peak-hold ballistics.
 */
public final class SpectrumAnimation {
    private static final float ATTACK_PER_SECOND = 14.0F;
    private static final float DECAY_PER_SECOND = 5.0F;

    private final float[] display = new float[DeckSnapshot.BAND_COUNT];
    private long lastMillis = -1L;

    /**
     * @return the smoothed 0..1 band levels, sampling the 16 bands up to {@code bars} columns
     */
    public float[] sample(int bars, @Nullable DeckSnapshot snapshot) {
        long now = Util.getMillis();
        float dt = lastMillis < 0 ? 0.0F : Math.min(0.25F, (now - lastMillis) / 1000.0F);
        lastMillis = now;

        boolean live = snapshot != null && snapshot.state() == DeckSnapshot.STATE_PLAYING
                && (snapshot.flags() & DeckSnapshot.FLAG_ON_AIR) != 0;
        for (int i = 0; i < display.length; i++) {
            float target = live ? snapshot.bands()[i] / 255.0F : 0.0F;
            float rate = target > display[i] ? ATTACK_PER_SECOND : DECAY_PER_SECOND;
            display[i] += (target - display[i]) * Math.min(1.0F, rate * dt);
            if (Math.abs(target - display[i]) < 0.003F) {
                display[i] = target;
            }
        }
        return resample(display, bars);
    }

    /** Linear interpolation of the 16 source bands into the display column count. */
    private static float[] resample(float[] bands, int bars) {
        if (bars == bands.length) {
            return bands;
        }
        float[] result = new float[bars];
        for (int i = 0; i < bars; i++) {
            float position = (float) i / (bars - 1) * (bands.length - 1);
            int low = (int) position;
            int high = Math.min(bands.length - 1, low + 1);
            float t = position - low;
            result[i] = bands[low] * (1.0F - t) + bands[high] * t;
        }
        return result;
    }
}
