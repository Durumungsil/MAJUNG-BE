package durumungsil.majung.domain.adventure.service;

import durumungsil.majung.domain.group.entity.Group;
import durumungsil.majung.domain.group.repository.GroupMemberRepository;
import durumungsil.majung.domain.preference.entity.Preference;
import durumungsil.majung.domain.preference.repository.PreferenceRepository;
import durumungsil.majung.domain.store.entity.Store;
import durumungsil.majung.domain.store.repository.StoreRepository;
import durumungsil.majung.domain.store.entity.Type;
import durumungsil.majung.domain.trip.entity.*;
import durumungsil.majung.domain.trip.repository.*;
import durumungsil.majung.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
        when(preferences.findAllOfCurrentMembers(2L, 1L)).thenReturn(List.of(
                Preference.builder().budgetMax(60_000).build(), Preference.builder().budgetMax(30_000).build()));
        Store affordable = store(Type.FOOD, 10_000);
        Store unknownPrice = store(Type.FOOD, null);
        when(stores.findInRegionsAndTypes(anyList(), anyCollection(), any(Pageable.class))).thenReturn(List.of(affordable, unknownPrice, store(Type.FOOD, 10_001)));
        List<Store> selected = selector.select(trip, 2);
        assertEquals(2, selected.size());
        assertTrue(selected.containsAll(List.of(affordable, unknownPrice)));
        verify(stores).findInRegionsAndTypes(eq(List.of("BUSAN", "부산", "부산광역시")),
                eq(EnumSet.of(Type.RETAIL, Type.FOOD, Type.ART_SPORTS)), eq(StoreRepository.regionCandidateLimit()));
        assertThrows(BusinessException.class, () -> selector.select(trip, 3));
    }

    @Test
    void combinesAllergiesAndCategoryExclusionsWithoutGuessingFoodSafety() {
        when(preferences.findAllOfCurrentMembers(2L, 1L)).thenReturn(List.of(
                Preference.builder().allergies("[\"PEANUT\"]").build()));
        when(constraints.findById(2L)).thenReturn(Optional.of(TripConstraint.builder()
                .trip(trip).excludedCategories("[\"RETAIL\"]").build()));
        Store safe = store(Type.ART_SPORTS, null);
        when(stores.findInRegionsAndTypes(anyList(), anyCollection(), any(Pageable.class))).thenReturn(List.of(safe, store(Type.FOOD, null), store(Type.RETAIL, null)));
        assertEquals(List.of(safe), selector.select(trip, 1));
        Set<Type> expected = EnumSet.allOf(Type.class);
        expected.removeAll(Set.of(Type.LODGING, Type.SCIENCE_TECHNOLOGY, Type.RETAIL));
        verify(stores, atLeastOnce()).findInRegionsAndTypes(anyList(), eq(expected), any(Pageable.class));
        assertThrows(BusinessException.class, () -> selector.select(trip, 2));
    }

    @Test
    void rejectsMissingPreferencesAndUnclassifiableConditions() {
        when(preferences.findAllOfCurrentMembers(2L, 1L)).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> selector.select(trip, 1));
        when(preferences.findAllOfCurrentMembers(2L, 1L)).thenReturn(List.of(Preference.builder().build()));
        when(constraints.findById(2L)).thenReturn(Optional.of(TripConstraint.builder()
                .excludedCategories("[\"EXTREME_HEIGHT\"]").build()));
        assertThrows(BusinessException.class, () -> selector.select(trip, 1));
        when(constraints.findById(2L)).thenReturn(Optional.empty());
        when(preferences.findAllOfCurrentMembers(2L, 1L)).thenReturn(List.of(
                Preference.builder().allergies("invalid json").build()));
        assertThrows(BusinessException.class, () -> selector.select(trip, 1));
    }

    @Test
    void keepsWalkingLegsShortAndExcludesLodgingAndTechnicalOffices() {
        when(preferences.findAllOfCurrentMembers(2L, 1L)).thenReturn(List.of(Preference.builder().build()));
        var near = List.of(store(Type.ART_SPORTS, null), store(Type.RETAIL, null));
        var far = Store.builder().destinationType(Type.ART_SPORTS).latitude(37.0).longitude(129.0).build();
        when(stores.findInRegionsAndTypes(anyList(), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(near.get(0), near.get(1), far, store(Type.LODGING, null), store(Type.SCIENCE_TECHNOLOGY, null)));
        assertEquals(2, selector.select(trip, 2).size());
        assertThrows(BusinessException.class, () -> selector.select(trip, 3));
    }

    @Test
    void mapsAllergiesToIndustrySubclassAndRejectsMissingClassification() {
        var allergy = List.of(durumungsil.majung.domain.preference.profile.Allergy.SHELLFISH);
        assertFalse(StoreAllergyFilter.allows(store(Type.FOOD, null), allergy));
        var seafood = Store.builder().destinationType(Type.FOOD).latitude(35.0).longitude(129.0)
                .industrySmallCode("I20112").industrySmallName("해산물 구이/찜 전문점").build();
        var cafe = Store.builder().destinationType(Type.FOOD).latitude(35.0).longitude(129.0)
                .industrySmallCode("I21201").industrySmallName("커피 전문점").build();
        assertFalse(StoreAllergyFilter.allows(seafood, allergy));
        assertTrue(StoreAllergyFilter.allows(cafe, allergy));
        assertFalse(StoreAllergyFilter.allows(cafe, List.of(durumungsil.majung.domain.preference.profile.Allergy.MILK)));
    }

    private Store store(Type type, Integer price) {
        return Store.builder().destinationType(type).latitude(35.0).longitude(129.0).pricePerPerson(price).build();
    }
}
