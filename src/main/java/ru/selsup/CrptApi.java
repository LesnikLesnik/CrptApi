package ru.selsup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private static final Logger logger = LoggerFactory.getLogger(CrptApi.class);

    private final RateLimiter rateLimiter;
    private final HttpClient httpClient;
    private final JsonUtil jsonUtil;

    /**
     * Конструктор класса CrptApi.
     *
     * @param timeUnit     Единица времени для ограничения запросов (секунда, минута и т.д.)
     * @param requestLimit Максимальное количество запросов в указанное время
     */
    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.rateLimiter = new RateLimiter(requestLimit, timeUnit);
        this.httpClient = HttpClient.newHttpClient();
        this.jsonUtil = new JsonUtil();
    }

    /**
     * Метод для создания документа.
     *
     * @param document Объект документа
     */
    public void createDocument(Document document) {
        try {
            // Получение разрешения перед выполнением запроса
            rateLimiter.acquire();

            // Сериализация документа в JSON
            String jsonBody = jsonUtil.serialize(document);

            // Отправка POST запроса
            ApiRequest apiRequest = new ApiRequest(httpClient);
            HttpResponse<String> response = apiRequest.sendPostRequest("https://ismp.crpt.ru/api/v3/lk/documents/create", jsonBody);

            // Обработка ответа
            if (response.statusCode() == 200) {
                logger.info("Успешно: {}", response.body());
            } else {
                logger.error("Ошибка: {}", response.body());
            }

        } catch (Exception e) {
            logger.error("Исключение при создании документа", e);
        } finally {
            // Освобождение разрешения после завершения запроса
            rateLimiter.release();
        }
    }

    /**
     * Внутренний класс для ограничения количества запросов.
     */
    private static class RateLimiter {
        private final Semaphore semaphore;
        private final ScheduledExecutorService scheduler;

        /**
         * Конструктор класса RateLimiter.
         *
         * @param requestLimit Максимальное количество запросов
         * @param timeUnit     Единица времени для ограничения запросов (секунда, минута и т.д.)
         */
        public RateLimiter(int requestLimit, TimeUnit timeUnit) {
            this.semaphore = new Semaphore(requestLimit);
            this.scheduler = Executors.newScheduledThreadPool(1);
            // Планирование задачи для сброса семафора
            scheduler.scheduleAtFixedRate(() -> {
                semaphore.drainPermits();
                semaphore.release(requestLimit);
            }, 0, 1, timeUnit);
        }

        /**
         * Метод для получения разрешения.
         */
        public void acquire() {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Поток прерван при получении семафора", e);
            }
        }

        /**
         * Метод для освобождения разрешения.
         */
        public void release() {
            semaphore.release();
        }
    }

    /**
     * Внутренний record для HTTP запросов.
     */
    private record ApiRequest(HttpClient httpClient) {
        /**
         * Метод для отправки POST запроса.
         *
         * @param url      URL для отправки запроса
         * @param jsonBody Тело запроса в формате JSON
         * @return Ответ HTTP запроса
         * @throws Exception Исключение при отправке запроса
         */
        public HttpResponse<String> sendPostRequest(String url, String jsonBody) throws Exception {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    /**
     * Внутренний класс для сериализации JSON.
     */
    private static class JsonUtil {
        private final ObjectMapper objectMapper;

        /**
         * Конструктор класса JsonUtil.
         */
        public JsonUtil() {
            this.objectMapper = new ObjectMapper();
        }

        /**
         * Метод для сериализации объекта в JSON.
         *
         * @param obj Объект для сериализации
         * @return JSON строка
         */
        public String serialize(Object obj) throws JsonProcessingException {
            return objectMapper.writeValueAsString(obj);
        }

        /**
         * Метод для десериализации JSON в объект.
         *
         * @param json  JSON строка
         * @param clazz Класс объекта
         * @return Объект указанного класса
         */
        public <T> T deserialize(String json, Class<T> clazz) throws JsonProcessingException {
            return objectMapper.readValue(json, clazz);
        }
    }

    /**
     * Внутренний record, представляющий документ.
     */
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
            String reg_number,
            List<Product> products
    ) {
    }

    /**
     * Внутренний record, представляющий продукт.
     */
    public record Product(
            String certificate_document,
            String certificate_document_date,
            String certificate_document_number,
            String owner_inn,
            String producer_inn,
            String production_date,
            String tnved_code,
            String uit_code,
            String uitu_code
    ) {
    }

}
