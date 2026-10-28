package vn.edu.ute.milktea.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import vn.edu.ute.milktea.entity.settings.GlobalSettings;
import vn.edu.ute.milktea.repository.settings.GlobalSettingsRepository;

/** Storefront pages should still render when optional shop settings cannot be read. */
@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalModelAttributes {

    private final GlobalSettingsRepository settingsRepository;

    @ModelAttribute("settings")
    public GlobalSettings globalSettings() {
        try {
            return settingsRepository.findById(1).orElse(null);
        } catch (DataAccessException ex) {
            // The old advice threw before CustomerPageController could apply its
            // catalog fallback; this turned the public home into HTTP 500.
            log.error("Cannot load shared store settings; render page with defaults and inspect SQL migrations", ex);
            return null;
        }
    }
}
