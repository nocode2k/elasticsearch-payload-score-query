### elasticsearch : payload-score-query Plugin



T-Shirt  상품 중에서 선택 옵션의 판매수량이 많거나 재고 수량이 많은 (혹은 적은) 상품을 검색 순위 (가중치) 계산에 포함하려면 어떻게 해야할까요?

Lucene이 제공하는 PayloadScoreQuery를 사용하면 Term의 차이를  구분할 수 있습니다. Lucene에서는 실제적으로 우리가 저장한 Payload 데이터를 "|"와 같은 문자 뒤의 숫자를 구분하여 tf에 곱한 다음에 가중치 계산을 하고 있습니다.

안타깝게도 Elasticsearch에서는 Delimited payload token filter는 제공하고 있지만 PayloadScoreQuery와 같이 가중치를 계산하고 있지는 않습니다.

Elasticsearch 공식 문서: https://www.elastic.co/guide/en/elasticsearch/reference/7.15/analysis-delimited-payload-tokenfilter.html#analysis-delimited-payload-tokenfilter


T-Shirt  상품의 검색 순위를 높여야 하는 요구사항을 만족하기 위해서 Elasticsearch에서 Plugin개발을 통해서 PayloadScoreQuery 기능을 적용하는 방법을 살펴보도록 하겠습니다. 


## 환경

- open jdk 11
- gradle 7.1
- elasticsearch 7.15.1



## Analyzer 추가：

payload_delimiter라는 이름으로 analyzer를 설정한 paylaod_score_query 예제 index를 생성：

```json
PUT paylaod_score_query
{
  "mappings": {
    "properties": {
      "color": {
        "type": "text",
        "term_vector": "with_positions_payloads",
        "analyzer": "payload_delimiter"
      }
    }
  },
  "settings": {
    "analysis": {
      "analyzer": {
        "payload_delimiter": {
          "tokenizer": "whitespace",
          "filter": [ "delimited_payload" ]
        }
      }
    }
  }
}
```



paylaod_score_query 예제 index에 3개의 테스트 문서를 색인합니다.

```json
POST paylaod_score_query/_doc/1
{
  "name" : "T-shirt S",
  "color" : "blue|1 green|2 yellow|3"
}

POST paylaod_score_query/_doc/2
{
  "name" : "T-shirt M",
  "color" : "blue|1 green|2 red|3"
}

POST paylaod_score_query/_doc/3
{
  "name" : "T-shirt XL",
  "color" : "blue|1 yellow|2"
}
```



문서들의 토큰이 base64-encoded된 payload인지 확인합니다. 

GET paylaod_score_query/_termvectors/1?fields=color

```json
{
  "_index" : "paylaod_score_query",
  "_type" : "_doc",
  "_id" : "1",
  "_version" : 2,
  "found" : true,
  "took" : 26,
  "term_vectors" : {
    "color" : {
      "field_statistics" : {
        "sum_doc_freq" : 11,
        "doc_count" : 4,
        "sum_ttf" : 11
      },
      "terms" : {
        "blue" : {
          "term_freq" : 1,
          "tokens" : [
            {
              "position" : 0,
              "payload" : "P4AAAA=="
            }
          ]
        },
        "green" : {
          "term_freq" : 1,
          "tokens" : [
            {
              "position" : 1,
              "payload" : "QAAAAA=="
            }
          ]
        },
        "yellow" : {
          "term_freq" : 1,
          "tokens" : [
            {
              "position" : 2,
              "payload" : "QEAAAA=="
            }
          ]
        }
      }
    }
  }
}
```



## Plugin을 사용하지 않은 Span Query 결과 확인：

payload_delimiter가 적용된 color 필드를 포함하여 span query를 실행합니다.

```json
GET paylaod_score_query/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "match": {
            "name": "t-shirt"
          }
        },
        {
          "span_or": {
            "clauses": [
              {
                "span_term": {
                  "color": "yellow"
                }
              }
            ]
          }
        }
      ]
    }
  }
}
```



아래의 실행 결과를 보면 Elasticsearch에서 payload score query를 지원하지 않기 때문에 color필드의 yellow|2 값을 가진 문서 _id 3의 가중치(score)가 yellow|3값을 가진 문서 _id 1보다 높은 것을 확인할 수 있습니다.

```json
{
  "took" : 845,
  "timed_out" : false,
  "_shards" : {
    "total" : 1,
    "successful" : 1,
    "skipped" : 0,
    "failed" : 0
  },
  "hits" : {
    "total" : {
      "value" : 2,
      "relation" : "eq"
    },
    "max_score" : 0.6121877,
    "hits" : [
      {
        "_index" : "paylaod_score_query",
        "_type" : "_doc",
        "_id" : "3",
        "_score" : 0.6121877,
        "_source" : {
          "name" : "T-shirt XL",
          "color" : "blue|1 yellow|2"
        }
      },
      {
        "_index" : "paylaod_score_query",
        "_type" : "_doc",
        "_id" : "1",
        "_score" : 0.5546068,
        "_source" : {
          "name" : "T-shirt S",
          "color" : "blue|1 green|2 yellow|3"
        }
      }
    ]
  }
}

```



------



**지금부터 payload데이터를 검색결과 가중치에 포함할 수 있도록 구현한 Elasticsearch Plugin의 Class와 주요 Method를 설명한 다음에 Plugin 설치 후 그 결과를 확인하겠습니다.**



## Lucene PayloadScoreQuery：

먼저 Lucene의 PayloadScoreQuery 구성 메소드를 살펴보면：

```java
  /**
   * Creates a new PayloadScoreQuery
   * @param wrappedQuery the query to wrap
   * @param function a PayloadFunction to use to modify the scores
   * @param decoder a PayloadDecoder to convert payloads into float values
   * @param includeSpanScore include both span score and payload score in the scoring algorithm
   */
  public PayloadScoreQuery(SpanQuery wrappedQuery, PayloadFunction function, PayloadDecoder decoder, boolean includeSpanScore) {
    this.wrappedQuery = Objects.requireNonNull(wrappedQuery);
    this.function = Objects.requireNonNull(function);
    this.decoder = Objects.requireNonNull(decoder);
    this.includeSpanScore = includeSpanScore;
  }
```



이 메소드는 4개의 파라미터가 필요합니다 ：

- SpanQuery wrappedQuery. 반드시 spanQuery이어야 합니다.
- PayloadFunction function. 여러개의 텀이 매칭되었을 경우, 가중치, max, min, sum을 정의합니다.
- PayloadDecoder decoder. float 값으로 변환합니다. int or float type이어야 합니다.
- boolean includeSpanScore.  저장되어 있는 score를 사용할지 여부 입니다.



## CustomPayloadScoreQueryPlugin

다음과 같이 CustomPayloadScoreQueryPlugin 클래스에 CustomPayloadScoreQueryBuilder를 생성하는 코드를 추가합니다.

```java
public class CustomPayloadScoreQueryPlugin extends Plugin implements SearchPlugin {
    @Override
    public List<QuerySpec<?>> getQueries() {
        return Collections.singletonList(
            new QuerySpec<>(CustomPayloadScoreQueryBuilder.NAME, CustomPayloadScoreQueryBuilder::new, CustomPayloadScoreQueryBuilder::fromXContent)
        );
    }
}
```



## CustomPayloadScoreQueryBuilder

### fromXContent 메소드의 구현 ：

```js
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
    return new PayloadScoreQueryBuilder(iqb, func, calc, includeSpanScore);
}
```



### doToQuery 메소드의  PayloadScoreQuery 구조：

```js
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
```



## Build source code



```
$ gradle clean build
```



## Install plugin



```
$ cd $ES_HOME
$ ./bin/elasticsearch-plugin install file:///$PROJECT/build/distributions/payload-score-0.1.zip
```



## RUN Elasticsearch

```
$ cd $ES_HOME
$ ./bin/elasticsearch
```



## Sample API 실행

customize한 plugin의 payload_score api를 사용하여 span query를 실행합니다.

```json
GET /paylaod_score_query/_search
{
  "explain": false, 
  "query": {
    "bool": {
      "must": [
        {
          "match": {
            "name": "t-shirt"
          }
        },
        {
          "payload_score": {
            "func": "sum",
            "calc": "sum",
            "includeSpanScore": "false",
            "query": {
              "span_or": {
                "clauses": [
                  {
                    "span_term": {
                      "color": "yellow"
                    }
                  }
                ]
              }
            }
          }
        }
      ]
    }
  }
}
```



아래의 API 응답결과를 확인해보면 일반적인 Span Query를 실행한 결과와 다르게 yellow|3 이 포함된 문서 _id 1의 가중치(score)가 적용된 것을 확인할 수 있습니다.

```json
{
  "took" : 14,
  "timed_out" : false,
  "_shards" : {
    "total" : 1,
    "successful" : 1,
    "skipped" : 0,
    "failed" : 0
  },
  "hits" : {
    "total" : {
      "value" : 2,
      "relation" : "eq"
    },
    "max_score" : 3.210721,
    "hits" : [
      {
        "_index" : "paylaod_score_query",
        "_type" : "_doc",
        "_id" : "1",
        "_score" : 3.210721,
        "_source" : {
          "name" : "T-shirt S",
          "color" : "blue|1 green|2 yellow|3"
        }
      },
      {
        "_index" : "paylaod_score_query",
        "_type" : "_doc",
        "_id" : "3",
        "_score" : 2.210721,
        "_source" : {
          "name" : "T-shirt XL",
          "color" : "blue|1 yellow|2"
        }
      }
    ]
  }
}

```




