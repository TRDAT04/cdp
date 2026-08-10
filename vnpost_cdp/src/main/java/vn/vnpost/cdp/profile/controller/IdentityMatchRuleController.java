package vn.vnpost.cdp.profile.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import vn.vnpost.cdp.common.response.MethodResult;
import vn.vnpost.cdp.profile.service.match.IdentityMatchRuleCatalogService;
import vn.vnpost.shared.sercurity.CheckPermission;

@RestController
@RequestMapping("/api/v1/admin/identity-match-rules")
public class IdentityMatchRuleController {

    private final IdentityMatchRuleCatalogService catalogService;

    public IdentityMatchRuleController(IdentityMatchRuleCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    @CheckPermission(index = 1, title = "Xem danh sách rule so khớp định danh")
    public Mono<ResponseEntity> getCatalog() {
        return Mono.fromSupplier(catalogService::getCatalog)
                .map(catalog -> ResponseEntity.ok(
                        MethodResult.success(
                                catalog,
                                (long) catalog.getRules().size()
                        )
                ));
    }
}
