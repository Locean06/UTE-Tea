package vn.edu.ute.milktea.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.ute.milktea.common.BusinessException;
import vn.edu.ute.milktea.dto.OrderDto;
import vn.edu.ute.milktea.entity.account.*;
import vn.edu.ute.milktea.entity.order.*;
import vn.edu.ute.milktea.repository.order.OrderRepository;
import vn.edu.ute.milktea.security.CurrentActor;
import vn.edu.ute.milktea.service.order.OrderService;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class OrderIdempotencyAccessTest {
 @Mock OrderRepository orders;@InjectMocks OrderService service;
 @Test void anotherAccountCannotReadCounterOrderByReusingKey(){
  when(orders.findByIdempotencyKey("known-key")).thenReturn(Optional.of(Order.builder().id(7L).source(OrderSource.COUNTER).account(Account.builder().id(1L).build()).build()));
  assertThrows(BusinessException.class,()->service.createCounterOrder(new OrderDto.CreateCounterOrderRequest(),CurrentActor.authenticated(2L,Role.CUSTOMER),"known-key"));
 }
 @Test void anonymousReplayIsNotAnOrderHistoryCredential(){
  when(orders.findByIdempotencyKey("known-key")).thenReturn(Optional.of(Order.builder().id(7L).source(OrderSource.COUNTER).build()));
  assertThrows(BusinessException.class,()->service.createCounterOrder(new OrderDto.CreateCounterOrderRequest(),null,"known-key"));
 }
}
