package com.netmusicairwaves.audio;

/**
 * Turns a window of μ-law radio frames into normalized spectrum band levels.
 * Pure Java on purpose: it can be exercised outside the game for testing.
 */
public final class SpectrumAnalyzer {
    public static final int WINDOW_SAMPLES = 2048;
    public static final int FFT_SIZE = 2048;
    public static final float LOW_HZ = 40.0F;
    private static final float RANGE_DB = 42.0F;

    private SpectrumAnalyzer() {
    }

    /**
     * @param mulaw     μ-law encoded samples, one byte per sample
     * @param bandCount number of log-spaced output bands
     * @return bandCount levels in 0..1, or all-zero when the window is too short
     */
    public static float[] bands(byte[] mulaw, int sampleRate, int bandCount) {
        float[] result = new float[bandCount];
        if (mulaw == null || mulaw.length < FFT_SIZE) {
            return result;
        }

        int offset = mulaw.length - FFT_SIZE;
        float[] real = new float[FFT_SIZE];
        float[] imag = new float[FFT_SIZE];
        double squared = 0.0D;
        for (int i = 0; i < FFT_SIZE; i++) {
            short pcm = MuLawCodec.decode(mulaw[offset + i]);
            squared += (double) pcm * pcm;
            // Hann window keeps spectral leakage from smearing the bands.
            float window = 0.5F - 0.5F * (float) Math.cos(2.0F * (float) Math.PI * i / (FFT_SIZE - 1));
            real[i] = pcm * window;
        }
        double rms = Math.sqrt(squared / FFT_SIZE);
        if (rms < 64.0D) {
            // Digital silence or μ-law idle noise: show a flat line, not a full-scale AGC wall.
            return result;
        }

        fft(real, imag);

        // Log-spaced band edges from LOW_HZ up to Nyquist.
        float nyquist = sampleRate * 0.5F;
        double logLow = Math.log(LOW_HZ);
        double logHigh = Math.log(Math.max(LOW_HZ * 2.0F, nyquist * 0.97F));
        double binsPerHz = FFT_SIZE / (double) sampleRate;
        double peak = 1e-9F;
        double[] bandMax = new double[bandCount];
        for (int bin = 1; bin < FFT_SIZE / 2; bin++) {
            double hz = bin / binsPerHz;
            if (hz < LOW_HZ) {
                continue;
            }
            int band = (int) ((Math.log(hz) - logLow) / (logHigh - logLow) * bandCount);
            if (band < 0 || band >= bandCount) {
                continue;
            }
            double magnitude = Math.sqrt(real[bin] * real[bin] + imag[bin] * imag[bin]);
            if (magnitude > bandMax[band]) {
                bandMax[band] = magnitude;
            }
            if (magnitude > peak) {
                peak = magnitude;
            }
        }

        for (int i = 0; i < bandCount; i++) {
            if (bandMax[i] <= 0.0D) {
                continue;
            }
            // Per-window AGC: each band is measured against the window peak in dB,
            // so quiet tracks still produce a usable display.
            double db = 20.0 * Math.log10(bandMax[i] / peak);
            // The loudness scale fades the whole display out as the signal gets quiet.
            double loudness = Math.min(1.0D, rms / 1024.0D);
            result[i] = (float) Math.max(0.0D, Math.min(1.0D, (1.0D + db / RANGE_DB) * loudness));
        }
        return result;
    }

    /** In-place iterative radix-2 Cooley-Tukey FFT. */
    private static void fft(float[] real, float[] imag) {
        int n = real.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                float t = real[i];
                real[i] = real[j];
                real[j] = t;
                t = imag[i];
                imag[i] = imag[j];
                imag[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            float angle = (float) (-2.0F * Math.PI / len);
            float wReal = (float) Math.cos(angle);
            float wImag = (float) Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                float curReal = 1.0F;
                float curImag = 0.0F;
                for (int k = 0; k < len / 2; k++) {
                    int even = i + k;
                    int odd = even + len / 2;
                    float oddReal = real[odd] * curReal - imag[odd] * curImag;
                    float oddImag = real[odd] * curImag + imag[odd] * curReal;
                    real[odd] = real[even] - oddReal;
                    imag[odd] = imag[even] - oddImag;
                    real[even] += oddReal;
                    imag[even] += oddImag;
                    float nextReal = curReal * wReal - curImag * wImag;
                    curImag = curReal * wImag + curImag * wReal;
                    curReal = nextReal;
                }
            }
        }
    }
}
