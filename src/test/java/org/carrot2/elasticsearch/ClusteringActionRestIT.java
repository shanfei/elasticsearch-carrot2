package org.carrot2.elasticsearch;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.assertj.core.api.Assertions;
import org.carrot2.core.LanguageCode;
import org.carrot2.elasticsearch.ClusteringAction.RestClusteringAction;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Test;

/**
 * REST API tests for {@link ClusteringAction}.
 */
public class ClusteringActionRestIT extends SampleIndexTestCase {
    XContentType type = randomFrom(XContentType.values());

    @Test
    public void testPostClusterByUrl() throws Exception {
        post("post_cluster_by_url.json");
    }
    
    @Test
    public void testPostMultipleFieldMapping() throws Exception {
        post("post_multiple_field_mapping.json");
    }
    
    @Test
    public void testPostWithHighlightedFields() throws Exception {
        post("post_with_highlighted_fields.json");
    }

    @Test
    public void testPostWithFields() throws Exception {
        post("post_with_fields.json");
    }

    @Test
    public void testPostWithSourceFields() throws Exception {
        post("post_with_source_fields.json");
    }

    protected void post(String queryJsonResource) throws Exception {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPost post = new HttpPost(restBaseUrl + "/" + RestClusteringAction.NAME + "?pretty=true");

            post.setEntity(new ByteArrayEntity(resourceAs(queryJsonResource, type)));
            HttpResponse response = httpClient.execute(post);

            Map<?,?> map = checkHttpResponseContainsClusters(response);

            List<?> clusterList = (List<?>) map.get("clusters");
            Assertions.assertThat(clusterList)
                .isNotNull()
                .isNotEmpty();

            Assertions.assertThat(clusterList.size())
                .isGreaterThan(5);
        }
    }

    @Test
    public void testGetClusteringRequest() throws Exception {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpGet get = new HttpGet(restBaseUrl + "/" + RestClusteringAction.NAME 
                    + "?pretty=true" 
                    // search-specific attrs
                    + "&q=data+mining"
                    + "&fields=url,title,content"
                    + "&size=100"
                    // clustering-specific attrs
                    + "&query_hint=data+mining"
                    + "&field_mapping_url=fields.url"
                    + "&field_mapping_content=fields.title,fields.content"
                    + "&algorithm=stc");
            HttpResponse response = httpClient.execute(get);

            Map<?,?> map = checkHttpResponseContainsClusters(response);

            List<?> clusterList = (List<?>) map.get("clusters");
            Assertions.assertThat(clusterList)
                .isNotNull()
                .isNotEmpty();

            Assertions.assertThat(clusterList.size())
                .isGreaterThan(5);
        }
    }
    
    @Test
    public void testRestApiPathParams() throws Exception {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPost post = new HttpPost(restBaseUrl 
                    + "/" + INDEX_NAME 
                    + "/empty/" 
                    + RestClusteringAction.NAME + "?pretty=true");
            post.setEntity(new ByteArrayEntity(resourceAs("post_with_fields.json", type)));
            HttpResponse response = httpClient.execute(post);
            Map<?,?> map = checkHttpResponseContainsClusters(response);

            List<?> clusterList = (List<?>) map.get("clusters");
            Assertions.assertThat(clusterList)
                .isNotNull()
                .isEmpty();
        }
    }    
    
    @Test
    public void testRestApiRuntimeAttributes() throws Exception {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPost post = new HttpPost(restBaseUrl + "/" + RestClusteringAction.NAME + "?pretty=true");
            post.setEntity(new ByteArrayEntity(resourceAs("post_runtime_attributes.json", type)));
            HttpResponse response = httpClient.execute(post);
            Map<?,?> map = checkHttpResponseContainsClusters(response);

            List<?> clusterList = (List<?>) map.get("clusters");
            Assertions.assertThat(clusterList)
                .isNotNull();
            Assertions.assertThat(clusterList)
                .hasSize(/* max. cluster size cap */ 5 + /* other topics */ 1);
        }
    }
    
    @Test
    public void testLanguageField() throws Exception {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPost post = new HttpPost(restBaseUrl + "/" + RestClusteringAction.NAME + "?pretty=true");
            post.setEntity(new ByteArrayEntity(resourceAs("post_language_field.json", type)));
            HttpResponse response = httpClient.execute(post);
            Map<?,?> map = checkHttpResponseContainsClusters(response);

            // Check top level clusters labels.
            Set<String> allLanguages = new HashSet<>();
            for (LanguageCode code : LanguageCode.values()) {
                allLanguages.add(code.toString());
            }

            List<?> clusterList = (List<?>) map.get("clusters");
            for (Object o : clusterList) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cluster = (Map<String, Object>) o; 
                allLanguages.remove(cluster.get("label"));
            }
            
            Assertions.assertThat(allLanguages.size())
                .describedAs("Expected a lot of languages to appear in top groups.")
                .isLessThan(LanguageCode.values().length / 2);            
        }
    }
    
    @Test
    public void testNonexistentFields() throws Exception {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPost post = new HttpPost(restBaseUrl + "/" + RestClusteringAction.NAME + "?pretty=true");
            post.setEntity(new ByteArrayEntity(resourceAs("post_nonexistent_fields.json", type)));
            HttpResponse response = httpClient.execute(post);
            Map<?,?> map = checkHttpResponseContainsClusters(response);

            List<?> clusterList = (List<?>) map.get("clusters");
            Assertions.assertThat(clusterList).isNotNull();
        }
    }

    @Test
    public void testNonexistentAlgorithmId() throws Exception {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPost post = new HttpPost(restBaseUrl + "/" + RestClusteringAction.NAME + "?pretty=true");
            post.setEntity(new ByteArrayEntity(resourceAs("post_nonexistent_algorithmId.json", type)));
            HttpResponse response = httpClient.execute(post);
            expectErrorResponseWithMessage(
                    response,
                    HttpStatus.SC_BAD_REQUEST,
                    "No such algorithm: _nonexistent_");
        }
    }    

    @Test
    public void testInvalidSearchQuery() throws Exception {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPost post = new HttpPost(restBaseUrl + "/" + RestClusteringAction.NAME + "?pretty=true");
            post.setEntity(new ByteArrayEntity(resourceAs("post_invalid_query.json", type)));
            HttpResponse response = httpClient.execute(post);
            expectErrorResponseWithMessage(
                    response, 
                    HttpStatus.SC_BAD_REQUEST, 
                    "query_parsing_exception");
        }
    }    

    @Test
    public void testPropagatingAlgorithmException() throws Exception {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPost post = new HttpPost(restBaseUrl + "/" + RestClusteringAction.NAME + "?pretty=true");
            post.setEntity(new ByteArrayEntity(resourceAs("post_invalid_attribute_value.json", type)));
            HttpResponse response = httpClient.execute(post);
            expectErrorResponseWithMessage(
                    response, 
                    HttpStatus.SC_INTERNAL_SERVER_ERROR, 
                    "Search results clustering error:");
        }
    }    
}
