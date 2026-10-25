package vn.edu.ute.milktea.service.account;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.common.*;
import vn.edu.ute.milktea.dto.*;
import vn.edu.ute.milktea.entity.account.*;
import vn.edu.ute.milktea.entity.catalog.Product;
import vn.edu.ute.milktea.repository.account.*;
import vn.edu.ute.milktea.repository.catalog.ProductRepository;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.catalog.CatalogService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class CustomerDataService {
    private final AccountRepository accounts;
    private final AccountCartRepository carts;
    private final DeliveryAddressRepository addresses;
    private final ProductInterestRepository interests;
    private final ProductRepository products;
    private final CatalogService catalog;
    private final ObjectMapper mapper;

    public static Long accountId(CurrentActor actor) {
        if(actor==null||!actor.isAuthenticated()||actor.getAccountId()==null)
            throw BusinessException.unauthorized(ErrorCode.ACCESS_DENIED,"Vui lòng đăng nhập");
        return actor.getAccountId();
    }
    private Account lock(CurrentActor actor) {
        return accounts.findByIdWithLock(accountId(actor)).orElseThrow(()->BusinessException.unauthorized(ErrorCode.ACCESS_DENIED,"Tài khoản không tồn tại"));
    }
    @Transactional(readOnly=true)
    public CartDto.CartResponse cart(CurrentActor actor) {
        var cart=carts.findById(accountId(actor)).orElseGet(AccountCart::new);
        return cartView(cart);
    }
    @Transactional
    public CartDto.CartResponse replaceCart(CurrentActor actor,CartDto.ReplaceCartRequest input,String version) {
        Account account=lock(actor);
        var cart=carts.findById(account.getId()).orElseGet(()->{var c=new AccountCart();c.setAccount(account);c.setAccountId(account.getId());return c;});
        if(version==null||!version.equals(Long.toString(cart.getRevision())))
            throw BusinessException.conflict(ErrorCode.CART_VERSION_CONFLICT,"Giỏ hàng đã thay đổi. Vui lòng tải lại trước khi sửa.");
        if(input==null||input.getItems()==null||input.getItems().size()>100) throw invalid("Giỏ tối đa 100 dòng");
        // Validate and reprice before storing only IDs, quantity and note.
        input.getItems().forEach(this::lineView);
        try {cart.setItemsJson(mapper.writeValueAsString(input.getItems()));}
        catch(Exception e){throw invalid("Dữ liệu giỏ không hợp lệ");}
        cart.setRevision(cart.getRevision()+1);
        carts.save(cart);
        return cartView(cart);
    }
    private CartDto.CartResponse cartView(AccountCart cart) {
        List<CartDto.CartItemRequest> lines;
        try {lines=mapper.readValue(cart.getItemsJson(),new TypeReference<>(){});}
        catch(Exception e){throw new IllegalStateException("Không đọc được giỏ đã lưu",e);}
        var views=lines.stream().map(this::lineView).toList();
        return CartDto.CartResponse.builder().version(Long.toString(cart.getRevision())).items(views)
            .totalAmount(views.stream().map(CartDto.CartItemResponse::getLineTotal).reduce(BigDecimal.ZERO,BigDecimal::add)).build();
    }
    private Product available(Long id) {
        if(id==null)throw invalid("Thiếu mã món");
        var p=products.findById(id).orElseThrow(()->invalid("Món không tồn tại"));
        if(!Boolean.TRUE.equals(p.getActive())||!Boolean.TRUE.equals(p.getCategory().getActive()))throw invalid("Món đã ngừng bán");
        return p;
    }
    private CartDto.CartItemResponse lineView(CartDto.CartItemRequest line) {
        if(line==null||line.getQuantity()==null||line.getQuantity()<1||line.getQuantity()>99||
            (line.getNote()!=null&&line.getNote().length()>500))throw invalid("Số lượng phải từ 1–99; ghi chú tối đa 500 ký tự");
        Product p=available(line.getProductId());
        var ids=line.getToppingIds()==null?List.<Long>of():line.getToppingIds();
        if(ids.size()>20||new HashSet<>(ids).size()!=ids.size())throw invalid("Danh sách topping không hợp lệ");
        if(!ids.isEmpty()&&!Boolean.TRUE.equals(p.getCategory().getAllowTopping()))throw invalid("Món không cho phép topping");
        var toppings=ids.stream().map(id->{
            var t=available(id);
            if(!t.getCategory().getName().toLowerCase(Locale.ROOT).contains("topping"))throw invalid("Món bổ sung không phải topping");
            return CartDto.ToppingItemResponse.builder().id(t.getId()).productId(t.getId()).name(t.getName()).price(t.getPrice()).build();
        }).toList();
        var total=p.getPrice().add(toppings.stream().map(CartDto.ToppingItemResponse::getPrice).reduce(BigDecimal.ZERO,BigDecimal::add))
            .multiply(BigDecimal.valueOf(line.getQuantity()));
        return CartDto.CartItemResponse.builder().productId(p.getId()).productName(p.getName()).size(p.getSize()).unitPrice(p.getPrice())
            .quantity(line.getQuantity()).note(line.getNote()).lineTotal(total).toppingIds(ids).toppings(toppings).build();
    }
    @Transactional(readOnly=true)
    public List<CustomerDataDto.AddressView> addresses(CurrentActor actor) {
        return addresses.findByAccountIdOrderByIdAsc(accountId(actor)).stream().map(this::addressView).toList();
    }
    @Transactional
    public CustomerDataDto.AddressView saveAddress(CurrentActor actor,Long id,CustomerDataDto.AddressInput input) {
        Account account=lock(actor);
        var existing=addresses.findByAccountIdOrderByIdAsc(account.getId());
        if(id==null&&existing.size()>=20)throw invalid("Tối đa 20 địa chỉ");
        var address=id==null?new DeliveryAddress():addresses.findByIdAndAccountId(id,account.getId()).orElseThrow(()->BusinessException.notFound(ErrorCode.ACCESS_DENIED,"Không tìm thấy địa chỉ"));
        boolean makeDefault=input.defaultAddress()||existing.isEmpty()||address.isDefaultAddress();
        if(makeDefault){existing.forEach(a->a.setDefaultAddress(false));addresses.flush();}
        address.setAccount(account);address.setRecipient(input.recipient().trim());address.setPhone(input.phone().trim());
        address.setAddressLine(input.addressLine().trim());address.setDefaultAddress(makeDefault);
        return addressView(addresses.save(address));
    }
    @Transactional
    public void deleteAddress(CurrentActor actor,Long id) {
        Account account=lock(actor);
        var address=addresses.findByIdAndAccountId(id,account.getId()).orElseThrow(()->BusinessException.notFound(ErrorCode.ACCESS_DENIED,"Không tìm thấy địa chỉ"));
        boolean wasDefault=address.isDefaultAddress();addresses.delete(address);addresses.flush();
        if(wasDefault)addresses.findByAccountIdOrderByIdAsc(account.getId()).stream().findFirst().ifPresent(a->a.setDefaultAddress(true));
    }
    private CustomerDataDto.AddressView addressView(DeliveryAddress a) {return new CustomerDataDto.AddressView(a.getId(),a.getRecipient(),a.getPhone(),a.getAddressLine(),a.isDefaultAddress());}
    @Transactional
    public void interest(CurrentActor actor,Long productId,Boolean favorite,boolean viewed) {
        Account account=lock(actor);Product p=available(productId);
        var interest=interests.findByAccountIdAndProductId(account.getId(),p.getId()).orElseGet(()->{var i=new ProductInterest();i.setAccount(account);i.setProduct(p);return i;});
        if(favorite!=null)interest.setFavorite(favorite);
        if(viewed)interest.setLastViewedAt(Instant.now());
        interests.save(interest);
    }
    @Transactional(readOnly=true)
    public List<CustomerDataDto.InterestView> interests(CurrentActor actor,boolean favorites) {
        return interests.findByAccountIdOrderByLastViewedAtDesc(accountId(actor)).stream()
            .filter(i->Boolean.TRUE.equals(i.getProduct().getActive()))
            .filter(i->favorites?i.isFavorite():i.getLastViewedAt()!=null).limit(100)
            .map(i->new CustomerDataDto.InterestView(catalog.getProduct(i.getProduct().getId()),i.isFavorite(),i.getLastViewedAt())).toList();
    }
    private BusinessException invalid(String message){return BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,message);}
}
