#!/bin/bash

# Variables
BASE_DIR=$(cd "$(dirname "$0")" && pwd)
NDK_HOME=$ANDROID_NDK_HOME
FFMPEG_VERSION=6.0
ANDROID_ABIS="x86 x86_64 armeabi-v7a arm64-v8a"
ENABLED_DECODERS="vorbis opus flac alac pcm_mulaw pcm_alaw mp3 amrnb amrwb aac ac3 eac3 dca mlp truehd"
HOST_PLATFORM="linux-x86_64"
BUILD_DIR=$BASE_DIR/build
OUTPUT_DIR=$BASE_DIR/output
FFMPEG_DIR=$BASE_DIR/ffmpeg-$FFMPEG_VERSION
JOBS=$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || sysctl -n hw.pysicalcpu || echo 4)
case "$OSTYPE" in
  darwin*)  HOST_PLATFORM="darwin-x86_64" ;;
  linux*)   HOST_PLATFORM="linux-x86_64" ;;
esac

cd "$BASE_DIR" || exit 1

if [[ ! -d "$FFMPEG_DIR" ]]; then
    # Download FFmpeg source code
    echo "Downloading FFmpeg source code of version $FFMPEG_VERSION..."
    curl -O https://ffmpeg.org/releases/ffmpeg-$FFMPEG_VERSION.tar.gz
    tar -zxvf ffmpeg-$FFMPEG_VERSION.tar.gz
    rm ffmpeg-$FFMPEG_VERSION.tar.gz
fi
cd "$FFMPEG_DIR" || exit 1

TOOLCHAIN_PREFIX="${NDK_HOME}/toolchains/llvm/prebuilt/${HOST_PLATFORM}/bin"
EXTRA_BUILD_CONFIGURATION_FLAGS=""
COMMON_OPTIONS=""

ls -la "${TOOLCHAIN_PREFIX}"

for decoder in $ENABLED_DECODERS; do
    COMMON_OPTIONS="${COMMON_OPTIONS} --enable-decoder=${decoder}"
done

if [[ ! -d "$OUTPUT_DIR" ]]; then

    # Build FFmpeg for each architecture and platform
    for ABI in $ANDROID_ABIS; do

        # Set up environment variables
        case $ABI in
        armeabi-v7a)
            TOOLCHAIN=armv7a-linux-androideabi16-
            CPU=armv7-a
            ARCH=arm
            ;;
        arm64-v8a)
            TOOLCHAIN=aarch64-linux-android21-
            CPU=armv8-a
            ARCH=aarch64
            ;;
        x86)
            TOOLCHAIN=i686-linux-android16-
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

        # Configure FFmpeg build
        ./configure \
            --prefix=$BUILD_DIR/$ABI \
            --enable-cross-compile \
            --arch=$ARCH \
            --cpu=$CPU \
            --cross-prefix="${TOOLCHAIN_PREFIX}/$TOOLCHAIN" \
            --nm="${TOOLCHAIN_PREFIX}/llvm-nm" \
            --ar="${TOOLCHAIN_PREFIX}/llvm-ar" \
            --ranlib="${TOOLCHAIN_PREFIX}/llvm-ranlib" \
            --strip="${TOOLCHAIN_PREFIX}/llvm-strip" \
            --target-os=android \
            --enable-shared \
            --disable-static \
            --disable-doc \
            --disable-programs \
            --disable-everything \
            --disable-avdevice \
            --disable-avformat \
            --disable-swscale \
            --disable-postproc \
            --disable-avfilter \
            --disable-symver \
            --enable-swresample \
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
fi
