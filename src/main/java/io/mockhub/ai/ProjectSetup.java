package io.mockhub.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.ApiException;
import java.util.List;

public interface ProjectSetup {

  record Parsed(List<ObjectNode> stubs, String title) {}

  Parsed parse(String projectId, String format, String content);

  String fetch(String url);

  JsonNode contract(String content);

  void attachContract(String projectId, JsonNode spec, String fileName, String by);

  ObjectNode checkCrud(String projectId, JsonNode request);

  int createCrud(String projectId, JsonNode request);

  void checkProto(String name, String text);

  void addProto(String projectId, String name, String text);

  JsonNode drift(String projectId);

  ProjectSetup NONE = new ProjectSetup() {
    private ApiException off() {
      return new ApiException(409, "This MockHub cannot set up projects from the assistant");
    }

    @Override
    public Parsed parse(String projectId, String format, String content) {
      throw off();
    }

    @Override
    public String fetch(String url) {
      throw off();
    }

    @Override
    public JsonNode contract(String content) {
      throw off();
    }

    @Override
    public void attachContract(String projectId, JsonNode spec, String fileName, String by) {
      throw off();
    }

    @Override
    public ObjectNode checkCrud(String projectId, JsonNode request) {
      throw off();
    }

    @Override
    public int createCrud(String projectId, JsonNode request) {
      throw off();
    }

    @Override
    public void checkProto(String name, String text) {
      throw off();
    }

    @Override
    public void addProto(String projectId, String name, String text) {
      throw off();
    }

    @Override
    public JsonNode drift(String projectId) {
      throw off();
    }
  };
}
