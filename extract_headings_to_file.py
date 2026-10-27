import sys
from pathlib import Path
from docx import Document

def get_heading_paragraphs(doc_path):
    doc = Document(doc_path)
    headings = []
    for para in doc.paragraphs:
        style = para.style.name if para.style else ''
        if style.startswith('Heading'):
            level = style.replace('Heading', '')
            try:
                level_num = int(level)
            except ValueError:
                level_num = 1
            headings.append((level_num, para.text.strip()))
    return headings

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Usage: python extract_headings.py <docx_path>')
        sys.exit(1)
    docx_path = Path(sys.argv[1])
    if not docx_path.is_file():
        print(f'File not found: {docx_path}')
        sys.exit(1)
    headings = get_heading_paragraphs(docx_path)
    out_path = Path('headings_extracted.txt')
    with out_path.open('w', encoding='utf-8') as f:
        for level, text in headings:
            indent = '  ' * (level-1)
            f.write(f'{indent}- {text}\n')
    print(f'Headings written to {out_path}')
