package de.insulink.api.web;

import de.insulink.api.web.security.EquipmentFilter;
import de.insulink.api.web.security.app.AppAuthenticationFilter;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Boots a single app controller on MockMvc with everything it always needs and
 * nothing it does not: the signing key from {@link TestAuthentication}, and no
 * servlet filters. Both filters are left out on purpose — they run off
 * {@code config.ini} and the reflected endpoint list, which a slice has neither
 * of, and they are covered by their own tests. Repositories are supplied per
 * test with {@code @MockitoBean}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@WebMvcTest(excludeFilters = @ComponentScan.Filter(
  type = FilterType.ASSIGNABLE_TYPE,
  classes = {EquipmentFilter.class, AppAuthenticationFilter.class}))
@Import(TestAuthentication.class)
public @interface AppControllerTest {
  @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
  Class<?>[] value();
}
