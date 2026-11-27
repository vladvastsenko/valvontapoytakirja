from pathlib import Path
import xml.etree.ElementTree as ET
languages = "en sv nb da et lv lt ru fr es it de pl".split()
def keys(folder):
    return {e.attrib["name"] for p in folder.glob("*.xml") for e in ET.parse(p).getroot() if e.tag == "string" and e.attrib.get("translatable") != "false"}
for module in ("app", "tarkastus"):
    root = Path(module) / "src/main/res"
    required = keys(root / "values")
    for lang in languages:
        missing = required - keys(root / ("values-" + lang))
        if missing:
            print(module, lang, sorted(missing))
            raise SystemExit(1)
print("All required string keys present")
