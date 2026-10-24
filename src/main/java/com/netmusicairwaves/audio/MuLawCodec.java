package com.netmusicairwaves.audio;

public final class MuLawCodec {
    private MuLawCodec() {
    }

    public static byte encode(short pcm) {
        int sample = pcm;
        int sign = sample >> 8 & 0x80;
        if (sign != 0) {
            sample = -sample;
        }
        if (sample > 32635) {
            sample = 32635;
        }
        sample += 132;
        int exponent = 7;
        int mask = 0x4000;
        while ((sample & mask) == 0 && exponent > 0) {
            exponent--;
            mask >>= 1;
        }
        int mantissa = sample >> (exponent + 3) & 0x0F;
        return (byte) ~(sign | exponent << 4 | mantissa);
    }

    /**
     * Standard μ-law expansion back to 16-bit PCM (inverse of {@link #encode}).
     */
    public static short decode(byte mulaw) {
        int u = ~mulaw & 0xFF;
        int exponent = (u >> 4) & 0x07;
        int mantissa = u & 0x0F;
        int magnitude = (((mantissa << 3) + 132) << exponent) - 132;
        return (short) ((u & 0x80) == 0 ? magnitude : -magnitude);
    }
}
