package vn.vnpost.cdp.rule.dto;

/**
 * Supported action types in the Rule Engine Layer.
 * Each value maps to a specific Apache Unomi 2.4 action plugin type.
 */
public enum ActionType {

    INCREMENT,


    SUM,


    SET_PROPERTY,


    ADD_TO_SET
}
