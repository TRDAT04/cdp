package vn.vnpost.example.profile.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import vn.vnpost.example.common.response.MethodResult;
import vn.vnpost.example.profile.service.match.IdentityMatchRuleCatalogService;

/**
 * Màn "Danh sách rule so khớp định danh" — chỉ đọc.
 *
 * <p>Rule hiện cố định trong code nên chưa có POST/PUT/DELETE. Nút "Thêm rule" trên UI thuộc giai
 * đoạn sau, khi rule được đưa vào DB.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/identity-match-rules")
public class IdentityMatchRuleController {

    private final IdentityMatchRuleCatalogService catalogService;

    public IdentityMatchRuleController(IdentityMatchRuleCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * GET /api/v1/admin/identity-match-rules
     *
     * <p>Trả về bảng rule đang áp dụng + bảng trọng số tín hiệu + ghi chú áp dụng chung.
     * Dữ liệu tĩnh, dựng từ hằng số của hệ thống nên không truy vấn DB.
     */
    @GetMapping
    public Mono<ResponseEntity<MethodResult>> getCatalog() {
        return Mono.fromSupplier(catalogService::getCatalog)
                .map(catalog -> {
                    log.info("GET /api/v1/admin/identity-match-rules - {} rule, {} trọng số tín hiệu",
                            catalog.getRules().size(), catalog.getSignalWeights().size());
                    return ResponseEntity.ok(
                            MethodResult.success(catalog, (long) catalog.getRules().size()));
                });
    }
}
