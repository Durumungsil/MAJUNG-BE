package durumungsil.majung.global.places;

import durumungsil.majung.global.exception.BusinessException;
import durumungsil.majung.global.exception.ErrorCode;
import durumungsil.majung.global.util.GeoUtils;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Component
public class RestGooglePlacesClient implements GooglePlacesClient {
    private final PlacesProperties properties;
    private final ObjectMapper mapper;
    private final RestClient client;

    public RestGooglePlacesClient(PlacesProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        client = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }

    @Override
    public Page searchText(Search search, Projection projection) {
        requireKey();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("textQuery", search.text());
        body.put("languageCode", "ko");
        body.put("pageSize", 20);
        if (search.includedType() != null) body.put("includedType", search.includedType());
        body.put("strictTypeFiltering", search.strictTypeFiltering());
        if (!search.priceLevels().isEmpty()) body.put("priceLevels", search.priceLevels());
        if (search.pageToken() != null && !search.pageToken().isBlank())
            body.put("pageToken", search.pageToken());
        if (search.bounds() != null) {
            Bounds bounds = search.bounds();
            body.put(
                    "locationRestriction",
                    Map.of(
                            "rectangle",
                            Map.of(
                                    "low",
                                            Map.of(
                                                    "latitude",
                                                    bounds.lowLatitude(),
                                                    "longitude",
                                                    bounds.lowLongitude()),
                                    "high",
                                            Map.of(
                                                    "latitude",
                                                    bounds.highLatitude(),
                                                    "longitude",
                                                    bounds.highLongitude()))));
        }
        try {
            String json =
                    client.post()
                            .uri("/v1/places:searchText")
                            .header("X-Goog-Api-Key", properties.apiKey())
                            .header("X-Goog-FieldMask", projection.mask())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(mapper.writeValueAsString(body))
                            .retrieve()
                            .body(String.class);
            JsonNode root = mapper.readTree(json);
            List<Place> places = new ArrayList<>();
            for (JsonNode item : root.path("places")) {
                String id = item.path("id").asString("");
                if (!validId(id)) continue;
                List<String> types = new ArrayList<>();
                for (JsonNode type : item.path("types"))
                    if (type.isString()) types.add(type.asString());
                places.add(
                        new Place(
                                id,
                                item.path("displayName").path("text").asString(null),
                                parseLocation(item.path("location")).orElse(null),
                                types));
            }
            return new Page(places, root.path("nextPageToken").asString(null));
        } catch (RuntimeException error) {
            // 공급자 응답·요청 URL·키·사용자 검색어는 로그나 공개 오류에 넣지 않는다.
            throw new BusinessException(ErrorCode.PLACES_UNAVAILABLE);
        }
    }

    @Override
    public Optional<Location> location(String placeId) {
        requireKey();
        if (!validId(placeId)) throw new BusinessException(ErrorCode.DESTINATION_NOT_FOUND);
        try {
            String json =
                    client.get()
                            .uri(
                                    uri ->
                                            uri.path("/v1/places/{id}")
                                                    .queryParam("languageCode", "ko")
                                                    .build(placeId))
                            .header("X-Goog-Api-Key", properties.apiKey())
                            .header("X-Goog-FieldMask", "location")
                            .retrieve()
                            .body(String.class);
            return parseLocation(mapper.readTree(json).path("location"));
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 404) return Optional.empty();
            throw new BusinessException(ErrorCode.PLACES_UNAVAILABLE);
        } catch (RuntimeException error) {
            throw new BusinessException(ErrorCode.PLACES_UNAVAILABLE);
        }
    }

    private Optional<Location> parseLocation(JsonNode location) {
        if (!location.path("latitude").isNumber() || !location.path("longitude").isNumber())
            return Optional.empty();
        double lat = location.path("latitude").asDouble(),
                lon = location.path("longitude").asDouble();
        GeoUtils.validate(lat, lon);
        return Optional.of(new Location(lat, lon));
    }

    public static boolean validId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,255}");
    }

    private void requireKey() {
        if (properties.apiKey().isBlank())
            throw new BusinessException(ErrorCode.PLACES_NOT_CONFIGURED);
    }
}
