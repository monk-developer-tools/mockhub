package io.mockhub.ext;

import com.github.tomakehurst.wiremock.admin.Router;
import com.github.tomakehurst.wiremock.extension.AdminApiExtension;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.mockhub.auth.Principal;
import java.util.function.Function;
import io.mockhub.api.ApiRouter;
import io.mockhub.api.ChangeAudit;
import io.mockhub.controller.ApiController;
import io.mockhub.web.RedirectToUiTask;
import io.mockhub.web.StaticUiTask;
import java.util.List;

public final class MockHubAdminExtension implements AdminApiExtension {
  private static final List<RequestMethod> API_METHODS =
      List.of(RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE);

  private final ApiRouter api;

  public MockHubAdminExtension(Function<Request, Principal> principals, List<ApiController> controllers) {
    this(principals, change -> ChangeAudit.Pending.NONE, controllers);
  }

  public MockHubAdminExtension(Function<Request, Principal> principals, ChangeAudit audit, List<ApiController> controllers) {
    this.api = new ApiRouter(principals, audit);
    controllers.forEach(c -> c.register(api));
  }

  @Override
  public String getName() {
    return "mockhub";
  }

  @Override
  public void contributeAdminApiRoutes(Router router) {
    API_METHODS.forEach(method -> router.add(method, "/mockhub/api/**", api.task()));
    router.add(RequestMethod.GET, "/mockhub/ui/**", new StaticUiTask());
    router.add(RequestMethod.GET, "/mockhub", new RedirectToUiTask());
  }
}
