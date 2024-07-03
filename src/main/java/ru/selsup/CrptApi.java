package ru.selsup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private static final Logger logger = LoggerFactory.getLogger(CrptApi.class);

    private final RateLimiter rateLimiter;
    private final HttpClient httpClient;
    private final JsonUtil jsonUtil;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.rateLimiter = new RateLimiter(requestLimit, timeUnit);
        this.httpClient = HttpClient.newHttpClient();
        this.jsonUtil = new JsonUtil();
    }

    public void createDocument(Document document) {
        try {
            // Acquire a permit before proceeding
            rateLimiter.acquire();

            // Serialize the document to JSON
            String jsonBody = jsonUtil.serialize(document);

            // Send POST request
            ApiRequest apiRequest = new ApiRequest(httpClient);
            HttpResponse<String> response = apiRequest.sendPostRequest("https://ismp.crpt.ru/api/v3/lk/documents/create", jsonBody);

            // Handle the response
            if (response.statusCode() == 200) {
                logger.info("Success: {}", response.body());

            } else {
                logger.error("Error: {}", response.body());
            }

        } catch (Exception e) {
            logger.error("Exception while creating document", e);
        } finally {
            // Release the permit after the request is completed
            rateLimiter.release();
        }
    }

    // Inner class for Rate Limiting
    private static class RateLimiter {
        private final Semaphore semaphore;
        private final ScheduledExecutorService scheduler;

        public RateLimiter(int requestLimit, TimeUnit timeUnit) {
            this.semaphore = new Semaphore(requestLimit);
            this.scheduler = Executors.newScheduledThreadPool(1);
            // Schedule task to reset the semaphore
            scheduler.scheduleAtFixedRate(() -> {
                semaphore.drainPermits();
                semaphore.release(requestLimit);
            }, 0, 1, timeUnit);
        }

        public void acquire() {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Thread interrupted while acquiring semaphore", e);
            }
        }

        public void release() {
            semaphore.release();
        }
    }

    // Inner record for HTTP requests
    private record ApiRequest(HttpClient httpClient) {
        public HttpResponse<String> sendPostRequest(String url, String jsonBody) throws Exception {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    // Inner class for JSON serialization
    private static class JsonUtil {
        private final ObjectMapper objectMapper;

        public JsonUtil() {
            this.objectMapper = new ObjectMapper();
        }

        public String serialize(Object obj) throws JsonProcessingException {
            return objectMapper.writeValueAsString(obj);
        }

        public <T> T deserialize(String json, Class<T> clazz) throws JsonProcessingException {
            return objectMapper.readValue(json, clazz);
        }
    }

    // Record representing the Document
    public record Document(
            String doc_id,
            String doc_status,
            String doc_type,
            boolean importRequest,
            String owner_inn,
            String participant_inn,
            String producer_inn,
            String production_date,
            String production_type,
            String reg_date,
            String reg_number
    ) {}
}
