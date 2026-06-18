#include "cubiomes/finders.h"
#include "cubiomes/generator.h"
#include "cubiomes/biomes.h"

#include "BiomeConversionFactors.h"
#include "Thread.h"
#include "generated/com_sunnyslopes_slimefinder_SlimeFinderBridge.h"

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <mutex>
#include <queue>
#include <thread>
#include <chrono>
#include <unordered_map>
#include <vector>

struct Progress {
    std::atomic_int current{0};
    std::atomic_int total{0};
    std::atomic_int chunkInRunning{0};
    std::atomic_int phase{0};
    std::atomic_bool try_pause{false};
    std::atomic_bool try_stop{false};
};

struct SlimeRes {
    int x = 0;
    int z = 0;
    int rawArea = 0;
    int convertedArea = 0;

    bool operator<(const SlimeRes &o) const noexcept
    {
        int a = convertedArea > 0 ? convertedArea : rawArea;
        int b = o.convertedArea > 0 ? o.convertedArea : o.rawArea;
        if (a != b) return a < b;
        if (x != o.x) return x > o.x;
        return z > o.z;
    }
};

namespace {

constexpr int BIOME_Y = -64;
constexpr int R_OUT = 128;
constexpr int R_IN = 24;

static int floorDiv16(int v)
{
    if (v >= 0)
        return v / 16;
    return (v - 15) / 16;
}

static int slimeBlock(uint64_t seed, int bx, int bz)
{
    return isSlimeChunk(seed, floorDiv16(bx), floorDiv16(bz)) ? 1 : 0;
}

static void getBiomeFactorRational(int biomeId, int versionTier, int &num, int &den)
{
    num = 1;
    den = 1;
    switch (biomeId) {
    case river:
        num = BiomeConversion::NUM;
        den = BiomeConversion::RIVER_DEN;
        return;
    case dripstone_caves:
        num = BiomeConversion::NUM;
        den = BiomeConversion::DRIPSTONE_CAVES_DEN;
        return;
    case old_growth_pine_taiga:
        num = BiomeConversion::NUM;
        den = BiomeConversion::OLD_GROWTH_PINE_TAIGA_DEN;
        return;
    case mushroom_fields:
        num = 0;
        den = 1;
        return;
    default:
        break;
    }
    if (versionTier >= 1 && biomeId == deep_dark) {
        num = 0;
        den = 1;
        return;
    }
    if (versionTier >= 2 && biomeId == sulfur_caves) {
        num = BiomeConversion::NUM;
        den = BiomeConversion::SULFUR_CAVES_DEN;
    }
}

static bool checkProgress(Progress *p)
{
    if (!p)
        return true;
    while (p->try_pause.load() && !p->try_stop.load())
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
    return !p->try_stop.load();
}

static int annulusSumInt(
    const std::vector<int> &prefix, int W, int stride,
    int cx, int cz, int R_out, int R_in)
{
    std::vector<int> dxOut(2 * R_out + 1);
    std::vector<int> dxIn(2 * R_in + 1);
    for (int dz = -R_out; dz <= R_out; dz++) {
        dxOut[dz + R_out] = (int) std::floor(std::sqrt((double) R_out * R_out - dz * dz));
    }
    for (int dz = -R_in; dz <= R_in; dz++) {
        dxIn[dz + R_in] = (int) std::floor(std::sqrt((double) R_in * R_in - dz * dz));
    }

    int area = 0;
    for (int dz = -R_out; dz <= R_out; dz++) {
        int row = cz + dz;
        int L = cx - dxOut[dz + R_out];
        int R = cx + dxOut[dz + R_out];
        if (std::abs(dz) > R_in) {
            area += prefix[(R + 1) + (row + 1) * stride]
                - prefix[L + (row + 1) * stride]
                - prefix[(R + 1) + row * stride]
                + prefix[L + row * stride];
        } else {
            int Lin = cx - dxIn[dz + R_in];
            int Rin = cx + dxIn[dz + R_in];
            area += prefix[Lin + (row + 1) * stride]
                - prefix[L + (row + 1) * stride]
                - prefix[Lin + row * stride]
                + prefix[L + row * stride];
            area += prefix[(R + 1) + (row + 1) * stride]
                - prefix[(Rin + 1) + (row + 1) * stride]
                - prefix[(R + 1) + row * stride]
                + prefix[(Rin + 1) + row * stride];
        }
    }
    return area;
}

template<int scale>
std::vector<SlimeRes> findBiggestSlimeRaw(
    uint64_t seed,
    int startX, int startZ, int sx, int sz,
    int minArea, double f)
{
    std::vector<SlimeRes> result;
    const int W = sx / scale;
    const int H = sz / scale;
    if (W <= 0 || H <= 0)
        return result;

    const int stride = W + 1;
    std::vector<int> raw(W * H, 0);
    std::vector<int> prefix((W + 1) * (H + 1), 0);

    for (int z = 0; z < H; z++) {
        for (int x = 0; x < W; x++) {
            int wx = startX + x * scale + (scale > 1 ? scale / 2 : 0);
            int wz = startZ + z * scale + (scale > 1 ? scale / 2 : 0);
            if constexpr (scale > 1) {
                raw[x + z * W] = slimeBlock(seed, wx, wz);
            } else {
                raw[x + z * W] = slimeBlock(seed, startX + x, startZ + z);
            }
        }
    }

    for (int z = 1; z <= H; z++) {
        for (int x = 1; x <= W; x++) {
            prefix[x + z * stride] = raw[(x - 1) + (z - 1) * W]
                + prefix[(x - 1) + z * stride]
                + prefix[x + (z - 1) * stride]
                - prefix[(x - 1) + (z - 1) * stride];
        }
    }

    const int R_out = R_OUT / scale;
    const int R_in = R_IN / scale;
    if (R_out * 2 >= W || R_out * 2 >= H)
        return result;

    struct AreaHit {
        int area;
        int x;
        int z;
        bool operator<(const AreaHit &o) const noexcept { return area < o.area; }
    };

    std::priority_queue<AreaHit> pq;
    int maxArea = 0;

    for (int cz = R_out; cz < H - R_out; cz++) {
        for (int cx = R_out; cx < W - R_out; cx++) {
            int area = annulusSumInt(prefix, W, stride, cx, cz, R_out, R_in);
            int worldArea = area * scale * scale;
            if (worldArea >= maxArea * f && worldArea >= minArea) {
                int worldX = startX + cx * scale;
                int worldZ = startZ + cz * scale;
                pq.push({worldArea, worldX, worldZ});
                if (worldArea > maxArea)
                    maxArea = worldArea;
            }
        }
    }

    while (!pq.empty()) {
        const auto &ra = pq.top();
        if (ra.area >= (int) (maxArea * f)) {
            SlimeRes r;
            r.x = ra.x;
            r.z = ra.z;
            r.rawArea = ra.area;
            result.push_back(r);
            if (f >= 1.0)
                break;
        }
        pq.pop();
    }
    return result;
}

static int biomeAtY64(const Generator *g, int bx, int bz)
{
    return getBiomeAt(g, 4, bx >> 2, BIOME_Y >> 2, bz >> 2);
}

static int countRawSlimeAnnulus(uint64_t seed, int centerX, int centerZ)
{
    std::vector<int> dxOut(2 * R_OUT + 1);
    std::vector<int> dxIn(2 * R_IN + 1);
    for (int dz = -R_OUT; dz <= R_OUT; dz++) {
        dxOut[dz + R_OUT] = (int) std::floor(std::sqrt((double) R_OUT * R_OUT - dz * dz));
    }
    for (int dz = -R_IN; dz <= R_IN; dz++) {
        dxIn[dz + R_IN] = (int) std::floor(std::sqrt((double) R_IN * R_IN - dz * dz));
    }

    int total = 0;
    for (int dz = -R_OUT; dz <= R_OUT; dz++) {
        int bz = centerZ + dz;
        int out = dxOut[dz + R_OUT];
        int in = (std::abs(dz) <= R_IN) ? dxIn[dz + R_IN] : -1;
        for (int dx = -out; dx <= out; dx++) {
            if (in >= 0 && dx >= -in && dx <= in)
                continue;
            if (slimeBlock(seed, centerX + dx, bz))
                total++;
        }
    }
    return total;
}

static bool annulusContainsCell(
    int cx, int cz, int R_out, int R_in,
    const std::vector<int> &dxOut, const std::vector<int> &dxIn,
    int x, int z)
{
    int dz = z - cz;
    if (std::abs(dz) > R_out)
        return false;
    int dx = x - cx;
    int out = dxOut[dz + R_out];
    if (std::abs(dx) > out)
        return false;
    if (std::abs(dz) <= R_in) {
        int in = dxIn[dz + R_in];
        if (std::abs(dx) <= in)
            return false;
    }
    return true;
}

static uint64_t packFactorKey(int num, int den)
{
    return ((uint64_t) (uint32_t) num << 32) | (uint32_t) den;
}

static int computeConvertedArea(
    Generator *g, uint64_t seed, int versionTier,
    int centerX, int centerZ)
{
    constexpr int scale = 4;
    const int startX = centerX - R_OUT;
    const int startZ = centerZ - R_OUT;
    const int W = (R_OUT * 2 + scale) / scale;
    const int H = W;

    const int cx = R_OUT / scale;
    const int cz = R_OUT / scale;
    const int R_out = R_OUT / scale;
    const int R_in = R_IN / scale;

    std::vector<int> dxOut(2 * R_out + 1);
    std::vector<int> dxIn(2 * R_in + 1);
    for (int dz = -R_out; dz <= R_out; dz++) {
        dxOut[dz + R_out] = (int) std::floor(std::sqrt((double) R_out * R_out - dz * dz));
    }
    for (int dz = -R_in; dz <= R_in; dz++) {
        dxIn[dz + R_in] = (int) std::floor(std::sqrt((double) R_in * R_in - dz * dz));
    }

    std::unordered_map<uint64_t, int> rawSlimeByFactor;

    for (int z = 0; z < H; z++) {
        for (int x = 0; x < W; x++) {
            if (!annulusContainsCell(cx, cz, R_out, R_in, dxOut, dxIn, x, z))
                continue;
            int baseX = startX + x * scale;
            int baseZ = startZ + z * scale;
            int factorNum = 1;
            int factorDen = 1;
            getBiomeFactorRational(
                biomeAtY64(g, baseX + 2, baseZ + 2), versionTier, factorNum, factorDen);
            int slimeCount = 0;
            for (int dz = 0; dz < scale; dz++) {
                for (int dx = 0; dx < scale; dx++) {
                    if (slimeBlock(seed, baseX + dx, baseZ + dz))
                        slimeCount++;
                }
            }
            if (slimeCount > 0) {
                uint64_t key = packFactorKey(factorNum, factorDen);
                rawSlimeByFactor[key] += slimeCount;
            }
        }
    }

    int converted = 0;
    for (const auto &entry: rawSlimeByFactor) {
        int num = (int) (entry.first >> 32);
        int den = (int) entry.first;
        converted += BiomeConversion::apply(entry.second, num, den);
    }

    if (converted <= 0) {
        converted = countRawSlimeAnnulus(seed, centerX, centerZ);
    }
    return converted;
}

struct SearchParams {
    uint64_t seed = 0;
    int startX = 0;
    int startZ = 0;
    int width = 0;
    int height = 0;
    int mc = MC_1_18;
    int versionTier = 0;
    int minArea = 0;
    float preserveRange = 0.9f;
    bool biomeConversion = false;
    int numThreads = 1;
};

void runSlimeSearch(
    const SearchParams &params,
    Progress *progress,
    ThreadSafeResults<SlimeRes> &globalResults)
{
    Generator g;
    setupGenerator(&g, params.mc, 0);
    applySeed(&g, DIM_OVERWORLD, params.seed);

    const int minRaw = params.biomeConversion ? 0 : params.minArea;
    int threads = params.numThreads > 0 ? params.numThreads : (int) std::thread::hardware_concurrency();
    if (threads < 1)
        threads = 1;

    {
        ThreadPool pool((size_t) threads);
        const int chunkSize = 8192;
        const int overlap = 256;
        std::atomic<int> completed{0};
        int totalChunks = 0;

        for (int x = 0; x < params.width; x += chunkSize - overlap) {
            for (int z = 0; z < params.height; z += chunkSize - overlap) {
                int currentSx = std::min(chunkSize, params.width - x);
                int currentSz = std::min(chunkSize, params.height - z);
                if (currentSx < 256 || currentSz < 256)
                    continue;
                totalChunks++;
            }
        }

        if (progress) {
            progress->current = 0;
            progress->total = std::max(totalChunks, 1);
            progress->chunkInRunning = 1;
            progress->phase = 1;
        }

        for (int x = 0; x < params.width; x += chunkSize - overlap) {
            for (int z = 0; z < params.height; z += chunkSize - overlap) {
                int currentSx = std::min(chunkSize, params.width - x);
                int currentSz = std::min(chunkSize, params.height - z);
                if (currentSx < 256 || currentSz < 256)
                    continue;

                pool.enqueue([&, x, z, currentSx, currentSz]() {
                    if (!checkProgress(progress))
                        return;

                    auto coarse = findBiggestSlimeRaw<16>(
                        params.seed,
                        params.startX + x, params.startZ + z,
                        currentSx, currentSz,
                        minRaw, 0.8);

                    int bx = currentSx / 256 + 2;
                    int bz = currentSz / 256 + 2;
                    std::vector<SlimeRes> flags(bx * bz);
                    for (const auto &it: coarse) {
                        int x2 = (it.x - params.startX - x) / 256;
                        int z2 = (it.z - params.startZ - z) / 256;
                        if (x2 >= 0 && x2 < bx && z2 >= 0 && z2 < bz) {
                            auto &f = flags[x2 + bx * z2];
                            if (f.rawArea < it.rawArea)
                                f = it;
                        }
                    }

                    std::vector<SlimeRes> sorted;
                    for (auto &kv: flags)
                        if (kv.rawArea > 0)
                            sorted.push_back(kv);
                    std::sort(sorted.begin(), sorted.end(), [](const SlimeRes &a, const SlimeRes &b) {
                        return a.rawArea > b.rawArea;
                    });

                    std::unordered_map<uint64_t, SlimeRes> refined;
                    int maxA = 0;
                    for (auto &res: sorted) {
                        if (!checkProgress(progress))
                            return;
                        auto sub = findBiggestSlimeRaw<4>(
                            params.seed,
                            res.x - 256, res.z - 256,
                            512, 512,
                            minRaw, 1.0);
                        if (sub.empty())
                            break;
                        if (sub[0].rawArea < maxA * 0.9)
                            break;
                        uint64_t key = ((uint64_t) (uint32_t) sub[0].x << 32) | (uint32_t) sub[0].z;
                        refined[key] = sub[0];
                        if (sub[0].rawArea > maxA)
                            maxA = sub[0].rawArea;
                    }

                    std::vector<SlimeRes> filtered;
                    for (const auto &entry: refined) {
                        const SlimeRes &result = entry.second;
                        int relX = result.x - (params.startX + x);
                        int relZ = result.z - (params.startZ + z);
                        if (relX > overlap / 2 && relX < currentSx - overlap / 2 &&
                            relZ > overlap / 2 && relZ < currentSz - overlap / 2)
                            filtered.push_back(result);
                    }
                    std::sort(filtered.begin(), filtered.end(), [](const SlimeRes &a, const SlimeRes &b) {
                        return a.rawArea > b.rawArea;
                    });
                    if (!filtered.empty())
                        globalResults.addResults(filtered);

                    if (progress)
                        progress->current = completed.fetch_add(1) + 1;
                });
            }
        }
    }

    if (progress) {
        progress->chunkInRunning = 0;
        progress->phase = 2;
        progress->current = 0;
        progress->total = 1;
    }

    auto all = globalResults.getAllResults();
    std::sort(all.begin(), all.end(), [](const SlimeRes &a, const SlimeRes &b) {
        return a.rawArea > b.rawArea;
    });

    globalResults.clear();
    int maxFinal = 0;
    std::vector<SlimeRes> finalResults;

    for (const auto &it: all) {
        if (!checkProgress(progress))
            break;
        auto temp = findBiggestSlimeRaw<1>(
            params.seed,
            it.x - R_OUT - 32, it.z - R_OUT - 32,
            R_OUT * 2 + 64, R_OUT * 2 + 64,
            1, 1.0);
        if (temp.empty() || temp[0].rawArea < maxFinal * params.preserveRange)
            break;

        SlimeRes hit = temp[0];
        if (!params.biomeConversion && hit.rawArea < params.minArea)
            continue;

        finalResults.push_back(hit);
        if (hit.rawArea > maxFinal)
            maxFinal = hit.rawArea;

        if (progress)
            progress->current = 1;
    }

    if (!finalResults.empty()) {
        if (params.biomeConversion) {
            for (auto &hit : finalResults) {
                hit.convertedArea = computeConvertedArea(
                    &g, params.seed, params.versionTier, hit.x, hit.z);
                if (hit.convertedArea <= 0)
                    hit.convertedArea = hit.rawArea;
                if (hit.convertedArea < params.minArea)
                    hit.convertedArea = -1;
            }
            finalResults.erase(
                std::remove_if(finalResults.begin(), finalResults.end(),
                    [](const SlimeRes &h) { return h.convertedArea < 0; }),
                finalResults.end());
            std::sort(finalResults.begin(), finalResults.end(),
                [](const SlimeRes &a, const SlimeRes &b) {
                    return a.convertedArea > b.convertedArea;
                });
        } else {
            std::sort(finalResults.begin(), finalResults.end(),
                [](const SlimeRes &a, const SlimeRes &b) {
                    return a.rawArea > b.rawArea;
                });
        }
        if (!finalResults.empty())
            globalResults.addResults(finalResults);
    }

    if (progress) {
        progress->phase = -1;
        progress->chunkInRunning = 0;
    }
}

} // namespace

static Progress g_progress{};
static ThreadSafeResults<SlimeRes> g_results{};
static bool g_lastBiomeConversion = false;

static jintArray makeIntArray(JNIEnv *env, const std::vector<jint> &data)
{
    if (data.empty())
        return nullptr;
    jsize len = (jsize) data.size();
    jintArray arr = env->NewIntArray(len);
    if (!arr)
        return nullptr;
    env->SetIntArrayRegion(arr, 0, len, data.data());
    return arr;
}

extern "C" {

JNIEXPORT jintArray JNICALL Java_com_sunnyslopes_slimefinder_SlimeFinderBridge_slimeSearch
  (JNIEnv *env, jclass, jlong seed, jint startX, jint startZ, jint width, jint height,
   jint mcVersion, jint versionTier, jint minArea, jfloat preserveRange,
   jint biomeConversion, jint numThreads)
{
    g_lastBiomeConversion = biomeConversion != 0;
    g_results.clear();
    g_progress.current = 0;
    g_progress.total = 1;
    g_progress.chunkInRunning = 0;
    g_progress.phase = 0;
    g_progress.try_pause = false;
    g_progress.try_stop = false;

    SearchParams p;
    p.seed = (uint64_t) seed;
    p.startX = startX;
    p.startZ = startZ;
    p.width = width;
    p.height = height;
    p.mc = mcVersion;
    p.versionTier = versionTier;
    p.minArea = minArea;
    p.preserveRange = preserveRange;
    p.biomeConversion = biomeConversion != 0;
    p.numThreads = numThreads;

    runSlimeSearch(p, &g_progress, g_results);

    if (g_progress.try_stop.load())
        return nullptr;

    auto hits = g_results.getAllResults();
    if (hits.empty())
        return nullptr;

    std::vector<jint> flat;
    if (g_lastBiomeConversion) {
        flat.reserve(hits.size() * 4);
        for (const auto &h: hits) {
            flat.push_back(h.x);
            flat.push_back(h.z);
            flat.push_back(h.rawArea);
            int conv = h.convertedArea > 0 ? h.convertedArea : h.rawArea;
            flat.push_back(conv);
        }
    } else {
        flat.reserve(hits.size() * 3);
        for (const auto &h: hits) {
            flat.push_back(h.x);
            flat.push_back(h.z);
            flat.push_back(h.rawArea);
        }
    }
    return makeIntArray(env, flat);
}

JNIEXPORT jintArray JNICALL Java_com_sunnyslopes_slimefinder_SlimeFinderBridge_getSearchProgress
  (JNIEnv *env, jclass)
{
    if (g_progress.total.load() == 0 && g_progress.phase.load() == 0)
        return nullptr;

    int status = 0;
    int ph = g_progress.phase.load();
    if (ph == 1) {
        status = g_progress.chunkInRunning.load() == 0 ? 1 : 0;
    } else if (ph == 2) {
        status = g_progress.current.load() >= g_progress.total.load() ? 2 : 1;
    }

    jint data[6] = {
        g_progress.current.load(),
        g_progress.total.load(),
        ph,
        status,
        g_progress.try_pause.load() ? 1 : 0,
        g_progress.try_stop.load() ? 1 : 0,
    };
    jintArray arr = env->NewIntArray(6);
    if (!arr)
        return nullptr;
    env->SetIntArrayRegion(arr, 0, 6, data);
    return arr;
}

JNIEXPORT jintArray JNICALL Java_com_sunnyslopes_slimefinder_SlimeFinderBridge_getNowResult
  (JNIEnv *env, jclass)
{
    if (g_results.empty())
        return nullptr;
    auto hits = g_results.getAllResults();
    std::vector<jint> flat;
    if (g_lastBiomeConversion) {
        for (const auto &h: hits) {
            flat.push_back(h.x);
            flat.push_back(h.z);
            flat.push_back(h.rawArea);
            int conv = h.convertedArea > 0 ? h.convertedArea : h.rawArea;
            flat.push_back(conv);
        }
    } else {
        for (const auto &h: hits) {
            flat.push_back(h.x);
            flat.push_back(h.z);
            flat.push_back(h.rawArea);
        }
    }
    return makeIntArray(env, flat);
}

JNIEXPORT jboolean JNICALL Java_com_sunnyslopes_slimefinder_SlimeFinderBridge_pause
  (JNIEnv *, jclass)
{
    g_progress.try_pause = true;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_sunnyslopes_slimefinder_SlimeFinderBridge_resume
  (JNIEnv *, jclass)
{
    g_progress.try_pause = false;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_sunnyslopes_slimefinder_SlimeFinderBridge_stop
  (JNIEnv *, jclass)
{
    g_progress.try_stop = true;
    return JNI_TRUE;
}

} // extern "C"
