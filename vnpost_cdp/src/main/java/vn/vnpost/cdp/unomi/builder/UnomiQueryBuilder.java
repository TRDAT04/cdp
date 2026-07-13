package vn.vnpost.cdp.unomi.builder;

import org.springframework.stereotype.Component;
import vn.vnpost.cdp.unomi.dto.*;

import java.util.List;
import java.util.Map;

@Component
public class UnomiQueryBuilder {

    private static final String PROFILE_CODE = "properties.cdpProfileCode";

    public UnomiProfileSearchRequest buildSearchByProfileCodes(List<String> profileCodes) {

        List<UnomiCondition> subConditions = profileCodes.stream()
                .distinct()
                .map(code ->
                        UnomiCondition.builder()
                                .type("profilePropertyCondition")
                                .parameterValues(Map.of(
                                        "propertyName", PROFILE_CODE,
                                        "comparisonOperator", "equals",
                                        "propertyValue", code
                                ))
                                .build())
                .toList();

        return UnomiProfileSearchRequest.builder()
                .condition(
                        UnomiCondition.builder()
                                .type("booleanCondition")
                                .parameterValues(Map.of(
                                        "operator", "or",
                                        "subConditions", subConditions
                                ))
                                .build()
                )
                .offset(0)
                .limit(subConditions.size())
                .build();
    }
}
