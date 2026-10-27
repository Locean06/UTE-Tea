from pathlib import Path
import re,json,hashlib
ROOT=Path('src/main/java/vn/edu/ute/milktea')
def method_end(s,start):
 i=start;depth=0;state='code'
 while i<len(s):
  c=s[i];n=s[i:i+2]
  if state=='code':
   if s[i:i+3]=='"""':state='text';i+=3;continue
   if n=='//':state='line';i+=2;continue
   if n=='/*':state='comment';i+=2;continue
   if c=='"':state='str'
   elif c=="'":state='char'
   elif c=='{':depth+=1
   elif c=='}':
    depth-=1
    if depth==0:return i+1
  elif state=='line':
   if c=='\n':state='code'
  elif state=='comment':
   if n=='*/':state='code';i+=2;continue
  elif state=='text':
   if s[i:i+3]=='"""':state='code';i+=3;continue
  elif state in ['str','char']:
   if c=='\\':i+=2;continue
   if c==('"' if state=='str' else "'"):state='code'
  i+=1
 raise ValueError(start)
methods={};files={}
for p in ROOT.rglob('*.java'):
 s=p.read_text();files[str(p)]=hashlib.sha256(p.read_bytes()).hexdigest()
 for m in re.finditer(r'\b(public|private|protected)\s+(?:static\s+)?[\w.$<>?,\[\] ]+?\s+(\w+)\s*\([^;{}]*?\)\s*(?:throws\s+[\w., ]+)?\{',s,re.S):
  end=method_end(s,m.end()-1);body=s[m.start():end]
  record={'file':str(p),'class':p.stem,'method':m.group(2),'start':s[:m.start()].count('\n')+1,'end':s[:end].count('\n')+1,'code':body}
  methods.setdefault(p.stem+'.'+m.group(2),[]).append(record)
Path('reports/_work/methods.json').write_text(json.dumps(methods,ensure_ascii=False,indent=2))
Path('reports/_work/source_hashes.json').write_text(json.dumps(files,indent=2))
# All source text consumed for indexing; provide an auditable inventory without credentials/config values.
entities=[]
for p in (ROOT/'entity').rglob('*.java'):
 s=p.read_text();table=re.search(r'@Table\(name\s*=\s*"([^"]+)"',s)
 if not table:continue
 fields=[]
 for m in re.finditer(r'((?:\s*@[^\n]+\n)+)\s*private\s+([\w.<>?, ]+)\s+(\w+)\s*(?:=[^;]*)?;',s):
  annotation,typ,name=m.groups();col=re.search(r'@(?:Column|JoinColumn)\(\s*name\s*=\s*"([^"]+)"',annotation)
  fields.append({'name':name,'type':typ,'column':col.group(1) if col else name,'pk':'@Id' in annotation or '@EmbeddedId' in annotation,'relation':bool(re.search(r'@(ManyToOne|OneToOne|ManyToMany|OneToMany)',annotation)),'annotation':annotation.strip()})
 entities.append({'file':str(p),'class':p.stem,'table':table.group(1),'fields':fields})
Path('reports/_work/entities.json').write_text(json.dumps(entities,ensure_ascii=False,indent=2))
for name in ['AuthService','CustomerDataService','CancellationService','RefundService','InventoryService','MarketService','ReviewService','VnpayService','SettingsService','ProductImageService','SessionCartService','TableSessionService']:
 print('\n###',name)
 for k,records in methods.items():
  if not k.startswith(name+'.'):continue
  r=records[0]
  if not r['code'].startswith('public '):continue
  errors=re.findall(r'"([^"\n]*(?:không|Không|Chỉ|chỉ|đã|Đã|hết|thiếu|Thiếu|tồn|Tồn)[^"\n]*)"',r['code'])
  print(k,r['start'],r['end'],' | '.join(errors[:5]))
print('TOTAL',len(methods),'methods;',len(entities),'entities')
