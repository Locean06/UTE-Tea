import sys
from pathlib import Path
from docx import Document
from docx.enum.text import WD_PARAGRAPH_ALIGNMENT

def add_section(doc, title, level=1):
    doc.add_heading(title, level=level)

def add_placeholder(doc, text):
    p = doc.add_paragraph(text)
    p.alignment = WD_PARAGRAPH_ALIGNMENT.CENTER

def main():
    if len(sys.argv) != 2:
        print("Usage: python add_placeholders.py <docx_path>")
        sys.exit(1)
    docx_path = Path(sys.argv[1])
    doc = Document(docx_path)
    # Chapter 1 already exists, ensure we add subsections if needed
    add_section(doc, "Chương 1. KIẾN TRÚC ỨNG DỤNG", level=1)
    add_placeholder(doc, "[CẦN BỔ SUNG SƠ ĐỒ KIẾN TRÚC ỨNG DỤNG]")
    add_section(doc, "Chương 2. SƠ ĐỒ HOẠT ĐỘNG CÁC CHỨC NĂNG", level=1)
    roles = ["Khách hàng", "Thu ngân", "Nhân viên bếp", "Quản trị viên"]
    for role in roles:
        add_section(doc, f"{role}", level=2)
        add_placeholder(doc, f"[CẦN BỔ SUNG SƠ ĐỒ HOẠT ĐỘNG CHO {role.upper()}]")
        add_placeholder(doc, f"[CẦN BỔ SUNG CODE XỬ LÝ CHỨC NĂNG CHO {role.upper()}]")
        add_placeholder(doc, f"[CẦN BỔ SUNG ẢNH GIAO DIỆN CHO {role.upper()}]")
    add_section(doc, "Chương 3. GIAO DIỆN HỆ THỐNG", level=1)
    add_placeholder(doc, "[CẦN BỔ SUNG ẢNH GIAO DIỆN HỆ THỐNG]")
    doc.save(docx_path)
    print(f"Placeholders added to {docx_path}")

if __name__ == "__main__":
    main()
