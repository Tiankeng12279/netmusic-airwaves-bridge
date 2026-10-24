package com.netmusicairwaves;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class Config {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue BROADCAST_GAIN;
    public static final ModConfigSpec.IntValue MAX_TRACK_SECONDS;
    public static final ModConfigSpec.IntValue MAX_DOWNLOAD_MB;
    public static final ModConfigSpec.BooleanValue ALLOW_HTTP;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_HOSTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("broadcast");
        BROADCAST_GAIN = builder
                .comment("Volume passed to Airwaves. Airwaves still applies distance, quality and weather effects.")
                .defineInRange("broadcastGain", 0.8D, 0.0D, 1.0D);
        MAX_TRACK_SECONDS = builder
                .comment("Maximum amount of audio decoded and broadcast from one CD.")
                .defineInRange("maxTrackSeconds", 900, 30, 3600);
        builder.pop();

        builder.push("network");
        ALLOW_HTTP = builder
                .comment("Allow HTTP audio URLs. NetEase's CDN redirect currently uses HTTP, so this defaults to true.")
                .define("allowHttp", true);
        MAX_DOWNLOAD_MB = builder
                .comment("Maximum audio download size in MiB.")
                .defineInRange("maxDownloadMb", 64, 1, 256);
        ALLOWED_HOSTS = builder
                .comment("Allowed audio host names. '*' allows any public host. Wildcards such as '*.music.126.net' only match subdomains.")
                .defineList("allowedHosts", List.of(
                        "music.163.com",
                        "*.music.163.com",
                        "*.music.126.net",
                        "*.126.net",
                        "*.163.com"
                ), () -> "", value -> value instanceof String);
        builder.pop();

        SPEC = builder.build();
    }

    private Config() {
    }
}
