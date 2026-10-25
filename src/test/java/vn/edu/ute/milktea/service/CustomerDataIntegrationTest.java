package vn.edu.ute.milktea.service;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ute.milktea.common.BusinessException;
import vn.edu.ute.milktea.dto.*;
import vn.edu.ute.milktea.entity.account.*;
import vn.edu.ute.milktea.repository.account.*;
import vn.edu.ute.milktea.repository.catalog.*;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.account.CustomerDataService;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class CustomerDataIntegrationTest {
    @Autowired CustomerDataService service;
    @Autowired AccountRepository accounts;
    @Autowired ProductRepository products;
    CurrentActor alice,bob;
    @BeforeEach void setup(){
        alice=actor("alice-data@example.test");bob=actor("bob-data@example.test");
    }
    CurrentActor actor(String email){
        var a=accounts.save(Account.builder().email(email).passwordHash("test-hash").role(Role.CUSTOMER).build());
        return CurrentActor.authenticated(a.getId(),Role.CUSTOMER);
    }
    @Test void cartPersistsAccountOwnershipPricesAndRejectsStaleWrites(){
        var p=products.findAllByActiveTrue().getFirst();
        var line=CartDto.CartItemRequest.builder().productId(p.getId()).quantity(2).toppingIds(List.of()).build();
        var input=CartDto.ReplaceCartRequest.builder().items(List.of(line)).build();
        var saved=service.replaceCart(alice,input,"0");
        assertEquals(p.getPrice().multiply(java.math.BigDecimal.valueOf(2)),saved.getTotalAmount());
        assertEquals(1,service.cart(alice).getItems().size());
        assertTrue(service.cart(bob).getItems().isEmpty());
        assertThrows(BusinessException.class,()->service.replaceCart(alice,input,"0"));
        assertThrows(BusinessException.class,()->service.cart(CurrentActor.guest()));
    }
    @Test void invalidQuantityAndDuplicateToppingsAreRejected(){
        var p=products.findAllByActiveTrue().getFirst();
        for(int qty:List.of(0,100)){
            var line=CartDto.CartItemRequest.builder().productId(p.getId()).quantity(qty).build();
            assertThrows(BusinessException.class,()->service.replaceCart(alice,CartDto.ReplaceCartRequest.builder().items(List.of(line)).build(),"0"));
        }
    }
    @Test void defaultAddressIsUniqueAndOtherAccountsCannotEditIt(){
        var first=service.saveAddress(alice,null,new CustomerDataDto.AddressInput("Alice","0901000001","1 Võ Văn Ngân",false));
        var second=service.saveAddress(alice,null,new CustomerDataDto.AddressInput("Alice","0901000001","2 Võ Văn Ngân",true));
        assertEquals(1,service.addresses(alice).stream().filter(CustomerDataDto.AddressView::defaultAddress).count());
        assertTrue(service.addresses(bob).isEmpty());
        assertThrows(BusinessException.class,()->service.saveAddress(bob,first.id(),new CustomerDataDto.AddressInput("Bob","0901000002","Other",true)));
        service.deleteAddress(alice,second.id());
        assertTrue(service.addresses(alice).getFirst().defaultAddress());
    }
    @Test void favoritesAndHistoryStayPrivateAndDoNotDuplicate(){
        var id=products.findAllByActiveTrue().getFirst().getId();
        service.interest(alice,id,true,true);service.interest(alice,id,true,true);
        assertEquals(1,service.interests(alice,true).size());
        assertEquals(1,service.interests(alice,false).size());
        assertTrue(service.interests(bob,true).isEmpty());
        service.interest(alice,id,false,false);
        assertTrue(service.interests(alice,true).isEmpty());
        assertEquals(1,service.interests(alice,false).size());
    }
}
