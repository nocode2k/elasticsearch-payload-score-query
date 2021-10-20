package nocode.elasticsearch.plugin;
import org.apache.lucene.queries.payloads.PayloadDecoder;
import org.apache.lucene.queries.payloads.PayloadFunction;
import org.apache.lucene.queries.payloads.PayloadScoreQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanQuery;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ParseField;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.*;

import java.io.IOException;
import java.util.Objects;

public class CustomPayloadScoreQueryBuilder extends AbstractQueryBuilder<CustomPayloadScoreQueryBuilder> {

    public static final String NAME = "payload_score";

    private static final ParseField QUERY_FIELD = new ParseField("query");
    private static final ParseField FUNC_FIELD = new ParseField("func");
    private static final ParseField CALC_FIELD = new ParseField("calc");
    private static final ParseField INCLUDE_SPAN_SCORE_FIELD = new ParseField("includeSpanScore");

    private final QueryBuilder query;
    private final String func;
    private final String calc;
    private final boolean includeSpanScore;

    public CustomPayloadScoreQueryBuilder(QueryBuilder query, String func, String calc, boolean includeSpanScore) {
        this.query = requireValue(query, "[" + NAME + "] requires '" + QUERY_FIELD.getPreferredName() + "' field");
        this.func = func;
        this.calc = calc;
        this.includeSpanScore = includeSpanScore;
    }

    public CustomPayloadScoreQueryBuilder(StreamInput in) throws IOException {
        super(in);
        this.query = in.readNamedWriteable(QueryBuilder.class);
        this.func = in.readString();
        this.calc = in.readString();
        this.includeSpanScore = in.readBoolean();
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeNamedWriteable(query);
        out.writeString(this.func);
        out.writeString(this.calc);
        out.writeBoolean(this.includeSpanScore);
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        builder.field(QUERY_FIELD.getPreferredName());
        query.toXContent(builder, params);

        builder.field(FUNC_FIELD.getPreferredName(), this.func);
        builder.field(CALC_FIELD.getPreferredName(), this.calc);
        builder.field(INCLUDE_SPAN_SCORE_FIELD.getPreferredName(), this.includeSpanScore);
        printBoostAndQueryName(builder);
        builder.endObject();
    }

    public static QueryBuilder fromXContent(XContentParser parser) throws IOException {
        String currentFieldName = null;
        XContentParser.Token token;
        QueryBuilder iqb = null;

        String func = null;
        String calc = null;
        boolean includeSpanScore = false;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token == XContentParser.Token.START_OBJECT) {
                if (QUERY_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                    iqb = parseInnerQueryBuilder(parser);
                } else {
                    throw new ParsingException(parser.getTokenLocation(),
                        "[" + NAME + "] query does not support [" + currentFieldName + "]");
                }
            } else if (token.isValue()) {
                if (FUNC_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                    func = parser.text();
                } else if (CALC_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                    calc = parser.text();
                } else if (INCLUDE_SPAN_SCORE_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                    includeSpanScore = parser.booleanValue();
                } else {
                    throw new ParsingException(parser.getTokenLocation(),
                        "[" + NAME + "] query does not support [" + currentFieldName + "]");
                }
            }
        }
        return new CustomPayloadScoreQueryBuilder(iqb, func, calc, includeSpanScore);
    }

    @Override
    protected Query doToQuery(SearchExecutionContext context) throws IOException {
        // query  parse
        SpanQuery spanQuery = null;
        try {
            spanQuery = (SpanQuery) query.toQuery(context);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }

        if (spanQuery == null) {
            throw new IllegalArgumentException("SpanQuery is null");
        }

        PayloadFunction payloadFunction = CustomPayloadUtils.getPayloadFunction(this.func);
        if (payloadFunction == null) {
            throw new IllegalArgumentException("Unknown payload function: " + func);
        }
        PayloadDecoder payloadDecoder = CustomPayloadUtils.getPayloadDecoder("float");

        return new PayloadScoreQuery(spanQuery, payloadFunction, payloadDecoder, this.includeSpanScore);
    }

    @Override
    protected boolean doEquals(CustomPayloadScoreQueryBuilder that) {
        return Objects.equals(query, that.query)
            && Objects.equals(func, that.func)
            && Objects.equals(calc, that.calc)
            && Objects.equals(includeSpanScore, that.includeSpanScore);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(query, func, calc, includeSpanScore);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public boolean isFragment() {
        return false;
    }
}
