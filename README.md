# Dynamic Island Port — A16 → A15

## 结论

**v16 插件（系统界面组件 16.5.3.14.0）有 lottie 动画资源文件但无触发代码。**
**v17 插件（17.1.3.48.2 手机版 / 17.1.5.4.0 平板版）与 A15 SystemUI 不兼容，导致系统界面崩溃。**

## 根因分析

| 组件 | 状态 |
|---|---|
| v16 插件资源 | hourglass.json、voice_wave_big.json 等 lottie 文件存在 |
| v16 插件代码 | `addDynamicIslandView` 不触发场景 lottie，代码引用为零 |
| v17 插件 | 有完整动画框架但依赖 A16 SystemUI，A15 上崩溃 |
| 手机 SystemUI | 通过 `MusicBgView` 自渲染动画，不依赖插件 lottie |
| 平板 SystemUI | 砍掉了 `MusicBgView` 等动画管线 |

## LSPosed 模块技术积累

- 成功打通了 SystemUI → 插件 ClassLoader → `addDynamicIslandView` hook 全链路
- 成功从 `onPluginLoaded` 获取插件 CL
- 成功 hook 插件 `handleDynamicIsland`、`sendWindowAnimEventForLinkage`
- 成功遍历插件视图树并调用 `LottieAnimationView.playAnimation()`
- 定位到 `DynamicIslandSizeRepository` 控制位置/触摸区域

## 构建

```
# GitHub Actions 自动构建，push 到 main 即可
```
