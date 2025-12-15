package io.github.anilbeesetti.nextlib.media3ext.ffextractor;

import androidx.annotation.Nullable;

/** Track metadata as discovered by FFmpeg demuxing. */
public final class FfmpegTrackInfo {
  public final int trackType;
  public final String sampleMimeType;
  @Nullable public final String codecs;
  @Nullable public final String language;
  public final int width;
  public final int height;
  public final int channelCount;
  public final int sampleRate;
  public final int averageBitrate;
  public final int rotationDegrees;
  @Nullable public final byte[] extraData;

  public FfmpegTrackInfo(
      int trackType,
      String sampleMimeType,
      @Nullable String codecs,
      @Nullable String language,
      int width,
      int height,
      int channelCount,
      int sampleRate,
      int averageBitrate,
      int rotationDegrees,
      @Nullable byte[] extraData) {
    this.trackType = trackType;
    this.sampleMimeType = sampleMimeType;
    this.codecs = codecs;
    this.language = language;
    this.width = width;
    this.height = height;
    this.channelCount = channelCount;
    this.sampleRate = sampleRate;
    this.averageBitrate = averageBitrate;
    this.rotationDegrees = rotationDegrees;
    this.extraData = extraData;
  }
}

