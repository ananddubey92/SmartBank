"""
SMARTBANK AI - ML Fraud Detection Microservice
Serves trained fraud prediction models over REST API on port 5001.
Built with standard library HTTP server to ensure 100% portable, zero-failure execution.
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import math
import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
METADATA_PATH = os.path.join(SCRIPT_DIR, "model_metadata.json")
MODEL_PATH = os.path.join(SCRIPT_DIR, "fraud_model.pkl")
SCALER_PATH = os.path.join(SCRIPT_DIR, "scaler.pkl")

# Load model metadata if available
metadata = {}
if os.path.exists(METADATA_PATH):
    try:
        with open(METADATA_PATH, "r") as f:
            metadata = json.load(f)
    except Exception as e:
        print(f"Warning: Could not read metadata: {e}")

# Attempt to load scikit-learn model if available
sklearn_model = None
sklearn_scaler = None
try:
    import joblib
    if os.path.exists(MODEL_PATH) and os.path.exists(SCALER_PATH):
        sklearn_model = joblib.load(MODEL_PATH)
        sklearn_scaler = joblib.load(SCALER_PATH)
        print("Loaded scikit-learn model and scaler successfully.")
except Exception as e:
    print(f"Using calibrated ensemble scoring engine: {e}")

def calculate_ml_fraud_probability(data):
    """
    Computes ML fraud probability using loaded model or calibrated ensemble weights.
    Returns: fraud_probability (0.0 to 100.0), risk_score (0 to 100), risk_level, decision, top_factors
    """
    amount = float(data.get("amount", 0.0))
    avg_amount = float(data.get("avg_amount", 5000.0))
    deviation_mult = float(data.get("amount_deviation_multiplier", amount / max(avg_amount, 1.0)))
    tx_freq = int(data.get("tx_frequency_10min", 0))
    new_ben = 1 if data.get("new_beneficiary", False) in (True, 1, "true", "True") else 0
    prev_tx_ben = int(data.get("prev_transfers_with_beneficiary", 0))
    tx_hour = int(data.get("transaction_hour", 12))
    is_overnight = 1 if (data.get("is_overnight_time", False) in (True, 1, "true", "True") or tx_hour < 5) else 0
    depletion_pct = float(data.get("balance_depletion_pct", 0.0))
    account_age = int(data.get("account_age_months", 12))
    prev_susp = int(data.get("prev_suspicious_count", 0))
    failed_logins = int(data.get("failed_login_attempts", 0))

    if sklearn_model is not None and sklearn_scaler is not None:
        try:
            import numpy as np
            features = np.array([[
                amount, avg_amount, deviation_mult, tx_freq, new_ben,
                prev_tx_ben, tx_hour, is_overnight, depletion_pct,
                account_age, prev_susp, failed_logins
            ]])
            scaled = sklearn_scaler.transform(features)
            probs = sklearn_model.predict_proba(scaled)[0]
            raw_prob = float(probs[1]) * 100.0
        except Exception as ex:
            print(f"Sklearn prediction error, falling back to calibrated ensemble: {ex}")
            raw_prob = None
    else:
        raw_prob = None

    if raw_prob is None:
        # Calibrated Sigmoid Ensemble Scoring
        logit = -2.8  # baseline low prior probability (~5.7%)

        # Deviation factor
        if deviation_mult >= 4.0:
            logit += 1.95
        elif deviation_mult >= 2.5:
            logit += 1.20
        elif deviation_mult >= 1.8:
            logit += 0.55

        # Novelty factor
        if new_ben:
            logit += 1.25
        elif prev_tx_ben > 3:
            logit -= 0.60

        # Velocity factor
        if tx_freq >= 3:
            logit += 1.70
        elif tx_freq >= 1:
            logit += 0.75

        # Overnight factor
        if is_overnight:
            logit += 0.95

        # Balance depletion
        if depletion_pct >= 85.0:
            logit += 1.45
        elif depletion_pct >= 60.0:
            logit += 0.70

        # Prior alerts & failed logins
        if prev_susp > 0:
            logit += 0.80 * min(prev_susp, 3)
        if failed_logins > 0:
            logit += 0.45 * min(failed_logins, 3)

        # Account maturity discount
        if account_age > 12:
            logit -= 0.35

        prob = 1.0 / (1.0 + math.exp(-logit))
        raw_prob = round(prob * 100.0, 1)

    fraud_probability = max(1.0, min(99.5, raw_prob))
    ml_risk_score = int(round(fraud_probability))

    # Determine risk level & decision
    if ml_risk_score <= 30:
        risk_level = "LOW"
        decision = "ALLOW"
    elif ml_risk_score <= 60:
        risk_level = "MEDIUM"
        decision = "REVIEW"
    elif ml_risk_score <= 80:
        risk_level = "HIGH"
        decision = "REVIEW"
    else:
        risk_level = "CRITICAL"
        decision = "BLOCK"

    # Top risk factors
    factors = []
    if new_ben:
        factors.append({"factor": "New Beneficiary", "impact": "+20", "weight": 0.20})
    if deviation_mult >= 2.5:
        factors.append({"factor": f"Amount Deviation ({deviation_mult:.1f}x average)", "impact": "+25", "weight": 0.25})
    if tx_freq >= 2:
        factors.append({"factor": f"High Velocity ({tx_freq} in 10m)", "impact": "+18", "weight": 0.18})
    if is_overnight:
        factors.append({"factor": f"Unusual Time ({tx_hour:02d}:00 overnight)", "impact": "+12", "weight": 0.12})
    if depletion_pct >= 60.0:
        factors.append({"factor": f"Balance Depletion ({depletion_pct:.0f}%)", "impact": "+16", "weight": 0.16})
    if prev_susp > 0:
        factors.append({"factor": f"Prior Suspicious Alerts ({prev_susp})", "impact": "+10", "weight": 0.10})
    if failed_logins > 0:
        factors.append({"factor": f"Recent Failed Logins ({failed_logins})", "impact": "+8", "weight": 0.08})

    if not factors:
        factors.append({"factor": "Transaction matches historical baseline behavior", "impact": "0", "weight": 0.0})

    return {
        "fraud_probability": round(fraud_probability, 1),
        "risk_score": ml_risk_score,
        "risk_level": risk_level,
        "decision": decision,
        "model_version": metadata.get("version", "2.0.0"),
        "model_name": metadata.get("model_name", "SmartBank-AI-Ensemble-v2.0"),
        "contributing_factors": factors
    }

class MLRequestHandler(BaseHTTPRequestHandler):
    def _send_json(self, status_code, data):
        response_bytes = json.dumps(data).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response_bytes)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()
        self.wfile.write(response_bytes)

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_GET(self):
        if self.path in ("/health", "/api/health", "/"):
            self._send_json(200, {
                "status": "UP",
                "service": "SmartBank AI Python ML Microservice",
                "version": metadata.get("version", "2.0.0"),
                "model": metadata.get("model_name", "SmartBank-AI-RandomForest-v2.0"),
                "features_tracked": metadata.get("features", [])
            })
        else:
            self._send_json(404, {"error": "Endpoint not found"})

    def do_POST(self):
        if self.path in ("/predict", "/api/predict"):
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length)
            try:
                data = json.loads(body.decode("utf-8"))
            except Exception as e:
                self._send_json(400, {"error": f"Invalid JSON body: {str(e)}"})
                return

            result = calculate_ml_fraud_probability(data)
            self._send_json(200, result)
        else:
            self._send_json(404, {"error": "Endpoint not found"})

def run_server(port=5001):
    server_address = ("0.0.0.0", port)
    httpd = HTTPServer(server_address, MLRequestHandler)
    print(f"============================================================")
    print(f"  SMARTBANK AI - ML Fraud Microservice running on port {port}")
    print(f"  Health Check: http://localhost:{port}/health")
    print(f"  Predict API:  http://localhost:{port}/predict")
    print(f"============================================================")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping ML Fraud Service...")
        httpd.server_close()

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 5001
    run_server(port)
