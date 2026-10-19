package durumungsil.majung.domain.adventure;

import durumungsil.majung.domain.group.Group;
import durumungsil.majung.domain.group.GroupMemberRepository;
import durumungsil.majung.domain.preference.Preference;
import durumungsil.majung.domain.preference.PreferenceRepository;
import durumungsil.majung.domain.store.Store;
import durumungsil.majung.domain.store.StoreRepository;
import durumungsil.majung.domain.store.Type;
import durumungsil.majung.domain.trip.*;
import durumungsil.majung.global.api.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class AdventureDestinationSelectorTests {
    private final StoreRepository stores = mock(StoreRepository.class);
    private final PreferenceRepository preferences = mock(PreferenceRepository.class);
    private final TripConstraintRepository constraints = mock(TripConstraintRepository.class);
    private final GroupMemberRepository members = mock(GroupMemberRepository.class);
    private final AdventureDestinationSelector selector = new AdventureDestinationSelector(
            stores, preferences, constraints, members, JsonMapper.builder().build());
    private Trip trip;

    @BeforeEach
    void setup() {
        Group group = Group.builder().build();
        ReflectionTestUtils.setField(group, "id", 1L);
        LocalDate date = LocalDate.of(2026, 10, 19);
        trip = Trip.builder().group(group).region("BUSAN").travelStartDate(date).travelEndDate(date).build();
        ReflectionTestUtils.setField(trip, "id", 2L);
        when(members.countByGroupId(1L)).thenReturn(1L);
        when(constraints.findById(2L)).thenReturn(Optional.empty());
    }

    @Test
    void filtersByLowestMealBudgetAndReturnsDistinctDestinations() {
        when(members.countByGroupId(1L)).thenReturn(2L);
        when(preferences.findCurrentMemberPreferences(2L, 1L)).thenReturn(List.of(
                Preference.builder().budgetMax(60_000).build(), Preference.builder().budgetMax(30_000).build()));
        Store affordable = store(Type.FOOD, 10_000);
        Store unknownPrice = store(Type.FOOD, null);
        when(stores.findInRegions(anyList())).thenReturn(List.of(affordable, unknownPrice, store(Type.FOOD, 10_001)));
        List<Store> selected = selector.select(trip, 2);
        assertEquals(2, selected.size());
        assertTrue(selected.containsAll(List.of(affordable, unknownPrice)));
        verify(stores).findInRegions(List.of("BUSAN", "부산", "부산광역시"));
        assertThrows(ApiException.class, () -> selector.select(trip, 3));
    }

    @Test
    void combinesAllergiesAndCategoryExclusionsWithoutGuessingFoodSafety() {
        when(preferences.findCurrentMemberPreferences(2L, 1L)).thenReturn(List.of(
                Preference.builder().allergies("[\"PEANUT\"]").build()));
        when(constraints.findById(2L)).thenReturn(Optional.of(TripConstraint.builder()
                .trip(trip).excludedCategories("[\"RETAIL\"]").build()));
        Store safe = store(Type.ART_SPORTS, null);
        when(stores.findInRegions(anyList())).thenReturn(List.of(safe, store(Type.FOOD, null), store(Type.RETAIL, null)));
        assertEquals(List.of(safe), selector.select(trip, 1));
        assertThrows(ApiException.class, () -> selector.select(trip, 2));
    }

    @Test
    void rejectsMissingPreferencesAndUnclassifiableConditions() {
        when(preferences.findCurrentMemberPreferences(2L, 1L)).thenReturn(List.of());
        assertThrows(ApiException.class, () -> selector.select(trip, 1));
        when(preferences.findCurrentMemberPreferences(2L, 1L)).thenReturn(List.of(Preference.builder().build()));
        when(constraints.findById(2L)).thenReturn(Optional.of(TripConstraint.builder()
                .excludedCategories("[\"EXTREME_HEIGHT\"]").build()));
        assertThrows(ApiException.class, () -> selector.select(trip, 1));
        when(constraints.findById(2L)).thenReturn(Optional.empty());
        when(preferences.findCurrentMemberPreferences(2L, 1L)).thenReturn(List.of(
                Preference.builder().allergies("invalid json").build()));
        assertThrows(ApiException.class, () -> selector.select(trip, 1));
    }

    private Store store(Type type, Integer price) {
        return Store.builder().destinationType(type).latitude(35.0).longitude(129.0).pricePerPerson(price).build();
    }
}
