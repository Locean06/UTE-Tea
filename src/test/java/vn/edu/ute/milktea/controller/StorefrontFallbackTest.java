package vn.edu.ute.milktea.controller;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.ui.ExtendedModelMap;
import vn.edu.ute.milktea.entity.settings.GlobalSettings;
import vn.edu.ute.milktea.repository.catalog.CategoryRepository;
import vn.edu.ute.milktea.repository.catalog.ProductRepository;
import vn.edu.ute.milktea.repository.settings.GlobalSettingsRepository;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Regressions for home/menu HTTP 500 from optional database reads. */
class StorefrontFallbackTest {
    @Test void sharedSettingsQueryFailureDoesNotBlockHtml() {
        var settings = mock(GlobalSettingsRepository.class);
        when(settings.findById(1)).thenThrow(new DataAccessResourceFailureException("SQL unavailable"));
        assertNull(new GlobalModelAttributes(settings).globalSettings());
    }
    @Test void sharedSettingsLoadsNormally() {
        var settings = mock(GlobalSettingsRepository.class);
        var expected = new GlobalSettings();
        when(settings.findById(1)).thenReturn(Optional.of(expected));
        assertSame(expected, new GlobalModelAttributes(settings).globalSettings());
    }
    @Test void homeReturnsHtmlWithWarningWhenCategoryQueryFails() {
        var settings = mock(GlobalSettingsRepository.class);
        var categories = mock(CategoryRepository.class);
        var products = mock(ProductRepository.class);
        when(settings.findById(1)).thenReturn(Optional.empty());
        when(categories.findAllByActiveTrue()).thenThrow(new DataAccessResourceFailureException("Migration missing"));
        when(products.findAllByActiveTrue()).thenReturn(List.of());
        var promos = mock(vn.edu.ute.milktea.repository.market.VoucherRepository.class);
        when(promos.findByCode("GIAMGIA10")).thenReturn(Optional.empty());
        var model = new ExtendedModelMap();
        assertEquals("customer/menu", new CustomerPageController(settings,categories,products,promos).menuPage(null,null,null,null,model));
        assertEquals(true, model.get("catalogUnavailable"));
        assertEquals(List.of(), model.get("products"));
    }
}
