package io.mockhub.contract;

public enum DriftKind {
  UNDOCUMENTED_PATH("Path not in the contract", "request"),
  UNDOCUMENTED_METHOD("Method not in the contract", "request"),
  UNDOCUMENTED_QUERY_PARAMETER("Query parameter not in the contract", "request"),
  UNDOCUMENTED_HEADER("Header not in the contract", "request"),
  MISSING_PARAMETER("Required parameter missing", "request"),
  INVALID_PARAMETER("Parameter doesn't match its schema", "request"),
  UNDOCUMENTED_REQUEST_BODY("Request body the contract doesn't describe", "request"),
  MISSING_REQUEST_BODY("Required request body missing", "request"),
  UNDOCUMENTED_REQUEST_CONTENT_TYPE("Request content type not in the contract", "request"),
  REQUEST_BODY_SCHEMA("Request body doesn't match its schema", "request"),
  UNDOCUMENTED_STATUS("Status code not in the contract", "response"),
  UNDOCUMENTED_RESPONSE_CONTENT_TYPE("Response content type not in the contract", "response"),
  RESPONSE_BODY_SCHEMA("Response body doesn't match its schema", "response");

  private final String label;
  private final String side;

  DriftKind(String label, String side) {
    this.label = label;
    this.side = side;
  }

  public String label() {
    return label;
  }

  public String side() {
    return side;
  }
}
