# SlimeFinderGUI

A GUI tool for searching large slime-chunk AFK spots in Minecraft Java Edition (annulus radius 24–128).
用于搜索 Minecraft Java 版史莱姆区块挂机点（环半径 24–128）的 GUI 工具。

## Requirements
- Java 17+
- MSYS2 MinGW (gcc, g++, cmake, ninja) for building the native library

## 环境要求
- Java 17+
- MSYS2 MinGW（gcc、g++、cmake、ninja），用于构建原生库

## Build / 构建

```bat
gradlew buildNative buildMainJar
```

## cubiomes

Submodule: [xpple/cubiomes](https://github.com/xpple/cubiomes) (commit [`62007b8`](https://github.com/xpple/cubiomes/commit/62007b8c6260290a3951f8ea9ce4a41e60dd1b54)).
子模块：[xpple/cubiomes](https://github.com/xpple/cubiomes)（提交 [`62007b8`](https://github.com/xpple/cubiomes/commit/62007b8c6260290a3951f8ea9ce4a41e60dd1b54)）。

If `cubiomes/` exists at the project root, the local copy is used; otherwise run:
若项目根目录已有 `cubiomes/`，则使用本地副本；否则执行：

```bat
git submodule update --init --recursive
```

## Features

- Slime chunk ring area search (24–128)
- Optional biome conversion at y=-64 (1:4 scale)
- MC version tiers: 1.18 / 1.19~26.1 / 26.2+
- Default min area 20% of full ring (~9932 / 49662)
- Multi-threaded search, pause / resume / stop, batch seed list

## 功能

- 史莱姆区块环带面积搜索（半径 24–128）
- 可选群系折算（y=-64，1:4 采样）
- MC 版本档：1.18 / 1.19~26.1 / 26.2+
- 默认最低面积：满环的 20%（约 9932 / 49662）
- 多线程搜索，支持暂停 / 继续 / 停止与种子列表批量搜索

## Biome conversion (optional)

When enabled, results are sorted by **converted area**; otherwise by **raw slime block count** in the ring.

At y=-64, each 4×4 cell in the annulus is sampled at its center biome (1:4). Slime blocks are summed **per factor group**, then each group is converted once with `(total × numerator + denominator/2) / denominator`.

Biomes with factor ≠ 1:

- river — 515/615 (≈0.8374), all tiers
- dripstone_caves — 515/610 (≈0.8443), all tiers
- old_growth_pine_taiga — 515/540 (≈0.9537), all tiers
- mushroom_fields — 0, all tiers
- deep_dark — 0, 1.19~26.1+
- sulfur_caves — 515/1555 (≈0.3312), 26.2+
- original value of sulfur_caves is 25/311 = 100/1244, but in sulfur_caves, slime spawn 1 per group, in normal biome it's 4,so times an addition 0.8, as 100/1555

All other biomes use factor 1. Constants: `BiomeConversionFactors.h` (native).

## 群系折算（可选）

勾选后按**折算面积**排序；未勾选则按环带内**史莱姆方块总数**排序。

在 y=-64 高度，环带内每个 4×4 格按格心群系（1:4 采样）归类；**同一折算系数群系**先累加史莱姆方块总数，再一次性 `(总数 × 分子 + 分母/2) / 分母` 折算后相加。

系数 ≠ 1 的群系：

- 河流 river — 515/615（≈0.8374），全部版本档
- 溶洞 dripstone_caves — 515/610（≈0.8443），全部版本档
- 原始松木针叶林 old_growth_pine_taiga — 515/540（≈0.9537），全部版本档
- 蘑菇岛 mushroom_fields — 0，全部版本档
- 深暗之域 deep_dark — 0，1.19~26.1+
- 硫黄洞穴 sulfur_caves — 515/1555（≈0.3312），26.2+
- 硫黄洞穴原始值是 25/311 = 100/1244，但是硫黄洞穴史莱姆每群只生成1个，正常群系是4个，所以效率打八折，最终结果是100/1555

其余群系系数为 1。常量定义见 `BiomeConversionFactors.h`（原生）。

## Core Libraries

- [cubiomes (xpple)](https://github.com/xpple/cubiomes) — biome generation & slime chunk RNG
- Based on [RiverFinder](https://github.com/melationin/riverfinder) search architecture

## 核心依赖

- [cubiomes (xpple)](https://github.com/xpple/cubiomes) — 群系生成与史莱姆区块 RNG
- 搜索架构参考 [RiverFinder](https://github.com/melationin/riverfinder)
