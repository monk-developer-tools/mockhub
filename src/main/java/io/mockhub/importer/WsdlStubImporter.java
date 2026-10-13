package io.mockhub.importer;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mockhub.api.Jsons;
import io.mockhub.soap.WsdlImporter;

public final class WsdlStubImporter implements StubImporter {
  @Override
  public String format() {
    return "wsdl";
  }

  @Override
  public Result parse(Request request) {
    WsdlImporter.Result r = WsdlImporter.importWsdl(request.body(), request.option("path"), request.project().basePath());
    ObjectNode details = Jsons.object();
    details.put("service", r.serviceName());
    details.put("endpointPath", r.endpointPath());
    details.put("soapVersion", r.soapVersion());
    details.set("operations", Jsons.MAPPER.valueToTree(r.operations()));
    return new Result(r.stubs(), details);
  }
}
