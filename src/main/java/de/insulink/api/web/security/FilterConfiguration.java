package de.insulink.api.web.security;

import de.insulink.api.web.security.app.AppAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class FilterConfiguration {
  @Autowired
  private EquipmentFilter equipmentFilter;
  @Autowired
  private AppAuthenticationFilter appAuthenticationFilter;

  @Bean
  public FilterRegistrationBean<EquipmentFilter> provideEquipmentFilter() {
    var registrationBean = new FilterRegistrationBean<EquipmentFilter>();
    registrationBean.setFilter(equipmentFilter);
    registrationBean.setOrder(1);
    return registrationBean;
  }

  @Bean
  public FilterRegistrationBean<AppAuthenticationFilter> providePanelAuthorizationFilter() {
    var registrationBean = new FilterRegistrationBean<AppAuthenticationFilter>();
    registrationBean.setFilter(appAuthenticationFilter);
    registrationBean.setOrder(2);
    return registrationBean;
  }
}
