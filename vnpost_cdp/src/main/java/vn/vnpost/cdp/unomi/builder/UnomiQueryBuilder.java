package vn.vnpost.cdp.unomi.builder;

import org.springframework.stereotype.Component;
import vn.vnpost.cdp.unomi.dto.*;

import java.util.List;
import java.util.Map;

@Component
public class UnomiQueryBuilder {

    private static final String PROFILE_CODE = "properties.cdpProfileCode";
    private static final String SEGMENTS = "segments";

    /**
     * Query tìm các profile thuộc một segment. Property {@code segments} là mảng,
     * Unomi diễn giải {@code equals} trên property dạng mảng là "chứa giá trị".
     */
    public UnomiProfileSearchRequest buildSearchBySegment(String segmentId, int limit) {
        return UnomiProfileSearchRequest.builder()
                .condition(
                        UnomiCondition.builder()
                                .type("profilePropertyCondition")
                                .parameterValues(Map.of(
                                        "propertyName", SEGMENTS,
                                        "comparisonOperator", "equals",
                                        "propertyValue", segmentId
                                ))
                                .build()
                )
                .offset(0)
                .limit(limit)
                .build();
    }

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
