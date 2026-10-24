package com.netmusicairwaves.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public final class AudioDecoder {
    public static final int SAMPLE_RATE = 16000;
    public static final int SAMPLES_PER_FRAME = 800;

    private AudioDecoder() {
    }

    /**
     * Decodes a complete audio file into 50 ms Airwaves μ-law frames.
     */
    public static byte[] decode(byte[] audio, int maxSeconds) throws Exception {
        if (audio == null || audio.length == 0) {
            throw new IOException("Audio data is empty");
        }
        int maxSamples = maxSeconds * SAMPLE_RATE;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (AudioInputStream raw = AudioSystem.getAudioInputStream(
                new BufferedInputStream(new ByteArrayInputStream(audio), 1 << 20))) {
            AudioFormat source = raw.getFormat();
            int bits = source.getSampleSizeInBits() == AudioSystem.NOT_SPECIFIED
                    ? 16 : source.getSampleSizeInBits();
            int channels = source.getChannels();
            if (source.getSampleRate() == AudioSystem.NOT_SPECIFIED || channels <= 0 || bits <= 0 || bits % 8 != 0) {
                throw new IOException("Unsupported audio format: " + source);
            }
            int frameSize = (bits / 8) * channels;
            AudioFormat sourcePcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    source.getSampleRate(), bits, channels, frameSize, source.getSampleRate(), false);

            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(sourcePcm, raw)) {
                AudioFormat target = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                try (AudioInputStream converted = AudioSystem.getAudioInputStream(target, pcm)) {
                    byte[] buffer = new byte[8192];
                    short[] frame = new short[SAMPLES_PER_FRAME];
                    int framePosition = 0;
                    int totalSamples = 0;
                    int read;

                    while (totalSamples < maxSamples) {
                        read = converted.read(buffer);
                        if (read < 0) {
                            break;
                        }
                        if (read == 0) {
                            continue;
                        }
                        for (int i = 0; i + 1 < read; i += 2) {
                            frame[framePosition++] = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
                            if (framePosition == SAMPLES_PER_FRAME) {
                                writeFrame(output, frame);
                                totalSamples += SAMPLES_PER_FRAME;
                                framePosition = 0;
                                if (totalSamples >= maxSamples) {
                                    break;
                                }
                            }
                        }
                    }

                    if (framePosition > 0 && totalSamples < maxSamples) {
                        Arrays.fill(frame, framePosition, SAMPLES_PER_FRAME, (short) 0);
                        writeFrame(output, frame);
                    }
                }
            }
        }
        return output.toByteArray();
    }

    private static void writeFrame(ByteArrayOutputStream output, short[] frame) {
        for (short sample : frame) {
            output.write(MuLawCodec.encode(sample));
        }
    }
}
