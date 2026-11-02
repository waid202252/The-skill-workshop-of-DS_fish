"""
把「大肥鱼」立绘处理成前端可用的素材。

做三件事：
  1. 去白底：从四角做 flood fill，只清除与边缘连通的白色区域，
     角色身上的白色（围裙、花边）不受影响——这是关键，不能简单"全局替换白色"。
  2. 裁掉透明边距，保证排版时没有多余留白。
  3. 生成两个尺寸：
       mascot.png      —— 登录页大图，透明背景
       mascot-face.png —— 头像/图标用的头部方形裁切
"""

from PIL import Image, ImageDraw

SRC = r"C:\Users\waid\Desktop\pic\资产\人物\Finish_大肥鱼\fish_front.png"
OUT_DIR = r"D:\develop\2025_6\frontend\public"

MAGIC = (255, 0, 255, 255)   # 用来标记"背景"的哨兵色
TOLERANCE = 42               # 颜色容差


def remove_white_background(img: Image.Image) -> Image.Image:
    img = img.convert("RGBA")
    w, h = img.size
    # 从四角各灌一次，覆盖白色背景可能被角色分割成多块的情况
    for start in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]:
        try:
            ImageDraw.floodfill(img, start, MAGIC, thresh=TOLERANCE)
        except Exception as exc:  # 某些像素点可能已经在边界外
            print(f"  floodfill 跳过 {start}: {exc}")
    # 把哨兵色改成全透明
    px = img.load()
    for y in range(h):
        for x in range(w):
            if px[x, y] == MAGIC:
                px[x, y] = (0, 0, 0, 0)
    return img


def trim(img: Image.Image) -> Image.Image:
    bbox = img.getbbox()
    if bbox:
        return img.crop(bbox)
    return img


def main():
    print(f"读取: {SRC}")
    src = Image.open(SRC)
    print(f"  原始尺寸: {src.size}")

    transparent = remove_white_background(src)
    trimmed = trim(transparent)
    tw, th = trimmed.size
    print(f"  去底并裁边后: {trimmed.size}")

    # ---- 大图：限制最长边 700px，兼顾清晰度和体积
    max_side = 700
    scale = min(1.0, max_side / max(tw, th))
    big = trimmed.resize((max(1, round(tw * scale)), max(1, round(th * scale))), Image.LANCZOS)
    big.save(f"{OUT_DIR}/mascot.png", optimize=True)
    print(f"  写出 mascot.png -> {big.size}")

    # ---- 头像：方形裁切，纵向Offset 让脸部落在画面中间
    # 0.20 这个偏移是看着成品调的：太小会只剩头发，太大又会切掉下巴
    side = min(tw, int(th * 0.52))
    left = max(0, (tw - side) // 2)
    top = min(max(0, int(th * 0.20)), max(0, th - side))
    face = trimmed.crop((left, top, left + side, top + side))
    face = face.resize((256, 256), Image.LANCZOS)

    # 头像下面垫一层淡蓝底，避免透明 PNG 在某些地方显示成黑底
    canvas = Image.new("RGBA", (256, 256), (231, 246, 255, 255))
    canvas.alpha_composite(face)
    canvas.convert("RGB").save(f"{OUT_DIR}/mascot-face.png", optimize=True)
    print("  写出 mascot-face.png -> 256x256")

    # ---- 图标：同样的头像缩到 64 和 180（iOS 主屏）
    canvas.resize((64, 64), Image.LANCZOS).save(f"{OUT_DIR}/icon-64.png", optimize=True)
    canvas.convert("RGB").resize((180, 180), Image.LANCZOS).save(
        f"{OUT_DIR}/apple-touch-icon.png", optimize=True
    )
    print("  写出 icon-64.png / apple-touch-icon.png")


if __name__ == "__main__":
    main()
