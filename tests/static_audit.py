#!/usr/bin/env python3
"""Lightweight asset/reference smoke checks; no Maven/DB required."""
from pathlib import Path
from bs4 import BeautifulSoup
import re,sys,subprocess
root = Path(__file__).resolve().parents[1]
templates=root/'src/main/resources/templates'; static=root/'src/main/resources/static'
issues=[];checked=0
for file in templates.rglob('*.html'):
    text=file.read_text(encoding='utf-8')
    soup=BeautifulSoup(text, 'html.parser')
    ids=[e['id'] for e in soup.select('[id]')]
    dupe={i for i in ids if ids.count(i)>1}
    if dupe: issues.append(f'{file.relative_to(root)} duplicate ids: {sorted(dupe)}')
    for ref in re.findall(r'@\{/((?:js|css|images)/[^}?#]+)',text):
        checked+=1
        if not (static/ref).is_file(): issues.append(f'{file.relative_to(root)} missing asset: {ref}')
    for ref in re.findall(r'~\{([^\s}:]+)\s*::',text):
        checked+=1
        if not (templates/(ref+'.html')).is_file(): issues.append(f'{file.relative_to(root)} missing fragment: {ref}')
for f in (static/'js').rglob('*.js'):
    checked+=1
    run=subprocess.run(['node','--check',str(f)],capture_output=True,text=True)
    if run.returncode:issues.append(f'{f.relative_to(root)} JS: {run.stderr.strip()}')
menu=(templates/'customer/menu.html').read_text(encoding='utf-8')
for token in ['catalog-ui.js','function filterCatalog','function filterCategory','function scheduleCatalogFilter']:
    checked+=1
    if token not in menu: issues.append('customer/menu.html missing: '+token)
print(f'STATIC CHECKS: {checked} checks; {len(issues)} failures')
for x in issues: print('FAIL:',x)
sys.exit(bool(issues))
