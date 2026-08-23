#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
    echo "usage: build_android.sh SOURCE_ROOT OUTPUT_ROOT NDK_ROOT" >&2
    exit 64
fi

source_root=$(cd "$1" && pwd)
mkdir -p "$2"
output_root=$(cd "$2" && pwd)
ndk_root=$(cd "$3" && pwd)
api=26

case "$(uname -s)" in
    Darwin) host_tools=darwin-x86_64 ;;
    Linux) host_tools=linux-x86_64 ;;
    *) echo "unsupported Hamlib build host" >&2; exit 65 ;;
esac

toolchain="$ndk_root/toolchains/llvm/prebuilt/$host_tools"
if [[ ! -x "$toolchain/bin/llvm-ar" ]]; then
    echo "missing Android NDK toolchain: $toolchain" >&2
    exit 66
fi

jobs=$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)
if [[ -n "${HAMLIB_ABIS:-}" ]]; then
    read -r -a abis <<< "$HAMLIB_ABIS"
else
    abis=(armeabi-v7a arm64-v8a x86 x86_64)
fi

for abi in "${abis[@]}"; do
    case "$abi" in
        armeabi-v7a)
            host=arm-linux-androideabi
            compiler=armv7a-linux-androideabi
            tool_prefix=arm-linux-androideabi
            ;;
        arm64-v8a)
            host=aarch64-linux-android
            compiler=aarch64-linux-android
            tool_prefix=aarch64-linux-android
            ;;
        x86)
            host=i686-linux-android
            compiler=i686-linux-android
            tool_prefix=i686-linux-android
            ;;
        x86_64)
            host=x86_64-linux-android
            compiler=x86_64-linux-android
            tool_prefix=x86_64-linux-android
            ;;
    esac

    build_dir="$output_root/work/$abi"
    install_dir="$output_root/$abi"
    shim_dir="$build_dir/toolchain-shims"
    mkdir -p "$build_dir" "$install_dir/include/hamlib" "$shim_dir"
    ln -sf "$toolchain/bin/llvm-ar" "$shim_dir/$tool_prefix-ar"
    ln -sf "$toolchain/bin/llvm-ranlib" "$shim_dir/$tool_prefix-ranlib"
    ln -sf "$toolchain/bin/llvm-nm" "$shim_dir/$tool_prefix-nm"
    ln -sf "$toolchain/bin/llvm-strip" "$shim_dir/$tool_prefix-strip"

    export PATH="$shim_dir:$toolchain/bin:$PATH"
    export AR="$toolchain/bin/llvm-ar"
    export RANLIB="$toolchain/bin/llvm-ranlib"
    export NM="$toolchain/bin/llvm-nm"
    export STRIP="$toolchain/bin/llvm-strip"
    export CC="$toolchain/bin/${compiler}${api}-clang"
    export CXX="$toolchain/bin/${compiler}${api}-clang++"
    export CFLAGS="-O2 -g0 -fPIC -fvisibility=hidden -ffunction-sections -fdata-sections"
    export CXXFLAGS="$CFLAGS -std=c++17 -fno-exceptions -fno-rtti"
    export LDFLAGS="-Wl,--gc-sections"

    if [[ ! -f "$build_dir/config.status" ]]; then
        (
            cd "$build_dir"
            "$source_root/configure" \
                --host="$host" \
                --disable-shared \
                --enable-static \
                --with-pic \
                --without-libusb \
                --without-indi \
                --without-readline \
                --without-cxx-binding \
                --disable-winradio \
                --disable-parallel \
                --disable-html-matrix \
                --disable-pytest \
                --disable-dependency-tracking
        )
    fi

    make -C "$build_dir/src" -j"$jobs" libhamlib.la \
        ROT_BACKENDEPS= AMP_BACKENDEPS= \
        ACLOCAL=: AUTOCONF=: AUTOHEADER=: AUTOMAKE=:

    cp "$build_dir/src/.libs/libhamlib.a" "$install_dir/libhamlib.a"
    cp "$build_dir/include/hamlib/config.h" "$install_dir/include/hamlib/config.h"
done

echo "Hamlib 4.7.2 Android static libraries built for ${abis[*]}"
