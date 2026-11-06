set -eu
apt-get update -qq
apt-get install -y -qq unzip make xz-utils gcc libc6-dev >/dev/null
cd /tmp
unzip -q /work/ndk.zip
tar xf /work/ffmpeg-8.0.1.tar.xz
mkdir -p /work/out
for pair in aarch64:aarch64-linux-android x86_64:x86_64-linux-android; do
    arch=${pair%:*}
    triple=${pair#*:}
    mkdir "build-$arch"
    cd "build-$arch"
    bin=/tmp/android-ndk-r28c/toolchains/llvm/prebuilt/linux-x86_64/bin
    ../ffmpeg-8.0.1/configure --target-os=android --arch="$arch" --enable-cross-compile \
        --cc="$bin/${triple}24-clang" --ar="$bin/llvm-ar" --nm="$bin/llvm-nm" --strip="$bin/llvm-strip" \
        --disable-x86asm --disable-autodetect --disable-everything --disable-doc --disable-debug \
        --disable-network --disable-ffplay --disable-ffprobe --enable-ffmpeg --enable-protocol=file \
        --enable-demuxer=mpegts --enable-muxer=mp4 --enable-parser=h264,hevc,aac,ac3,mpegaudio \
        --enable-bsf=aac_adtstoasc,extract_extradata --extra-ldflags=-Wl,-z,max-page-size=16384
    make -j8 ffmpeg
    cp ffmpeg "/work/out/$arch"
    cp config.h "/work/out/$arch-config.h"
    cd /tmp
done