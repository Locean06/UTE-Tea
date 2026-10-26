#!/usr/bin/env python3
"""Real Chromium DOM tests for storefront pagination/buttons without a database."""
from pathlib import Path
import shutil
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1]
html='''<!DOCTYPE html><html lang="vi"><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/><style>
*{box-sizing:border-box}body{margin:0;font-family:system-ui;background:#faf9f3;color:#303a2d}.shell{max-width:1100px;padding:16px;margin:auto}
.row{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,210px),1fr));gap:16px}.col{min-width:0}.product-card{display:flex;flex-direction:column;background:white;border:1px solid #e4e4d9;border-radius:14px;overflow:hidden;height:100%}
.product-img-wrapper{position:relative}.product-img{width:100%;aspect-ratio:4/3;object-fit:cover;background:#eeede4}.p-3{padding:12px;display:flex;flex-direction:column;gap:8px}
button{cursor:pointer;min-height:32px;background:#565c45;color:white;border-radius:8px;padding:6px 10px;border:0}.text-truncate{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.d-none{display:none}
</style></head><body><main class="shell"><h1>UTE Tea — Thực đơn</h1><div id="uteCatalogEmpty" class="d-none">Không tìm thấy món</div><div id="productsGrid" class="row"><div class="col product-item" data-id="999">Thẻ SSR cũ</div></div>
<button id="catalogPrevious">Trước</button><span id="catalogPageLabel"></span><button id="catalogNext">Tiếp</button></main></body></html>'''
products=[{'id':i,'name':f'Trà số {i}', 'price':40000+i*1000,'size':'M','imageUrl':'','categoryId':2,'categoryName':'Trà sữa','shopId':None} for i in range(1,31)]
with sync_playwright() as p:
    chrome = shutil.which('chromium') or shutil.which('chromium-browser') or shutil.which('google-chrome')
    args = {'headless':True, 'args':['--no-sandbox','--disable-dev-shm-usage']}
    if chrome: args['executable_path'] = chrome
    browser=p.chromium.launch(**args)
    page=browser.new_page(viewport={'width':390,'height':844})
    page.set_content(html)
    page.add_script_tag(content='window.events=[];window.favoriteProduct=(id)=>events.push(["favorite",id]);window.openCustomizeModal=(id)=>events.push(["add",id]);window.MilkTeaApi={formatVND:n=>Number(n).toLocaleString("vi-VN")+" ₫"};')
    page.add_script_tag(path=str(root/'src/main/resources/static/js/customer/catalog-ui.js'))
    page.evaluate('(items)=>UTETeaCatalog.render({items,total:30,size:20,page:0},0)',products[:20])
    assert page.locator('.product-item').count()==20, 'first page must contain exactly 20 API cards'
    assert page.locator('[data-id="999"]').count()==0, 'stale SSR cards should be replaced'
    assert not page.locator('#catalogPrevious').is_enabled()
    assert page.locator('#catalogNext').is_enabled()
    page.locator('.product-item button').first.click()
    page.locator('.product-item button').nth(1).click()
    assert page.evaluate('events')==[['favorite',1],['add',1]],'buttons must perform actions'
    page.evaluate('(items)=>UTETeaCatalog.render({items,total:30,size:20,page:1},1)',products[20:])
    assert page.locator('.product-item').count()==10, 'page 2 must render products not present in original HTML'
    assert page.locator('[data-id="21"]').count()==1
    assert page.locator('#catalogPrevious').is_enabled()
    assert not page.locator('#catalogNext').is_enabled()
    assert page.locator('#catalogPageLabel').inner_text()=='Trang 2 / 2 · 30 món'
    escaped=dict(products[0]);escaped['name']='<img src=x onerror=alert(1)>'
    page.evaluate('(item)=>UTETeaCatalog.render({items:[item],total:1,size:20,page:0},0)',escaped)
    assert page.locator('.product-card h5').inner_text()==escaped['name']
    assert page.locator('.product-card h5 img').count()==0,'product names must not inject HTML'
    page.evaluate('UTETeaCatalog.showError("API 500")')
    assert page.locator('.product-item').count()==1,'API fallback must preserve existing cards'
    assert 'API 500' in page.locator('#uteCatalogError').inner_text()
    page.evaluate('UTETeaCatalog.render({items:[],total:0,size:20,page:0},0)')
    assert page.locator('#uteCatalogEmpty').is_visible(),'empty state must be visible'
    for width in [390,768,1024,1440]:
        page.set_viewport_size({'width':width,'height':850})
        page.evaluate('(items)=>UTETeaCatalog.render({items,total:30,size:20,page:0},0)',products[:20])
        assert page.evaluate('document.documentElement.scrollWidth<=window.innerWidth'),f'horizontal overflow at {width}px'
    browser.close()
print('BROWSER CHECKS: 11 assertions / responsive 4 widths: PASS')
