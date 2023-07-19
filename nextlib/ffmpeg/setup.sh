#!/bin/bash

# Variables
BASE_DIR=$(cd "$(dirname "$0")" && pwd)
NDK_HOME=$ANDROID_NDK_HOME
FFMPEG_VERSION=6.0
VPX_VERSION=1.13.0
ANDROID_ABIS="x86 x86_64 armeabi-v7a arm64-v8a"
ENABLED_DECODERS="vorbis opus flac alac pcm_mulaw pcm_alaw mp3 amrnb amrwb aac ac3 eac3 dca mlp truehd h264 hevc mpeg2video mpegvideo libvpx_vp8 libvpx_vp9"
HOST_PLATFORM="linux-x86_64"
BUILD_DIR=$BASE_DIR/build
OUTPUT_DIR=$BASE_DIR/output
FFMPEG_DIR=$BASE_DIR/ffmpeg-$FFMPEG_VERSION
VPX_DIR=$BASE_DIR/libvpx-$VPX_VERSION
JOBS=$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || sysctl -n hw.pysicalcpu || echo 4)
TOOLCHAIN_PREFIX="${NDK_HOME}/toolchains/llvm/prebuilt/${HOST_PLATFORM}"

# Set up host platform variables
case "$OSTYPE" in
    darwin*) HOST_PLATFORM="darwin-x86_64" ;;
    linux*) HOST_PLATFORM="linux-x86_64" ;;
    msys)
        case "$(uname -m)" in
        x86_64) HOST_PLATFORM="windows-x86_64" ;;
        i686) HOST_PLATFORM="windows" ;;
        esac
        ;;
esac

cd "$BASE_DIR" || exit 1
# Download libvpx source code if it doesn't exist
if [[ ! -d "$VPX_DIR" ]]; then
    # Download FFmpeg source code
    echo "Downloading Vpx source code of version $VPX_VERSION..."
    VPX_FILE=libvpx-$VPX_VERSION.tar.gz
    wget https://github.com/webmproject/libvpx/archive/refs/tags/v$VPX_VERSION.tar.gz -O $VPX_FILE
    tar -zxf $VPX_FILE
    rm $VPX_FILE
fi
cd "$VPX_DIR" || exit 1

VPX_AS=${TOOLCHAIN_PREFIX}/bin/llvm-as

for ABI in $ANDROID_ABIS; do
      # Set up environment variables
      case $ABI in
      armeabi-v7a)
          EXTRA_BUILD_FLAGS="--force-target=armv7-android-gcc --disable-neon"
          TOOLCHAIN=armv7a-linux-androideabi21-
          ;;
      arm64-v8a)
          EXTRA_BUILD_FLAGS="--force-target=armv8-android-gcc"
          TOOLCHAIN=aarch64-linux-android21-
          ;;
      x86)
          EXTRA_BUILD_FLAGS="--force-target=x86-android-gcc --disable-sse2 --disable-sse3 --disable-ssse3 --disable-sse4_1 --disable-avx --disable-avx2 --enable-pic"
          VPX_AS=${TOOLCHAIN_PREFIX}/bin/yasm
          TOOLCHAIN=i686-linux-android21-
          ;;
      x86_64)
          EXTRA_BUILD_FLAGS="--force-target=x86_64-android-gcc --disable-sse2 --disable-sse3 --disable-ssse3 --disable-sse4_1 --disable-avx --disable-avx2 --enable-pic --disable-neon --disable-neon-asm"
          VPX_AS=${TOOLCHAIN_PREFIX}/bin/yasm
          TOOLCHAIN=x86_64-linux-android21-
          ;;
      *)
          echo "Unsupported architecture: $ABI"
          exit 1
          ;;
      esac

      [ -z "${TARGET_TRIPLE_MACHINE_CC}" ] && TARGET_TRIPLE_MACHINE_CC=${TARGET_TRIPLE_MACHINE_ARCH}

      CC=${TOOLCHAIN_PREFIX}/bin/${TOOLCHAIN}clang \
      CXX=${CC}++ \
      LD=${CC} \
      AR=${TOOLCHAIN_PREFIX}/bin/llvm-ar \
      AS=${VPX_AS} \
      STRIP=${TOOLCHAIN_PREFIX}/bin/llvm-strip \
      NM=${TOOLCHAIN_PREFIX}/bin/llvm-nm \
      ./configure \
          --prefix=$BUILD_DIR/external/$ABI \
          --libc="${TOOLCHAIN_PREFIX}/sysroot" \
          --enable-vp8 \
          --enable-vp9 \
          --enable-static \
          --disable-shared \
          --disable-examples \
          --disable-docs \
          --enable-realtime-only \
          --enable-install-libs \
          --enable-multithread \
          --disable-webm-io \
          --disable-libyuv \
          --enable-small \
          --enable-better-hw-compatibility \
          --disable-runtime-cpu-detect \
          ${EXTRA_BUILD_FLAGS}

      make clean
      make -j"$JOBS"
      make install
done

cd "$BASE_DIR" || exit 1
# Download FFmpeg source code if it doesn't exist
if [[ ! -d "$FFMPEG_DIR" ]]; then
    # Download FFmpeg source code
    echo "Downloading FFmpeg source code of version $FFMPEG_VERSION..."
    FFMPEG_FILE=ffmpeg-$FFMPEG_VERSION.tar.gz
    wget https://ffmpeg.org/releases/ffmpeg-$FFMPEG_VERSION.tar.gz -O $FFMPEG_FILE
    tar -zxf $FFMPEG_FILE
    rm $FFMPEG_FILE
fi
cd "$FFMPEG_DIR" || exit 1

EXTRA_BUILD_CONFIGURATION_FLAGS=""
COMMON_OPTIONS=""

# Add enabled decoders to FFmpeg build configuration
for decoder in $ENABLED_DECODERS; do
    COMMON_OPTIONS="${COMMON_OPTIONS} --enable-decoder=${decoder}"
done



# Build FFmpeg for each architecture and platform
for ABI in $ANDROID_ABIS; do

    # Set up environment variables
    case $ABI in
    armeabi-v7a)
        TOOLCHAIN=armv7a-linux-androideabi21-
        CPU=armv7-a
        ARCH=arm
        ;;
    arm64-v8a)
        TOOLCHAIN=aarch64-linux-android21-
        CPU=armv8-a
        ARCH=aarch64
        ;;
    x86)
        TOOLCHAIN=i686-linux-android21-
        CPU=i686
        ARCH=i686
        EXTRA_BUILD_CONFIGURATION_FLAGS=--disable-asm
        ;;
    x86_64)
        TOOLCHAIN=x86_64-linux-android21-
        CPU=x86_64
        ARCH=x86_64
        ;;
    *)
        echo "Unsupported architecture: $ABI"
        exit 1
        ;;
    esac

    # Referencing dependencies without pkgconfig
    DEP_CFLAGS="-I$BUILD_DIR/external/$ABI/include"
    DEP_LD_FLAGS="-L$BUILD_DIR/external/$ABI/lib"

    # Configure FFmpeg build
    ./configure \
        --prefix=$BUILD_DIR/$ABI \
        --enable-cross-compile \
        --arch=$ARCH \
        --cpu=$CPU \
        --cross-prefix="${TOOLCHAIN_PREFIX}/bin/$TOOLCHAIN" \
        --nm="${TOOLCHAIN_PREFIX}/bin/llvm-nm" \
        --ar="${TOOLCHAIN_PREFIX}/bin/llvm-ar" \
        --ranlib="${TOOLCHAIN_PREFIX}/bin/llvm-ranlib" \
        --strip="${TOOLCHAIN_PREFIX}/bin/llvm-strip" \
        --extra-cflags="-O3 -fPIC $DEP_CFLAGS" \
        --extra-ldflags="$DEP_LD_FLAGS" \
        --pkg-config="$(which pkg-config)" \
        --target-os=android \
        --enable-shared \
        --disable-static \
        --disable-doc \
        --disable-programs \
        --disable-everything \
        --disable-vulkan \
        --disable-avdevice \
        --disable-avformat \
        --disable-postproc \
        --disable-avfilter \
        --disable-symver \
        --enable-swresample \
        --enable-libvpx \
        --extra-ldexeflags=-pie \
        ${EXTRA_BUILD_CONFIGURATION_FLAGS} \
        ${COMMON_OPTIONS}

    # Build FFmpeg
    echo "Building FFmpeg for $ARCH..."
    make clean
    make -j"$JOBS"
    make install

    OUTPUT_LIB=${OUTPUT_DIR}/lib/${ABI}
    mkdir -p "${OUTPUT_LIB}"
    cp "${BUILD_DIR}"/"${ABI}"/lib/*.so "${OUTPUT_LIB}"

    OUTPUT_HEADERS=${OUTPUT_DIR}/include/${ABI}
    mkdir -p "${OUTPUT_HEADERS}"
    cp -r "${BUILD_DIR}"/"${ABI}"/include/* "${OUTPUT_HEADERS}"

done
