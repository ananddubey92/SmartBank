package com.project.onlinebankingsystem.service;

import com.project.onlinebankingsystem.dto.FraudAssessmentResponse.RiskFactorItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MlFraudServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MlFraudServiceClient.class);

    private final RestTemplate restTemplate;
    private final JavaMlFraudEngine javaMlFraudEngine;

    @Value("${smartbank.ml.service.url:http://localhost:5001}")
    private String mlServiceUrl;

    public MlFraudServiceClient(RestTemplateBuilder restTemplateBuilder, JavaMlFraudEngine javaMlFraudEngine) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(1200))
                .setReadTimeout(Duration.ofMillis(1500))
                .build();
        this.javaMlFraudEngine = javaMlFraudEngine;
    }

    public Map<String, Object> predict(BigDecimal amount,
                                       BigDecimal avgAmount,
                                       double deviationMultiplier,
                                       int txFrequency10Min,
                                       boolean newBeneficiary,
                                       int prevTransfersWithBeneficiary,
                                       int transactionHour,
                                       boolean isOvernight,
                                       double balanceDepletionPct,
                                       int accountAgeMonths,
                                       int prevSuspiciousCount,
                                       int failedLoginAttempts) {

        // Prepare JSON payload for Python Microservice
        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", amount != null ? amount.doubleValue() : 0.0);
        payload.put("avg_amount", avgAmount != null ? avgAmount.doubleValue() : 5000.0);
        payload.put("amount_deviation_multiplier", deviationMultiplier);
        payload.put("tx_frequency_10min", txFrequency10Min);
        payload.put("new_beneficiary", newBeneficiary ? 1 : 0);
        payload.put("prev_transfers_with_beneficiary", prevTransfersWithBeneficiary);
        payload.put("transaction_hour", transactionHour);
        payload.put("is_overnight_time", isOvernight ? 1 : 0);
        payload.put("balance_depletion_pct", balanceDepletionPct);
        payload.put("account_age_months", accountAgeMonths);
        payload.put("prev_suspicious_count", prevSuspiciousCount);
        payload.put("failed_login_attempts", failedLoginAttempts);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    mlServiceUrl + "/predict",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map respBody = response.getBody();
                double prob = respBody.get("fraud_probability") instanceof Number
                        ? ((Number) respBody.get("fraud_probability")).doubleValue() : 10.0;
                int score = respBody.get("risk_score") instanceof Number
                        ? ((Number) respBody.get("risk_score")).intValue() : (int) Math.round(prob);
                String riskLevel = respBody.get("risk_level") != null ? respBody.get("risk_level").toString() : "LOW";
                String decision = respBody.get("decision") != null ? respBody.get("decision").toString() : "ALLOW";

                List<RiskFactorItem> factorItems = new ArrayList<>();
                if (respBody.get("contributing_factors") instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            String factor = m.get("factor") != null ? m.get("factor").toString() : "";
                            String impactStr = m.get("impact") != null ? m.get("impact").toString().replace("+", "") : "0";
                            int impact = 0;
                            try { impact = Integer.parseInt(impactStr); } catch (Exception ignored) {}
                            factorItems.add(RiskFactorItem.builder()
                                    .factorName(factor)
                                    .scoreImpact(impact)
                                    .description("Flagged by Python ML Microservice")
                                    .build());
                        }
                    }
                }

                Map<String, Object> result = new HashMap<>();
                result.put("fraudProbability", prob);
                result.put("mlRiskScore", score);
                result.put("riskLevel", riskLevel);
                result.put("decision", decision);
                result.put("factors", factorItems);
                result.put("modelName", respBody.get("model_name") != null ? respBody.get("model_name").toString() : "Python-ML-Service");
                result.put("connected", true);

                log.info("ML Fraud Microservice response: score={}, prob={}%", score, prob);
                return result;
            }
        } catch (Exception ex) {
            log.debug("Python ML service unreachable ({}: {}). Utilizing embedded Java ML engine fallback.",
                    ex.getClass().getSimpleName(), ex.getMessage());
        }

        // Seamless high-accuracy fallback to Java ML engine
        Map<String, Object> fallback = javaMlFraudEngine.predictFraudRisk(
                amount, avgAmount, deviationMultiplier, txFrequency10Min,
                newBeneficiary, prevTransfersWithBeneficiary, transactionHour,
                isOvernight, balanceDepletionPct, accountAgeMonths,
                prevSuspiciousCount, failedLoginAttempts
        );
        fallback.put("connected", false);
        return fallback;
    }
}
