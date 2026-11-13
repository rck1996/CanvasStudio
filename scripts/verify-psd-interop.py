"""Independent check of LayeredPsdExchangeTest's exported synthetic document.

Requires psd-tools 1.16.x in an isolated Python environment. Does not edit the PSD.
"""
import io
import sys
from pathlib import Path

from PIL import Image
from psd_tools import PSDImage
from psd_tools.api.layers import PixelLayer
from psd_tools.constants import BlendMode, Compression


def audit(path: str) -> None:
    psd = PSDImage.open(path)
    assert psd.size == (768, 640), psd.size
    assert [layer.name for layer in psd] == ["Base ñ", "Luz 夜", "Boceto oculto"]
    base, light, sketch = psd
    assert base.visible and light.visible and not sketch.visible
    assert light.opacity == 128 and light.blend_mode == BlendMode.MULTIPLY
    assert light.clipping, "Upper layer should keep its clipping flag"
    rgba = base.topil().convert("RGBA")
    visible = rgba.getpixel((130 - base.left, 514 - base.top))
    hidden = rgba.getpixel((340 - base.left, 514 - base.top))
    assert visible[0] > 200 and visible[3] > 0, visible
    assert hidden[3] < visible[3], (hidden, visible)
    merged = psd.topil().convert("RGBA")
    assert merged.size == psd.size
    assert merged.getpixel((4, 4))[3] == 0
    print("PSD INTEROP OK: 768x640; 3 bottom-to-top raster layers; Unicode, opacity, clipping and alpha")


def make_fixture(path: str) -> None:
    """Write a small independent source for the Android reader regression."""
    psd = PSDImage.new("RGBA", (32, 24))
    base = PixelLayer.frompil(Image.new("RGBA", (12, 8), (255, 0, 0, 160)), psd,
                              "Base externa", top=3, left=2, compression=Compression.RLE)
    light = PixelLayer.frompil(Image.new("RGBA", (9, 7), (0, 0, 255, 255)), psd,
                               "Luz externa", top=6, left=14, compression=Compression.ZIP)
    base.name = "Base externa ñ"
    light.name = "Luz externa 夜"
    light.visible = False
    light.opacity = 128
    light.blend_mode = BlendMode.MULTIPLY
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    buffer = io.BytesIO()
    psd.save(buffer)
    destination.write_bytes(buffer.getvalue())


if __name__ == "__main__":
    if sys.argv[1] == "--make-fixture":
        make_fixture(sys.argv[2])
    else:
        audit(sys.argv[1])
