package durumungsil.majung;

import com.sun.net.httpserver.HttpServer;
import durumungsil.majung.global.places.*;
import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.domain.place.PlaceTypeCatalog;
import durumungsil.majung.domain.preference.entity.TravelCategory;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class GooglePlacesClientTests {
    private HttpServer server;
    private final AtomicReference<String> mask = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private RestGooglePlacesClient client;
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            mask.set(exchange.getRequestHeaders().getFirst("X-Goog-FieldMask"));
            path.set(exchange.getRequestURI().toString());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String json = exchange.getRequestMethod().equals("GET") ? "{\"location\":{\"latitude\":35.1,\"longitude\":129.1}}"
                    : "{\"places\":[{\"id\":\"ChIJtest\",\"displayName\":{\"text\":\"테스트 카페\"},\"location\":{\"latitude\":35.1,\"longitude\":129.1},\"types\":[\"cafe\"]}],\"nextPageToken\":\"next\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        client = new RestGooglePlacesClient(new PlacesProperties("test-key", "http://127.0.0.1:"+server.getAddress().getPort(),
                Duration.ofSeconds(2), "test-secret"), JsonMapper.builder().build());
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void searchUsesProFieldsAndPassesPageAndRectangleWithoutEnterpriseFields() {
        var search = new GooglePlacesClient.Search("부산 카페", "cafe", true,
                new GooglePlacesClient.Bounds(35,129,36,130), List.of("PRICE_LEVEL_INEXPENSIVE"), "page2");
        var page = client.searchText(search, GooglePlacesClient.Projection.RECOMMENDATION);
        assertThat(page.places()).hasSize(1); assertThat(page.nextPageToken()).isEqualTo("next");
        assertThat(mask.get()).isEqualTo("places.id,places.displayName,places.location,places.types,nextPageToken");
        assertThat(body.get()).contains("\"languageCode\":\"ko\"", "\"pageSize\":20", "\"pageToken\":\"page2\"", "\"rectangle\"", "\"strictTypeFiltering\":true");
        assertThat(mask.get()).doesNotContain("rating", "priceLevel", "photos", "formattedAddress");
        assertThat(path.get()).doesNotContain("test-key");
    }
    @Test void rouletteUsesIdsOnlyAndDetailsOnlyRequestsLocation() {
        client.searchText(new GooglePlacesClient.Search("부산 관광명소", "tourist_attraction", false, null,List.of(),null), GooglePlacesClient.Projection.IDS_ONLY);
        assertThat(mask.get()).isEqualTo("places.id,nextPageToken");
        assertThat(client.location("ChIJtest")).contains(new GooglePlacesClient.Location(35.1,129.1));
        assertThat(mask.get()).isEqualTo("location");
        assertThat(path.get()).isEqualTo("/v1/places/ChIJtest?languageCode=ko");
        assertThatThrownBy(()->client.location("../key")).isInstanceOf(BusinessException.class);
    }
    @Test void festivalIsIgnoredAndSpecificCafeClassificationWins() {
        assertThat(PlaceTypeCatalog.types(TravelCategory.FESTIVAL)).isEmpty();
        assertThat(PlaceTypeCatalog.categoryOf(List.of("restaurant","cafe"),TravelCategory.FOOD)).isEqualTo(TravelCategory.CAFE);
        assertThat(PlaceTypeCatalog.allows(TravelCategory.CAFE,"seafood_restaurant")).isFalse();
    }
}
