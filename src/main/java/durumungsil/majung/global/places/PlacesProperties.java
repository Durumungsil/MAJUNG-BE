package durumungsil.majung.global.places;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("google.places")
public record PlacesProperties(
        String apiKey, String baseUrl, Duration timeout, String cursorSecret) {
    public PlacesProperties {
        apiKey = apiKey == null ? "" : apiKey.strip();
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://places.googleapis.com" : baseUrl;
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        if (timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(Duration.ofSeconds(30)) > 0)
            throw new IllegalArgumentException("Places timeout은 0초 초과 30초 이하여야 합니다.");
        cursorSecret = cursorSecret == null ? "" : cursorSecret;
    }
}
