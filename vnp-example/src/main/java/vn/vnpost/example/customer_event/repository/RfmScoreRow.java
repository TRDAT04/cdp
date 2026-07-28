package vn.vnpost.example.customer_event.repository;

/**
 * Projection cho {@link CustomerEventRepository#findRfmScores}: 1 dòng
 * {@code [recency_score, frequency_score, monetary_score]} (1..5, NTILE quintile).
 */
public record RfmScoreRow(Integer recencyScore, Integer frequencyScore, Integer monetaryScore) {
}
