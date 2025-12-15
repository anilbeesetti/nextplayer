package io.github.anilbeesetti.nextlib.media3ext.ffextractor;

/** MIME types for codecs typically found in ASF/WMV containers. */
public final class WmvMimeTypes {
  private WmvMimeTypes() {}

  // Container (used only for detection, not decoding).
  public static final String VIDEO_X_MS_WMV = "video/x-ms-wmv";
  public static final String VIDEO_X_MS_ASF = "video/x-ms-asf";
  public static final String APPLICATION_VND_MS_ASF = "application/vnd.ms-asf";

  // Video codecs.
  public static final String VIDEO_X_MS_WMV3 = "video/x-ms-wmv3";
  public static final String VIDEO_X_MS_WMV2 = "video/x-ms-wmv2";
  public static final String VIDEO_X_MS_MSMPEG4V3 = "video/x-ms-msmpeg4v3";

  // Audio codecs.
  public static final String AUDIO_X_MS_WMAV1 = "audio/x-ms-wmav1";
  public static final String AUDIO_X_MS_WMAV2 = "audio/x-ms-wmav2";
  public static final String AUDIO_X_MS_WMAPRO = "audio/x-ms-wmapro";
  public static final String AUDIO_X_MS_WMALOSSLESS = "audio/x-ms-wmalossless";
}

