"""
从「大肥鱼」表情设定表里切出 6 个表情头像。

设定表是 2304x1536，3 列 x 2 行，每格 768x768：
  第一行：平静 / 微笑 / 眨眼
  第二行：不爽 / 生气 / 张嘴

每个格子单独去白底（从四角泛洪，保留围裙等内部白色），再裁边缩放到 256。
"""

from PIL import Image, ImageDraw
import os

SRC = r"C:\Users\waid\Desktop\pic\资产\人物\Finish_大肥鱼\character_sheet-150923_00001_.png"
OUT_DIR = r"D:\develop\2025_6\frontend\public\faces"
PREVIEW = r"D:\develop\2025_6\.tmp\preview\faces-montage.jpg"

COLS, ROWS = 3, 2
MAGIC = (255, 0, 255, 255)
TOLERANCE = 34
SIZE = 256

# 与网格顺序一一对应
KEYS = [
    ["calm", "smile", "wink"],
    ["grumpy", "angry", "shout"],
]


def cut_cell(sheet: Image.Image, col: int, row: int) -> Image.Image:
    w, h = sheet.size
    cw, ch = w // COLS, h // ROWS
    return sheet.crop((col * cw, row * ch, (col + 1) * cw, (row + 1) * ch))


def remove_bg(cell: Image.Image) -> Image.Image:
    img = cell.convert("RGBA")
    w, h = img.size
    # 只从四角灌，避免把角色身上的白色（围裙、花边）也清掉
    for start in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]:
        try:
            ImageDraw.floodfill(img, start, MAGIC, thresh=TOLERANCE)
        except Exception:
            pass
    px = img.load()
    for y in range(h):
        for x in range(w):
            if px[x, y] == MAGIC:
                px[x, y] = (0, 0, 0, 0)
    return img


def trim(img: Image.Image) -> Image.Image:
    bbox = img.getbbox()
    return img.crop(bbox) if bbox else img


def fit_square(img: Image.Image, size: int) -> Image.Image:
    """等比缩放后居中放进透明方画布，保证 6 张头像视觉大小一致"""
    w, h = img.size
    scale = size / max(w, h)
    resized = img.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(resized, ((size - resized.size[0]) // 2, (size - resized.size[1]) // 2))
    return canvas


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)

    sheet = Image.open(SRC).convert("RGBA")
    print(f"设定表: {sheet.size}")

    produced = []
    for row in range(ROWS):
        for col in range(COLS):
            key = KEYS[row][col]
            cell = cut_cell(sheet, col, row)
            face = fit_square(trim(remove_bg(cell)), SIZE)
            path = os.path.join(OUT_DIR, f"face-{key}.png")
            face.save(path, optimize=True)
            produced.append((key, path))
            print(f"  写出 face-{key}.png")

    # 拼一张预览图，方便一次看清 6 个表情
    montage = Image.new("RGB", (SIZE * COLS, SIZE * ROWS), (232, 244, 252))
    for idx, (key, path) in enumerate(produced):
        img = Image.open(path).convert("RGBA")
        montage.paste(img, ((idx % COLS) * SIZE, (idx // COLS) * SIZE), img)
    montage.save(PREVIEW, quality=88)
    print(f"预览: {PREVIEW}")


if __name__ == "__main__":
    main()
