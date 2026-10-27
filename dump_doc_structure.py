import sys
from pathlib import Path
from docx import Document

def dump_doc_structure(doc_path, out_path):
    doc = Document(doc_path)
    with open(out_path, 'w', encoding='utf-8') as f:
        for i, para in enumerate(doc.paragraphs, start=1):
            style = para.style.name if para.style else 'NoStyle'
            f.write(f"{i}\t{style}\t{para.text}\n")
    print(f'Document structure written to {out_path}')

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print('Usage: python dump_doc_structure.py <docx_path> <output_txt>')
        sys.exit(1)
    docx_path = Path(sys.argv[1])
    out_path = Path(sys.argv[2])
    dump_doc_structure(docx_path, out_path)
