package vn.vnpost.cdp.common.discovery;

import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Vu Sy Vuong (vusyvuong@gmail.com)
 */
@RestController
@RequestMapping("/api-discovery")
public class ApiDiscoveryController extends vn.vnpost.shared.discovery.DiscoveryController {
  public ApiDiscoveryController(ApplicationContext applicationContext) {
    super(applicationContext);
  }
}
