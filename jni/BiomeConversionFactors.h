#ifndef SLIMEFINDER_BIOME_CONVERSION_FACTORS_H
#define SLIMEFINDER_BIOME_CONVERSION_FACTORS_H

namespace BiomeConversion {

constexpr int NUM = 515; // 100/515 or 100/520
constexpr int RIVER_DEN = 615; // 100/615
constexpr int DRIPSTONE_CAVES_DEN = 610; // 100/610
constexpr int OLD_GROWTH_PINE_TAIGA_DEN = 540; // 100/540
constexpr int SULFUR_CAVES_DEN = 1244 * 1.25; // 25/311 = 100/1244, but in sulfur caves, slime spawn 1 per group, in normal biome it's 4,so times an addition 1.25, as 100/1555

inline int apply(int slimeCount, int num, int den)
{
    if (num == 0)
        return 0;
    if (num == den)
        return slimeCount;
    return (slimeCount * num + den / 2) / den;
}

} // namespace BiomeConversion

#endif
