"""
生成 PWA 图标：微信/QQ 风格的蓝色圆角方块 + 白色对话气泡。

纯几何绘制，不依赖字体（避免不同机器上字体缺失导致渲染不一致）。
输出：
  icon-192.png          普通图标
  icon-512.png          大图标
  icon-maskable-512.png 安卓自适应图标（内容内缩，避免被裁边）
  apple-touch-icon.png  iOS 主屏图标（180×180，不透明背景）
"""

from PIL import Image, ImageDraw

BLUE_TOP = (18, 183, 245)      # #12B7F5
BLUE_BOTTOM = (10, 140, 200)   # 略深的同色系，做竖向渐变
WHITE = (255, 255, 255, 255)


def vertical_gradient(size, top, bottom):
    """生成竖直线性渐变底图"""
    img = Image.new("RGB", (size, size), top)
    px = img.load()
    for y in range(size):
        t = y / max(1, size - 1)
        r = round(top[0] + (bottom[0] - top[0]) * t)
        g = round(top[1] + (bottom[1] - top[1]) * t)
        b = round(top[2] + (bottom[2] - top[2]) * t)
        for x in range(size):
            px[x, y] = (r, g, b)
    return img


def rounded_mask(size, radius):
    """圆角矩形遮罩（L 模式）"""
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return mask


def draw_bubble(draw, box, tail_side="left"):
    """在给定方框内画一个白色对话气泡（圆角矩形 + 左下角小尾巴 + 两个点）"""
    x0, y0, x1, y1 = box
    w = x1 - x0
    h = y1 - y0
    radius = int(min(w, h) * 0.34)

    # 气泡主体
    draw.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=WHITE)

    # 左下角尾巴：一个三角
    tail_w = int(w * 0.16)
    tail_h = int(h * 0.16)
    tx = x0 + int(w * 0.22) if tail_side == "left" else x1 - int(w * 0.22)
    draw.polygon(
        [
            (tx, y1 - tail_h // 2),
            (tx + (tail_w if tail_side == "left" else -tail_w), y1 - tail_h // 2),
            (tx, y1 + tail_h),
        ],
        fill=WHITE,
    )

    # 两个小点，让气泡看起来像"正在输入"
    dot_r = max(2, int(w * 0.055))
    cy = y0 + h // 2
    gap = int(w * 0.18)
    for dx in (-gap, gap):
        cx = x0 + w // 2 + dx
        draw.ellipse([cx - dot_r, cy - dot_r, cx + dot_r, cy + dot_r], fill=BLUE_TOP)


def make_icon(size, radius_ratio=0.22, content_scale=1.0, opaque_bg=False):
    """生成一个图标。content_scale < 1 时内容内缩（maskable 用）"""
    base = vertical_gradient(size, BLUE_TOP, BLUE_BOTTOM)

    if opaque_bg:
        # iOS 主屏图标自带圆角裁切，这里铺满背景即可
        canvas = base
    else:
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        mask = rounded_mask(size, int(size * radius_ratio))
        canvas.paste(base, (0, 0), mask)

    # 气泡占画布约 52%，再按 content_scale 缩放
    side = int(size * 0.52 * content_scale)
    bw = side
    bh = int(side * 0.78)
    x0 = (size - bw) // 2
    y0 = (size - bh) // 2
    draw = ImageDraw.Draw(canvas)
    draw_bubble(draw, (x0, y0, x0 + bw, y0 + bh))
    return canvas


def main():
    out = "public"
    import os

    os.makedirs(out, exist_ok=True)

    make_icon(192).save(f"{out}/icon-192.png")
    make_icon(512).save(f"{out}/icon-512.png")
    # maskable：内容缩到 78%，保证被圆形裁切时不切到气泡
    make_icon(512, radius_ratio=0.0, content_scale=0.78, opaque_bg=True).save(
        f"{out}/icon-maskable-512.png"
    )
    # iOS 不透明、无圆角（系统自己加）
    make_icon(180, radius_ratio=0.0, content_scale=0.92, opaque_bg=True).save(
        f"{out}/apple-touch-icon.png"
    )
    print("已生成 4 个图标到 public/")


if __name__ == "__main__":
    main()
