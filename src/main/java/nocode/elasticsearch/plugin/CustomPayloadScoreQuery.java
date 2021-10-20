package nocode.elasticsearch.plugin;

import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.SearchPlugin;

import java.util.Collections;
import java.util.List;

public class CustomPayloadScoreQuery extends Plugin implements SearchPlugin {
    @Override
    public List<QuerySpec<?>> getQueries() {
        return Collections.singletonList(
            new QuerySpec<>(CustomPayloadScoreQueryBuilder.NAME, CustomPayloadScoreQueryBuilder::new, CustomPayloadScoreQueryBuilder::fromXContent)
        );
    }
}