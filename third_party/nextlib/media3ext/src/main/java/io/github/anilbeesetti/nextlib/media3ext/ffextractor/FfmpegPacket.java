package io.github.anilbeesetti.nextlib.media3ext.ffextractor;

import androidx.annotation.Nullable;

/** A demuxed packet from FFmpeg (one sample for a given track). */
public final class FfmpegPacket {
  public final int trackIndex;
  public final long timeUs;
  public final int flags;
  @Nullable public final byte[] data;

  public FfmpegPacket(int trackIndex, long timeUs, int flags, @Nullable byte[] data) {
    this.trackIndex = trackIndex;
    this.timeUs = timeUs;
    this.flags = flags;
    this.data = data;
  }
}

