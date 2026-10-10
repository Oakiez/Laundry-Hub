#!/usr/bin/env python3
"""
รวมส่วนรายงานของทุกคน (doc/report/<ชื่อ>-sections.md) + front-matter.md เป็นไฟล์ Word เล่มเดียว
รูปแบบตามตัวอย่างของอาจารย์: ปก, บทคัดย่อ, คำนำ, สารบัญ, บทที่ 1-5, เอกสารอ้างอิง, ภาคผนวก (ฟอนต์ TH Sarabun New)

วิธีใช้ (ครั้งแรกต้องมี python-docx):
    python3 -m venv ~/.venvs/report && ~/.venvs/report/bin/pip install python-docx
    ~/.venvs/report/bin/python doc/report/build_report.py

กติกาการเขียนไฟล์ <ชื่อ>-sections.md (ดู doc/report/README.md):
  ## บทที่ N ...   เริ่มเนื้อหาของบทที่ N          ### หัวข้อ   หัวข้อหลัก (สคริปต์ใส่เลข N.M ให้อัตโนมัติ)
  #### หัวข้อย่อย  ได้เลข N.M.K ให้อัตโนมัติ          ## เอกสารอ้างอิง / ## ภาคผนวก  ส่วนท้ายเล่ม
  บรรทัดที่ขึ้นต้นด้วย > และ --- จะถูกข้าม (ใช้เขียนโน้ตถึงตัวเอง)   *( ... )* = ข้อความที่ต้องตรวจก่อนส่ง (ไฮไลต์เหลือง)
  หัวข้อ ### ชื่อเดียวกันจากหลายคนในบทเดียวกันจะถูกรวมไว้ใต้หัวข้อเดียว
"""
import argparse
import glob
import os
import re
from collections import OrderedDict

from docx import Document
from docx.enum.section import WD_ORIENT, WD_SECTION  # noqa: F401
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_COLOR_INDEX
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Inches, Pt, RGBColor

HERE = os.path.dirname(os.path.abspath(__file__))
FONT = "TH Sarabun New"
MONO = "Courier New"

CHAPTERS = OrderedDict([
    (1, "บทนำ"),
    (2, "ทฤษฎีและเทคโนโลยีที่เกี่ยวข้อง"),
    (3, "วิธีดำเนินการและการออกแบบระบบ"),
    (4, "ผลการพัฒนาและการทดสอบ"),
    (5, "สรุปผล อภิปรายผล และข้อเสนอแนะ"),
])

# ลำดับการรวมเนื้อหาในแต่ละหัวข้อ + ชื่อสั้นที่ใช้เมื่อมีหลายคนเขียนหัวข้อเดียวกัน
# ชื่อจริงที่ใช้ในเล่ม (ไม่ใช้ชื่อเล่น)
UPDATE_FIELDS = True   # --no-update-fields ปิดการให้ Word ถามอัปเดตสารบัญตอนเปิดไฟล์

AUTHORS = OrderedDict([("oak", "วงศธร ธน.ยอด"), ("peach", "คมชาญ น้อยเนียม"),
                       ("pond", "ปฏิภาณ มะนิลทิพย์"), ("shogun", "ภีมเดช กลั่นกิ่ง")])
# ชื่อเล่นที่อาจหลุดมาในเนื้อหาของไฟล์ <ชื่อ>-sections.md จะถูกแทนด้วยชื่อจริงตอนสร้างเล่ม
NICKNAMES = [("โอ๊ค", AUTHORS["oak"]), ("พีช", AUTHORS["peach"]), ("ปอนด์", AUTHORS["pond"]), ("โชกุน", AUTHORS["shogun"])]


# ภาพประกอบเพิ่มเติมท้ายส่วนของปอนด์ (ภาคผนวก): แผนภาพที่ใส่ในเล่มได้ และภาพเดโมบน Render
# ถ้าไฟล์ภาพยังไม่มี จะข้ามไปและแจ้งในรายการ "สิ่งที่ยังขาด" (ไม่ใส่ข้อความสีเหลืองในเล่ม)
EXTRA_FIGURES = {
    "pond": [
        ("State Diagram ของเครื่อง (Machine)", "pond-state-machine.png"),
        ("State Diagram ของรอบใช้งาน (Session)", "pond-state-session.png"),
        ("Sequence Diagram: สร้างออเดอร์ฝากซัก", "pond-sequence-order-create.png"),
        ("Sequence Diagram: จอง เริ่ม และจบรอบใช้งาน", "pond-sequence-session-booking.png"),
        ("Sequence Diagram: ชำระเงินของรอบใช้งาน (Checkout)", "pond-sequence-checkout.png"),
        ("เดโมบน Render: จองสำเร็จ", "pond-demo-01-booked.png"),
        ("เดโมบน Render: ชำระ QR จำลองสำเร็จ", "pond-demo-02-paid.png"),
        ("เดโมบน Render: รอบกำลังใช้งาน", "pond-demo-03-session-in-use.png"),
        ("เดโมบน Render: เครื่องกำลังใช้งาน", "pond-demo-04-machine-in-use.png"),
        ("เดโมบน Render: รอบเสร็จสิ้น", "pond-demo-05-completed.png"),
        ("เดโมบน Render: เครื่องกลับมาว่าง", "pond-demo-06-machine-available.png"),
        ("เดโมบน Render: แจ้งเตือนครบทุกเหตุการณ์", "pond-demo-07-notifications.png"),
        ("เดโมบน Render: ปุ่มชำระเงินหายหลังชำระแล้ว", "pond-demo-08-paid-button-hidden.png"),
    ],
}
PROJECT_ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def real_names(text):
    for nick, real in NICKNAMES:
        text = text.replace(nick, real)
    return text

# ใครต้องส่งบทไหน (ถ้าไม่มี จะแทรกข้อความเตือนสีเหลืองให้เห็น)
EXPECTED = {"oak": [1, 2, 3, 4, 5], "peach": [2, 3, 4, 5], "pond": [2, 3, 4, 5], "shogun": [2, 3, 4, 5]}

META = {
    "title": "ระบบจัดการร้านซักรีดและซักผ้าหยอดเหรียญ (LaundryHub)",
    "members": [
        "673380425-2   วงศธร ธน.ยอด",
        "673380395-5   คมชาญ น้อยเนียม",
        "673380589-2   ปฏิภาณ มะนิลทิพย์",
        "673380420-2   ภีมเดช กลั่นกิ่ง",
    ],
    "advisor": "อาจารย์ประจำวิชา: รศ. ดร.ปัญญาพล หอระตะ",
    "course": "รายงานนี้เป็นส่วนหนึ่งของการศึกษาวิชา CP353002 หลักการพัฒนาซอฟต์แวร์ (Principles of Software Development)",
    "term": "สาขาวิชาวิทยาการคอมพิวเตอร์ ภาคเรียน 1 ปีการศึกษา 2569",
    "college": "วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น",
}

TOKEN = re.compile(r"(\*\*.+?\*\*|`[^`]+`|\*\(.+?\)\*|\*[^*\s][^*]*\*|\[[^\]]+\]\([^)]+\))")


# ---------------------------------------------------------------- เครื่องมือจัดรูปแบบ
def set_rfonts(rpr, name):
    rf = rpr.find(qn("w:rFonts"))
    if rf is None:
        rf = OxmlElement("w:rFonts")
        rpr.insert(0, rf)
    for attr in ("w:asciiTheme", "w:hAnsiTheme", "w:eastAsiaTheme", "w:cstheme"):
        rf.attrib.pop(qn(attr), None)
    for attr in ("w:ascii", "w:hAnsi", "w:cs", "w:eastAsia"):
        rf.set(qn(attr), name)


def style_font(style, size, bold=False, color=RGBColor(0, 0, 0)):
    style.font.name = FONT
    style.font.size = Pt(size)
    style.font.bold = bold
    style.font.color.rgb = color
    set_rfonts(style.element.get_or_add_rPr(), FONT)


def fmt_run(run, size, bold=False, italic=False, mono=False, highlight=False):
    name = MONO if mono else FONT
    run.font.name = name
    set_rfonts(run._r.get_or_add_rPr(), name)
    run.font.size = Pt(size - 2 if mono else size)
    run.font.bold = bold
    run.font.italic = italic
    if highlight:
        run.font.highlight_color = WD_COLOR_INDEX.YELLOW


def add_inline(par, text, size=16, bold=False):
    text = real_names(text)
    for part in TOKEN.split(text):
        if not part:
            continue
        if part.startswith("**") and part.endswith("**") and len(part) > 4:
            fmt_run(par.add_run(part[2:-2]), size, True)
        elif part.startswith("`") and part.endswith("`"):
            fmt_run(par.add_run(part[1:-1]), size, bold, mono=True)
        elif part.startswith("*(") and part.endswith(")*"):
            fmt_run(par.add_run("(" + part[2:-2] + ")"), size, bold, highlight=True)
        elif part.startswith("*") and part.endswith("*") and len(part) > 2:
            fmt_run(par.add_run(part[1:-1]), size, bold, italic=True)
        elif part.startswith("[") and "](" in part:
            label, url = re.match(r"\[([^\]]+)\]\(([^)]+)\)", part).groups()
            fmt_run(par.add_run(label if label == url else f"{label} ({url})"), size, bold)
        else:
            fmt_run(par.add_run(part), size, bold)


def thai_justify(par):
    ppr = par._p.get_or_add_pPr()
    jc = OxmlElement("w:jc")
    jc.set(qn("w:val"), "thaiDistribute")
    ppr.append(jc)


def para(doc, text, size=16, bold=False, align=None, indent=None, hanging=None, space_after=6, justify=True):
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(space_after)
    p.paragraph_format.space_before = Pt(0)
    if indent is not None:
        p.paragraph_format.left_indent = Inches(indent)
    if hanging is not None:
        p.paragraph_format.first_line_indent = Inches(-hanging)
    if align is not None:
        p.alignment = align
    elif justify:
        thai_justify(p)
    add_inline(p, text, size, bold)
    return p


def add_field(par, instr):
    for kind, text in (("begin", None), (None, instr), ("separate", None), ("text", "(กดคลิกขวา → อัปเดตฟิลด์)"), ("end", None)):
        run = par.add_run()
        fmt_run(run, 16)
        if kind in ("begin", "separate", "end"):
            fc = OxmlElement("w:fldChar")
            fc.set(qn("w:fldCharType"), kind)
            run._r.append(fc)
        elif kind is None:
            it = OxmlElement("w:instrText")
            it.set(qn("xml:space"), "preserve")
            it.text = text
            run._r.append(it)
        else:
            run.text = text


def shade(cell, fill):
    tcpr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), fill)
    tcpr.append(shd)


# ---------------------------------------------------------------- อ่าน Markdown
def norm_key(title):
    t = re.sub(r"\((?:ส่วน)[^)]*\)", "", title)
    t = re.sub(r"^\s*\d+(?:\.(?:\d+|x))*\s+", "", t)   # "5.x ข้อจำกัด" กับ "ข้อจำกัด" ต้องรวมเป็นหัวข้อเดียวกัน
    return re.sub(r"\s+", " ", t).strip().lower()


def clean_title(title):
    t = re.sub(r"\s*\((?:ส่วน)[^)]*\)", "", title)
    t = re.sub(r"^\d+(?:\.(?:\d+|x))*\s+", "", t)
    return real_names(re.sub(r"\s+", " ", t).strip())


def split_file(path):
    """คืน dict: key(1-5/'ref'/'app') -> list[(title|None, raw_lines)]"""
    data = OrderedDict()
    cur = sec = None
    in_code = False
    for raw in open(path, encoding="utf-8").read().splitlines():
        s = raw.strip()
        if s.startswith("```"):
            in_code = not in_code
        if not in_code:
            m = re.match(r"^##\s+บทที่\s*(\d)", s)
            if m:
                cur, sec = int(m.group(1)), None
                data.setdefault(cur, []).append((None, []))
                continue
            if re.match(r"^##\s+เอกสารอ้างอิง", s):
                cur, sec = "ref", None
                data.setdefault(cur, []).append((None, []))
                continue
            if re.match(r"^##\s+ภาคผนวก", s):
                cur, sec = "app", None
                data.setdefault(cur, []).append((None, []))
                continue
            if re.match(r"^#{1,2}\s", s):
                cur = None
                continue
            m = re.match(r"^###\s+(.*)", s)
            if m and cur is not None:
                data[cur].append((m.group(1).strip(), []))
                continue
        if cur is not None:
            data[cur][-1][1].append(raw)
    return data


def parse_blocks(lines, base_dir):
    blocks, i = [], 0
    while i < len(lines):
        s = lines[i].strip()
        if not s or s == "---" or s.startswith(">") or s.startswith("<!--"):
            i += 1
            continue
        if s.startswith("```"):
            code, i = [], i + 1
            while i < len(lines) and not lines[i].strip().startswith("```"):
                code.append(lines[i].rstrip())
                i += 1
            i += 1
            blocks.append(("code", code))
            continue
        if s.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                cells = [c.strip() for c in lines[i].strip().strip("|").split("|")]
                if not all(re.fullmatch(r":?-{2,}:?", c) for c in cells):
                    rows.append(cells)
                i += 1
            blocks.append(("table", rows))
            continue
        m = re.match(r"^!\[(.*?)\]\((.+?)\)", s)
        if m:
            blocks.append(("image", m.group(1), os.path.normpath(os.path.join(base_dir, m.group(2)))))
        elif s.startswith("#### "):
            blocks.append(("sub", s[5:].strip()))
        elif re.match(r"^[-*•]\s+", s):
            blocks.append(("bullet", re.sub(r"^[-*•]\s+", "", s)))
        elif re.match(r"^\d+[.)]\s+", s):
            n, t = re.match(r"^(\d+)[.)]\s+(.*)", s).groups()
            blocks.append(("num", n, t))
        else:
            blocks.append(("para", s))
        i += 1
    return blocks


# ---------------------------------------------------------------- เขียน Word
class Book:
    def __init__(self):
        self.doc = Document()
        self.fig = 0
        self.warnings = []
        d = self.doc
        sec = d.sections[0]
        sec.page_width, sec.page_height = Cm(21.0), Cm(29.7)
        sec.left_margin, sec.right_margin = Inches(1.5), Inches(1.0)
        sec.top_margin, sec.bottom_margin = Inches(1.5), Inches(1.0)
        style_font(d.styles["Normal"], 16)
        style_font(d.styles["Heading 1"], 20, True)
        style_font(d.styles["Heading 2"], 18, True)
        style_font(d.styles["Heading 3"], 16, True)
        h1 = d.styles["Heading 1"].paragraph_format
        h1.page_break_before = True
        h1.space_after = Pt(12)
        h1.alignment = WD_ALIGN_PARAGRAPH.CENTER
        for name in ("Heading 2", "Heading 3"):
            pf = d.styles[name].paragraph_format
            pf.space_before, pf.space_after = Pt(10), Pt(4)
        if UPDATE_FIELDS:
            upd = OxmlElement("w:updateFields")
            upd.set(qn("w:val"), "true")
            d.settings.element.append(upd)
        d.core_properties.title = META["title"]
        d.core_properties.author = "กลุ่ม LaundryHub"

    # ---- เลขหน้า: ส่วนหน้า (บทคัดย่อ-สารบัญ) เป็นอักษรไทย ก ข ค ... ส่วนเนื้อหาตั้งแต่บทที่ 1 เป็นเลขอารบิกเริ่มที่ 1
    def start_section(self, fmt):
        """New page + new Word section whose page numbers use `fmt` ("thaiLetters" or "decimal") and restart at 1.
        The number sits at the top right (header). The cover (first section) has no number."""
        sec = self.doc.add_section(WD_SECTION.NEW_PAGE)
        sect_pr = sec._sectPr
        for old in sect_pr.findall(qn("w:pgNumType")):
            sect_pr.remove(old)
        pg = OxmlElement("w:pgNumType")
        pg.set(qn("w:fmt"), fmt)
        pg.set(qn("w:start"), "1")
        # schema order matters to Word: pgNumType comes before cols/docGrid etc.
        after = [qn("w:" + t) for t in ("cols", "formProt", "vAlign", "noEndnote", "titlePg", "textDirection", "bidi", "rtlGutter", "docGrid", "printerSettings")]
        anchor = next((el for el in sect_pr if el.tag in after), None)
        if anchor is not None:
            anchor.addprevious(pg)
        else:
            sect_pr.append(pg)
        if fmt == "thaiLetters":          # first numbered section creates the header; the next one inherits it
            sec.header.is_linked_to_previous = False
            hp = sec.header.paragraphs[0]
            hp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
            run = hp.add_run()
            fmt_run(run, 16)
            for kind in ("begin", None, "end"):
                if kind:
                    fc = OxmlElement("w:fldChar")
                    fc.set(qn("w:fldCharType"), kind)
                    run._r.append(fc)
                else:
                    it = OxmlElement("w:instrText")
                    it.set(qn("xml:space"), "preserve")
                    it.text = "PAGE"
                    run._r.append(it)
        return sec

    # ---- ส่วนหน้า
    def cover(self):
        d = self.doc
        c = WD_ALIGN_PARAGRAPH.CENTER
        para(d, "รายงานโครงงาน", 24, True, c, space_after=30)
        para(d, "เรื่อง", 18, False, c, space_after=6)
        para(d, META["title"], 22, True, c, space_after=40)
        para(d, "จัดทำโดย", 18, True, c, space_after=6)
        for m in META["members"]:
            para(d, m, 18, False, c, space_after=4)
        para(d, "", 16, False, c, space_after=24)
        para(d, META["advisor"], 18, False, c)
        para(d, META["course"], 16, False, c)
        para(d, META["term"], 16, False, c)
        para(d, META["college"], 16, False, c)

    def front_block(self, heading, blocks, base_dir, page_break=True):
        p = self.doc.add_paragraph()
        p.paragraph_format.page_break_before = page_break
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_after = Pt(12)
        fmt_run(p.add_run(heading), 20, True)
        self.render(blocks, base_dir)

    def toc(self):
        p = self.doc.add_paragraph()
        p.paragraph_format.page_break_before = True
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        fmt_run(p.add_run("สารบัญ"), 20, True)
        add_field(self.doc.add_paragraph(), 'TOC \\o "1-2" \\h \\z \\u')

    # ---- เนื้อหา
    def render(self, blocks, base_dir, sub_ctx=None):
        d = self.doc
        for b in blocks:
            kind = b[0]
            if kind == "para":
                para(d, b[1])
            elif kind == "bullet":
                para(d, "•  " + b[1], indent=0.5, hanging=0.25, space_after=3)
            elif kind == "num":
                para(d, f"{b[1]}.  {b[2]}", indent=0.5, hanging=0.3, space_after=3)
            elif kind == "sub":
                if sub_ctx:
                    sub_ctx["sub"] += 1
                    h = d.add_heading(f'{sub_ctx["ch"]}.{sub_ctx["sec"]}.{sub_ctx["sub"]} {clean_title(b[1])}', 3)
                else:
                    h = d.add_heading(clean_title(b[1]), 3)
                h.alignment = WD_ALIGN_PARAGRAPH.LEFT
            elif kind == "code":
                for ln in b[1]:
                    p = d.add_paragraph()
                    p.paragraph_format.left_indent = Inches(0.3)
                    p.paragraph_format.space_after = Pt(0)
                    fmt_run(p.add_run(ln if ln else " "), 16, mono=True)
                d.add_paragraph().paragraph_format.space_after = Pt(4)
            elif kind == "table":
                self.table(b[1])
            elif kind == "image":
                self.image(b[1], b[2])

    def table(self, rows):
        if not rows:
            return
        ncol = max(len(r) for r in rows)
        t = self.doc.add_table(rows=len(rows), cols=ncol)
        t.style = "Table Grid"
        for ri, row in enumerate(rows):
            for ci in range(ncol):
                cell = t.cell(ri, ci)
                cell.text = ""
                p = cell.paragraphs[0]
                p.paragraph_format.space_after = Pt(2)
                add_inline(p, row[ci] if ci < len(row) else "", 14, bold=(ri == 0))
                if ri == 0:
                    shade(cell, "D9D9D9")
        self.doc.add_paragraph().paragraph_format.space_after = Pt(6)

    def image(self, alt, path):
        if os.path.exists(path):
            self.doc.add_picture(path, width=Inches(5.6))
            self.doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
            self.fig += 1
            para(self.doc, f"ภาพที่ {self.fig} {alt}", 14, False, WD_ALIGN_PARAGRAPH.CENTER)
        else:
            self.warnings.append(f"ไม่พบภาพ: {path}")
            para(self.doc, f"*(ไม่พบไฟล์ภาพ {os.path.basename(path)})*", justify=False)

    def extra_figures(self, author):
        figs = EXTRA_FIGURES.get(author, [])
        present = [(alt, os.path.join(PROJECT_ROOT, "img", f)) for alt, f in figs if os.path.exists(os.path.join(PROJECT_ROOT, "img", f))]
        for alt, f in figs:
            if not os.path.exists(os.path.join(PROJECT_ROOT, "img", f)):
                self.warnings.append(f"ยังไม่มีภาพของ{AUTHORS[author]} (ข้ามในเล่ม): img/{f}")
        if not present:
            return
        para(self.doc, f"แผนภาพและภาพหลักฐานเพิ่มเติมของ{AUTHORS[author]}", 16, True, justify=False, space_after=6)
        for alt, path in present:
            self.image(alt, path)

    def placeholder(self, text):
        self.warnings.append(text)
        para(self.doc, f"*({text})*", justify=False)

    def chapter(self, ch, intro, merged):
        d = self.doc
        h = d.add_heading(f"บทที่ {ch} {CHAPTERS[ch]}", 1)
        h.alignment = WD_ALIGN_PARAGRAPH.CENTER
        for _author, blocks, bdir in intro:
            self.render(blocks, bdir)
        for sec_no, (_key, sec) in enumerate(merged.items(), 1):
            d.add_heading(f"{ch}.{sec_no} {sec['title']}", 2).alignment = WD_ALIGN_PARAGRAPH.LEFT
            ctx = {"ch": ch, "sec": sec_no, "sub": 0}
            many = len(sec["parts"]) > 1
            for author, blocks, bdir in sec["parts"]:
                if many:
                    para(d, f"ส่วนของ{AUTHORS[author]}", 16, True, justify=False, space_after=3)
                self.render(blocks, bdir, ctx)


def load_all():
    """คืน {author: {chapter_key: [(title, blocks, base_dir)]}}"""
    out = {}
    for path in sorted(glob.glob(os.path.join(HERE, "*-sections.md"))):
        author = os.path.basename(path).split("-")[0]
        if author not in AUTHORS:
            print(f"ข้ามไฟล์ที่ไม่รู้จักชื่อผู้เขียน: {path}")
            continue
        out[author] = {
            ch: [(t, parse_blocks(lines, HERE)) for t, lines in secs]
            for ch, secs in split_file(path).items()
        }
    return out


def front_matter():
    path = os.path.join(HERE, "front-matter.md")
    parts = OrderedDict()
    if not os.path.exists(path):
        return parts
    name, buf = None, []
    for raw in open(path, encoding="utf-8").read().splitlines():
        m = re.match(r"^##\s+(.*)", raw.strip())
        if m:
            if name:
                parts[name] = parse_blocks(buf, HERE)
            name, buf = m.group(1).strip(), []
        elif name:
            buf.append(raw)
    if name:
        parts[name] = parse_blocks(buf, HERE)
    return parts


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(HERE, "LaundryHub-report-draft.docx"))
    ap.add_argument("--no-update-fields", action="store_true", help="ไม่ใส่ updateFields (ใช้ตอนให้ Word อัปเดตสารบัญเอง)")
    args = ap.parse_args()
    global UPDATE_FIELDS
    UPDATE_FIELDS = not args.no_update_fields

    authors = load_all()
    book = Book()
    book.cover()
    book.start_section("thaiLetters")          # บทคัดย่อ - สารบัญ: ก ข ค ... (มุมบนขวา)
    fm = front_matter()
    for i, name in enumerate(("บทคัดย่อ", "คำนำ")):
        if name in fm:
            book.front_block(name, fm[name], HERE, page_break=(i > 0))
        else:
            book.front_block(name, [("para", f"*(ยังไม่มีเนื้อหา {name} ใน front-matter.md)*")], HERE, page_break=(i > 0))
    book.toc()
    book.start_section("decimal")              # บทที่ 1 เป็นต้นไป: 1 2 3 ... (มุมบนขวา)

    for ch in CHAPTERS:
        intro, merged = [], OrderedDict()
        for author in AUTHORS:
            for title, blocks in authors.get(author, {}).get(ch, []):
                if title is None:
                    if blocks:
                        intro.append((author, blocks, HERE))
                    continue
                key = norm_key(title)
                merged.setdefault(key, {"title": clean_title(title), "parts": []})["parts"].append((author, blocks, HERE))
        book.chapter(ch, intro, merged)
        for author in AUTHORS:
            if ch in EXPECTED.get(author, []) and ch not in authors.get(author, {}):
                book.placeholder(f"รอส่วนของ{AUTHORS[author]} ในบทที่ {ch}")

    # เอกสารอ้างอิง (รวมจากทุกคน ตัดซ้ำ เรียงตามตัวอักษร)
    refs = set()
    for author in AUTHORS:
        for _t, blocks in authors.get(author, {}).get("ref", []):
            refs.update(b[1] for b in blocks if b[0] in ("bullet", "para"))
    h = book.doc.add_heading("เอกสารอ้างอิง", 1)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER
    for r in sorted(refs):
        para(book.doc, r, indent=0.5, hanging=0.5, space_after=6)
    if not refs:
        book.placeholder("ยังไม่มีเอกสารอ้างอิง")

    # ภาคผนวก
    h = book.doc.add_heading("ภาคผนวก", 1)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER
    any_app = False
    for author in AUTHORS:
        for _t, blocks in authors.get(author, {}).get("app", []):
            if blocks:
                any_app = True
                para(book.doc, f"ส่วนของ{AUTHORS[author]}", 16, True, justify=False, space_after=3)
                book.render(blocks, HERE)
        book.extra_figures(author)
    if not any_app:
        book.placeholder("ยังไม่มีภาคผนวก")

    book.doc.save(args.out)
    print("สร้างไฟล์:", args.out)
    print("ผู้เขียนที่พบ:", ", ".join(authors) or "-")
    if book.warnings:
        print("สิ่งที่ยังขาด/ต้องตรวจ:")
        for w in book.warnings:
            print("  -", w)


if __name__ == "__main__":
    main()
