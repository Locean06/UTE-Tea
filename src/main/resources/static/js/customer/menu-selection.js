/* Shared size/topping dialog for the menu, marketplace and existing account cart. */
const MenuSelection = (() => {
    const $ = id => document.getElementById(id);
    let options, selected, editIndex = null, toppingIds = new Set();
    function node(tag,text,cls) {const n=document.createElement(tag);if(text!=null)n.textContent=text;if(cls)n.className=cls;return n;}
    function error(e) {$('selectionError').textContent=e.message;$('selectionError').classList.remove('d-none');}
    function renderSizes() {
        $('modalSizeContainer').replaceChildren();
        for(const variant of options.variants) {
            const button=node('button','Size '+variant.size+' · '+MilkTeaApi.formatVND(variant.price),'btn btn-sm '+(variant.id===selected.id?'btn-primary-custom':'btn-outline-secondary'));
            button.type='button';button.setAttribute('aria-pressed',String(variant.id===selected.id));
            button.onclick=()=>{selected=variant;renderSizes();calculate();};$('modalSizeContainer').append(button);
        }
    }
    function renderToppings() {
        $('toppingSelectionSection').hidden=!options.allowTopping;
        $('modalToppingList').replaceChildren();
        if(!options.allowTopping){toppingIds.clear();return;}
        for(const topping of options.toppings) {
            const label=node('label',null,'d-flex align-items-center gap-2 py-2 border-bottom small');
            const checkbox=node('input');checkbox.type='checkbox';checkbox.className='form-check-input modal-topping-cb';checkbox.value=topping.id;checkbox.checked=toppingIds.has(topping.id);
            checkbox.onchange=()=>{if(checkbox.checked)toppingIds.add(topping.id);else toppingIds.delete(topping.id);calculate();};
            label.append(checkbox,node('span',topping.name,'text-break me-auto'),node('span','+'+MilkTeaApi.formatVND(topping.price)));$('modalToppingList').append(label);
        }
    }
    function calculate() {
        if(!selected)return;const qty=Math.max(1,Math.min(99,Number($('modalProductQty').value)||1));$('modalProductQty').value=qty;
        const price=Number(selected.price),toppings=options.toppings.filter(t=>toppingIds.has(t.id)).reduce((sum,t)=>sum+Number(t.price),0),total=qty*(price+toppings);
        $('modalProductPrice').textContent=MilkTeaApi.formatVND(price);$('calcSizeLabel').textContent='Size '+selected.size;$('calcBasePrice').textContent=MilkTeaApi.formatVND(price);
        $('calcToppingPerCup').textContent=MilkTeaApi.formatVND(toppings);$('calcQty').textContent=qty;$('calcLineTotal').textContent=MilkTeaApi.formatVND(total);
        $('calcFormulaText').textContent=qty+' × ('+MilkTeaApi.formatVND(price)+' + '+MilkTeaApi.formatVND(toppings)+') = '+MilkTeaApi.formatVND(total);
    }
    async function open(productId,index=null) {
        await CustomerApp.initContext();
        options=await MilkTeaApi.get('/api/v1/public/market/products/'+productId+'/options');selected=options.selected;editIndex=index;toppingIds=new Set();
        const old=index==null?null:CustomerApp.getLocalCart()[index];
        if(old) {
            selected=options.variants.find(p=>p.size===old.size)||selected;
            for(const t of options.toppings)if((old.toppingIds||[]).includes(t.id)||(old.toppings||[]).some(previous=>previous.name===t.name))toppingIds.add(t.id);
        }
        $('selectionError').classList.add('d-none');$('modalProductName').textContent=selected.name;$('modalProductCategory').textContent='Tùy chọn món uống';
        $('modalProductImg').src=selected.imageUrl||'/images/ute-tea-product-fallback.svg';$('modalProductImg').alt=selected.name;
        $('modalProductQty').value=old?.quantity||1;$('modalProductNote').value=old?.note||'';
        $('confirmCartSelection').textContent=old?'Lưu thay đổi':'Thêm vào giỏ hàng';renderSizes();renderToppings();calculate();
        bootstrap.Modal.getOrCreateInstance($('customizeModal')).show();
        if(document.body.dataset.accountId)MilkTeaApi.post('/api/v1/account/viewed/'+productId).catch(console.warn);
    }
    async function confirm() {
        if(!selected)return;const button=$('confirmCartSelection');button.disabled=true;
        try {
            const toppings=options.toppings.filter(t=>toppingIds.has(t.id)).map(t=>({id:t.id,name:t.name,price:Number(t.price)}));
            const args=[selected,Number($('modalProductQty').value),$('modalProductNote').value.trim(),toppings];
            if(editIndex==null)await CustomerApp.addToCart(...args);else await CustomerApp.replaceItem(editIndex,...args);
            bootstrap.Modal.getInstance($('customizeModal')).hide();if(typeof triggerCartBounce==='function')triggerCartBounce();
        } catch(e) {error(e);} finally {button.disabled=false;}
    }
    return {open,confirm,adjust:delta=>{$('modalProductQty').value=Number($('modalProductQty').value)+delta;calculate();}};
})();
function openCustomizeModal(id) {return MenuSelection.open(id).catch(e=>MilkTeaApi.showToast(e.message,'error'));}
function adjustModalQty(delta) {MenuSelection.adjust(delta);}
function confirmAddToCart() {return MenuSelection.confirm();}
