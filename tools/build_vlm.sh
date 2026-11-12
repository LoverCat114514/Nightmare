#!/usr/bin/env bash
# tools/build_vlm.sh — build libvlm_engine.so (llama.cpp libllama + mtmd +
# vlm_engine.cpp JNI bridge) for arm64-v8a.
#
# Why not cmake/NDK-prebuilt:
#   - apt has no network (mirror DNS dead in this proot) → no cmake/ninja;
#   - the NDK's prebuilt toolchain is linux-x86_64 and cannot execute here.
# So: system aarch64 clang (host == target arch) + NDK r27 sysroot (pure data)
# + GNU ld (bfd, supports aarch64linux) + a libgcc.a shim that points at
# NDK's clang_rt.builtins (the clang driver hardcodes -lgcc).
#
# Usage: tools/build_vlm.sh [path/to/llama.cpp]
# Default source: /tmp/llama.cpp (shallow clone via ssh.github.com:443).
set -e

LLAMA_SRC="${1:-/tmp/llama.cpp}"
OUT="/tmp/nmwork/app/src/main/jniLibs/arm64-v8a"
BUILD=/tmp/vlm-build
NDK=/root/android-sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/linux-x86_64
SYS="$NDK/sysroot"
API=31

mkdir -p "$BUILD" "$OUT"
rm -f "$BUILD/FAILED"  # fail-fast accumulator

# libgcc shim (clang driver injects -lgcc; NDK ships clang_rt.builtins)
mkdir -p /tmp/fakelib
ln -sf "$NDK/lib/clang/18/lib/linux/libclang_rt.builtins-aarch64-android.a" /tmp/fakelib/libgcc.a

CC="clang --target=aarch64-linux-android$API --sysroot=$SYS -fuse-ld=bfd"
CXX="clang++ --target=aarch64-linux-android$API --sysroot=$SYS -fuse-ld=bfd"
FLAGS="-O3 -fPIC -ffunction-sections -fdata-sections -march=armv8.2-a+dotprod+fp16"
DEFS="-D_GNU_SOURCE -DGGML_USE_CPU"  # _GNU_SOURCE: bionic gates cpu_set_t/sched_setaffinity behind __USE_GNU (C++ defines it implicitly, C does not)
CFLAGS="$FLAGS $DEFS"
CXXFLAGS="$FLAGS $DEFS -std=c++17 -stdlib=libc++"
INC="-I$LLAMA_SRC/include \
     -I$LLAMA_SRC/ggml/include \
     -I$LLAMA_SRC/src \
     -I$LLAMA_SRC/ggml/src \
     -I$LLAMA_SRC/ggml/src/ggml-cpu \
     -I$LLAMA_SRC/tools/mtmd \
     -I$LLAMA_SRC/vendor/stb \
     -I$LLAMA_SRC/vendor/miniaudio \
     -I$LLAMA_SRC/vendor/hash \
     -I$LLAMA_SRC/vendor"

# ---- generated version headers (normally produced by cmake) ----
sed -e 's/@GGML_VERSION@/0.0.0/' -e 's/@GGML_BUILD_COMMIT@/unknown/' \
    "$LLAMA_SRC/ggml/src/ggml-version.h.in" > "$LLAMA_SRC/ggml/src/ggml-version.h"
sed -e 's/@LLAMA_VERSION@/0.0.0/' -e 's/@LLAMA_BUILD_COMMIT@/unknown/' \
    "$LLAMA_SRC/src/llama-version.h.in" > "$LLAMA_SRC/src/llama-version.h"

# Fail-fast: every compile job appends to FAILED on error; each phase checks
# and aborts before linking (a stale/partial .so must never ship).
check_failed() {
    if [ -s "$BUILD/FAILED" ]; then
        echo "VLM_BUILD_FAILED (compile errors):"
        cat "$BUILD/FAILED"
        rm -f "$OUT/libvlm_engine.so"
        exit 1
    fi
}
compile_c()   { { $CC $CFLAGS   $INC -c "$1" -o "$BUILD/$(basename "$1").o"; } || echo "FAIL $1" >> "$BUILD/FAILED" & }
compile_cpp() { { $CXX $CXXFLAGS $INC -c "$1" -o "$BUILD/$(basename "$1").o"; } || echo "FAIL $1" >> "$BUILD/FAILED" & }

# name-safe wrappers: quants.c collides between ggml-cpu/ and arch/arm/
compile_c_named()   { { $CC $CFLAGS   $INC -c "$1" -o "$BUILD/$2.o"; } || echo "FAIL $1" >> "$BUILD/FAILED" & }
compile_cpp_named() { { $CXX $CXXFLAGS $INC -c "$1" -o "$BUILD/$2.o"; } || echo "FAIL $1" >> "$BUILD/FAILED" & }

# run a batch of compile jobs, then wait — bounded parallelism (all clang
# processes live on the phone's RAM; unbounded &-fanout gets OOM-killed)
run_jobs() {
    local pids=()
    for job in "$@"; do :; done
    wait
}

echo "== [1/4] ggml core =="
for f in ggml.c ggml.cpp ggml-alloc.c ggml-backend.cpp ggml-backend-meta.cpp \
         ggml-opt.cpp ggml-threading.cpp ggml-quants.c ggml-backend-reg.cpp \
         ggml-backend-dl.cpp gguf.cpp; do
    case "$f" in *.c) compile_c "$LLAMA_SRC/ggml/src/$f";; *) compile_cpp "$LLAMA_SRC/ggml/src/$f";; esac
done
wait
check_failed

echo "== [2/4] ggml-cpu (arm) =="
# vendor/hash: sha256 used by mtmd-helper (image cache IDs); upstream links
# vendor::hash into mtmd. Without it dlopen fails on hash_sha256_hex.
compile_cpp "$LLAMA_SRC/vendor/hash/hash.cpp"
compile_c   "$LLAMA_SRC/vendor/hash/sha256/sha256.c"
# excluded: repack*/amx/tiled (x86-AMX/universal — not needed for Q8_0/f16),
# llamafile sgemm (x86 intrinsic bombs), arch/x86 (backend-dl now in phase 1:
# ggml-backend-reg.cpp references dl_* unconditionally — dlopen would fail)
for f in ggml-cpu/ggml-cpu.c ggml-cpu/ggml-cpu.cpp ggml-cpu/traits.cpp \
         ggml-cpu/hbm.cpp ggml-cpu/binary-ops.cpp ggml-cpu/unary-ops.cpp \
         ggml-cpu/vec.cpp ggml-cpu/ops.cpp; do
    case "$f" in *.c) compile_c "$LLAMA_SRC/ggml/src/$f";; *) compile_cpp "$LLAMA_SRC/ggml/src/$f";; esac
done
compile_c_named "$LLAMA_SRC/ggml/src/ggml-cpu/quants.c" "ggml-cpu-quants.c.o"
compile_c_named "$LLAMA_SRC/ggml/src/ggml-cpu/arch/arm/quants.c" "arm-quants.c.o"
# tiled: ggml-cpu.c calls ggml_compute_forward_mul_mat_tiled/ggml_tiled_wdata_size
# unconditionally — upstream CMake compiles tiled/ always (exclusion was wrong).
compile_cpp_named "$LLAMA_SRC/ggml/src/ggml-cpu/tiled/tiled.cpp" "tiled.cpp"
compile_cpp_named "$LLAMA_SRC/ggml/src/ggml-cpu/tiled/tiled-kernel.cpp" "tiled-kernel.cpp"
wait
check_failed

echo "== [3/4] llama + mtmd =="
# bounded parallelism: 8 jobs at a time
JOBS=8; RUNNING=0
for f in "$LLAMA_SRC"/src/*.cpp "$LLAMA_SRC"/src/models/*.cpp \
         "$LLAMA_SRC"/tools/mtmd/mtmd.cpp "$LLAMA_SRC"/tools/mtmd/mtmd-audio.cpp \
         "$LLAMA_SRC"/tools/mtmd/mtmd-image.cpp "$LLAMA_SRC"/tools/mtmd/mtmd-helper.cpp \
         "$LLAMA_SRC"/tools/mtmd/mtmd-helper-gen.cpp "$LLAMA_SRC"/tools/mtmd/mtmd-model.cpp \
         "$LLAMA_SRC"/tools/mtmd/mtmd-subcontext.cpp "$LLAMA_SRC"/tools/mtmd/mtmd-graph.cpp \
         "$LLAMA_SRC"/tools/mtmd/models/*.cpp \
         /tmp/nmwork/app/src/main/cpp/vlm_engine.cpp; do
    base="$(basename "$f")"
    # src/models vs tools/mtmd/models can collide (qwen3vl.cpp exists in both)
    case "$f" in
        *src/models/*)    out="llama-models-$base";;
        *mtmd/models/*)   out="mtmd-models-$base";;
        *)                out="$base";;
    esac
    { $CXX $CXXFLAGS $INC -c "$f" -o "$BUILD/$out.o"; } || echo "FAIL $f" >> "$BUILD/FAILED" &
    RUNNING=$((RUNNING+1))
    if [ "$RUNNING" -ge "$JOBS" ]; then wait -n 2>/dev/null || wait; RUNNING=$((RUNNING-1)); fi
done
wait
check_failed

echo "== [4/4] link =="
rm -f "$OUT/libvlm_engine.so"
$CXX "$BUILD"/*.o -shared -o "$OUT/libvlm_engine.so" \
    -L/tmp/fakelib -lc++_shared -llog \
    -Wl,--gc-sections

cp "$OUT/libvlm_engine.so" /tmp/libvlm_engine.unstripped.so
strip --strip-unneeded "$OUT/libvlm_engine.so" 2>/dev/null || true
# JNI contract gate: the bridge entry points must be exported or the .so is
# dead weight (this is exactly how a compile-error build almost shipped).
if ! nm -D "$OUT/libvlm_engine.so" 2>/dev/null | grep -q 'Java_com_abrah_nightmare_reverse_VlmEngine_nativeLoadModel'; then
    echo "VLM_BUILD_FAILED: JNI bridge symbols missing from linked .so"
    rm -f "$OUT/libvlm_engine.so"
    exit 1
fi
# Own-API gate: every ggml_/llama_/mtmd_/gguf_ symbol must resolve internally.
# (dlopen fails hard on ANY unresolved symbol; these prefixes are ours, never
# system libs — this gate catches missing source files like gguf.cpp.)
if nm -D --undefined-only "$OUT/libvlm_engine.so" 2>/dev/null | awk '{print $NF}' | grep -qE '^(gguf_|llama_|mtmd_|ggml_)'; then
    echo "VLM_BUILD_FAILED: unresolved llama.cpp API symbols (missing source files?):"
    nm -D --undefined-only "$OUT/libvlm_engine.so" | awk '{print $NF}' | grep -E '^(gguf_|llama_|mtmd_|ggml_)' | head -10
    rm -f "$OUT/libvlm_engine.so"
    exit 1
fi
ls -la "$OUT/libvlm_engine.so"
echo "VLM_BUILD_OK"