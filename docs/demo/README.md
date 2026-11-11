# 演示片素材

用来说明这个 App 长什么样的 12 秒动态演示。**画面由真实界面代码渲染，不是 AI 生成的假 UI**——手机里的数值、曲线、指标都来自项目实际实现（62.40 kg / BMI 21.59 / 体脂 16.6% / BMR 1542 与 `verify_data_layer.py` 的样例一致）。

## 文件

| 文件 | 规格 | 用途 |
|---|---|---|
| `neon-scale-demo.gif` | 720×405 · 12 fps · 6.1 MB · 11.8 s 循环 | **README 顶部直接嵌入**，GitHub 会自动播放 |
| `neon-scale-demo.mp4` | 1920×1080 · 30 fps · 3.8 MB · H.264 | 高清分享、社交平台、Release 附件 |
| `poster-wide.png` | 1920×1080 | 四屏全景海报；适合设为 GitHub 仓库 Social preview |
| `poster-measure.png` | 1920×1080 | 测量页特写海报 |

> GIF 已针对 GitHub 优化：720px 宽 + 12fps + 128 色 + Bayer 抖动。再放大或提帧率体积会翻倍（900px/14fps 版本约 15.8 MB，加载明显变慢）。

## 分镜

| 时间 | 画面 |
|---|---|
| 0.0–0.7s | 黑场起，透视网格与双色辉光淡入；`NEON//SCALE` 逐字点亮，居中放大 |
| 0.7–1.2s | 品牌副标题出现，标题归位到左上角 |
| 1.2–1.8s | 四台手机错峰从下方升起（测量 / 趋势 / 档案 / 成员） |
| 2.0–4.6s | 各屏动效同时展开：圆环扫入 + 数字 0→62.40、BMI 分级卡弹出、折线生长、记录与成员卡滑入、档案详情浮出 |
| 5.2–8.5s | 镜头推近第 1 台（测量页），放大展示圆环、BMI 等级、遥测区；其余三屏压暗 |
| 8.5–9.3s | 拉回四屏全景 |
| 9.3–11.8s | 底部技术栈标语浮现，定格收尾 |

## 重新生成

源码在 [`tools/demo/`](../../tools/demo/)：

```bash
# 1. 渲染 354 帧 PNG（本机 Chrome / Edge 无头模式 + DevTools 协议，无需装依赖）
node tools/demo/capture.mjs tools/demo/stage.html frames 30 11.8

# 2. 合成 MP4
ffmpeg -y -framerate 30 -i frames/f_%04d.png \
  -c:v libx264 -preset slow -crf 18 -pix_fmt yuv420p -movflags +faststart \
  docs/demo/neon-scale-demo.mp4

# 3. 压 GIF
ffmpeg -y -i docs/demo/neon-scale-demo.mp4 \
  -vf "fps=12,scale=720:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle" \
  docs/demo/neon-scale-demo.gif
```

- 需要 Node 18+（用到内置 `fetch` 与 `WebSocket`；本项目在 Node 22 上验证）、Chrome 或 Edge、ffmpeg。
- 浏览器路径用 `CHROME_PATH` 覆盖，例如 `CHROME_PATH=/usr/bin/google-chrome`。
- `stage.html` 是纯前端单文件，用 `?t=6.5` 可定格任意时刻、`?play` 可实时预览，方便调时间轴。
- 改文案或数值只动 `stage.html` 里的 HTML/JS 常量，重新跑上面三步即可。

## 说明

- 片中数据为演示用的示例值（与 README 中的协议样例、公式校验样例对齐），不代表任何真实用户。
- 8 项身体成分在片中出现时沿用 App 内的标注口径：**公式估算、非秤体实测、不作医学诊断**。
