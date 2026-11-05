package io.mockhub.contract;

import com.fasterxml.jackson.databind.JsonNode;

public record Contract(JsonNode spec, String source, String fileName, String updatedAt, String updatedBy, String notifyUrl, String stripPrefix) {

  public Contract(JsonNode spec, String source, String fileName, String updatedAt, String updatedBy, String notifyUrl) {
    this(spec, source, fileName, updatedAt, updatedBy, notifyUrl, null);
  }

  public Contract withNotifyUrl(String url) {
    return new Contract(spec, source, fileName, updatedAt, updatedBy, url, stripPrefix);
  }

  public ApiContract compile() {
    return ApiContract.of(spec, stripPrefix);
  }
}
