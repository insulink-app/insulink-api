package de.insulink.api.web;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * Every app endpoint returns a {@code CompletableFuture}, so MockMvc hands back
 * an unfinished request that has to be dispatched a second time before there is
 * a status or a body to assert on. This wraps both halves into one call.
 */
@RequiredArgsConstructor(staticName = "create", access = AccessLevel.PRIVATE)
public final class AsyncEndpoint {
  private final MockMvc mockMvc;

  public static AsyncEndpoint on(MockMvc mockMvc) {
    return create(mockMvc);
  }

  public ResultActions call(RequestBuilder requestBuilder) throws Exception {
    var started = mockMvc.perform(requestBuilder)
      .andExpect(request().asyncStarted())
      .andReturn();
    return mockMvc.perform(asyncDispatch(started));
  }
}
