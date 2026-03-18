package com.noteops.agent.api.search;

import com.noteops.agent.api.ApiEnvelope;
import com.noteops.agent.application.search.SearchApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final SearchApplicationService searchApplicationService;

    public SearchController(SearchApplicationService searchApplicationService) {
        this.searchApplicationService = searchApplicationService;
    }

    @GetMapping
    public ApiEnvelope<SearchResponse> search(@RequestParam("user_id") String userId,
                                              @RequestParam("query") String query) {
        log.info("module=SearchController action=search_request path=/api/v1/search user_id={} query_length={} trace_id=null",
            userId,
            query == null ? 0 : query.length());
        SearchApplicationService.SearchView view = searchApplicationService.search(userId, query);
        return ApiEnvelope.success(null, SearchResponse.from(view));
    }
}
