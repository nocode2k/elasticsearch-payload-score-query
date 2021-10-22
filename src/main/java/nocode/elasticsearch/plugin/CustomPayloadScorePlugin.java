package nocode.elasticsearch.plugin;

import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.SearchPlugin;

import java.util.Collections;
import java.util.List;

public class CustomPayloadScorePlugin extends Plugin implements SearchPlugin {
    @Override
    public List<QuerySpec<?>> getQueries() {
        return Collections.singletonList(
            new QuerySpec<>(CustomPayloadScoreBuilder.NAME, CustomPayloadScoreBuilder::new, CustomPayloadScoreBuilder::fromXContent)
        );
    }
}