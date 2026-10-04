package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R2.5: a merchant needs a free storefront to work, one merchant per storefront. */
class StorefrontTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator();
    private int next = 1;

    private Resident person(Occupation job) {
        return new Resident(new UUID(6, next++), "Test", "Person", Gender.MALE, new Traits(50, 50, 50, 50),
                job, true, 10_000, null, null, Needs.initial());
    }

    /** A village of {@code people} jobless residents with plenty of everything a merchant could sell. */
    private Settlement plentiful(int people) {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < people; i++) {
            s.addResident(person(Occupation.UNEMPLOYED));
        }
        s.ledger().add(ResourceType.FOOD, 40 * people);
        s.ledger().add(ResourceType.WOOD, 9 * people);
        s.ledger().add(ResourceType.STONE, 9 * people);
        s.ledger().add(ResourceType.GOODS, 9 * people);
        return s;
    }

    private static void shop(Settlement s, int at) {
        s.registerBuilding(new Building(BuildingType.SHOP, at, 64, 0, 0, "test"));
    }

    private static long merchants(Settlement s) {
        return s.residents().stream().filter(r -> r.occupation() == Occupation.MERCHANT).count();
    }

    @Test
    void aShopSignIsAShopWithOnePlace() {
        assertEquals(Optional.of(BuildingType.SHOP), BuildingType.fromSign("[Shop]"));
        assertEquals(Optional.of(BuildingType.SHOP), BuildingType.fromSign("[ shop ]"));
        assertEquals(Optional.of(Occupation.MERCHANT), BuildingType.SHOP.job());
        assertEquals(1, BuildingType.SHOP.places());
        assertEquals(BuildingType.WORKERS_PER_BUILDING, BuildingType.FARM.places());
    }

    @Test
    void withoutAStorefrontNobodyBecomesAMerchant() {
        Settlement s = plentiful(31);
        simulator.simulateTo(s, 10, 100);
        assertEquals(0, merchants(s));
        assertEquals(0, s.ledger().treasury(), "no merchant selling");
    }

    @Test
    void oneMerchantPerStorefront() {
        Settlement one = plentiful(31); // a village this size could use three
        shop(one, 0);
        simulator.simulateTo(one, 10, 100);
        assertEquals(1, merchants(one));

        Settlement two = plentiful(31);
        shop(two, 0);
        shop(two, 1);
        simulator.simulateTo(two, 10, 100);
        assertEquals(2, merchants(two), "building a second storefront lets a second merchant work");
        assertTrue(two.ledger().treasury() > 0);
    }

    @Test
    void aMerchantWithoutAStorefrontIsReleasedAndBreakingTheSignDoesTheSame() {
        Settlement s = plentiful(4);
        shop(s, 0);
        Resident merchant = person(Occupation.MERCHANT);
        s.addResident(merchant);
        simulator.simulateTo(s, 3, 100);
        assertEquals(Occupation.MERCHANT, merchant.occupation());

        s.removeBuilding(0, 64, 0, 3); // the sign was broken
        simulator.simulateTo(s, 4, 100);
        assertTrue(merchant.occupation() != Occupation.MERCHANT, "released: " + merchant.occupation());
    }

    @Test
    void moreMerchantsThanStorefrontsReleasesTheExtraOnesOneADay() {
        Settlement s = plentiful(4);
        shop(s, 0);
        Resident a = person(Occupation.MERCHANT);
        Resident b = person(Occupation.MERCHANT);
        Resident c = person(Occupation.MERCHANT);
        s.addResident(a);
        s.addResident(b);
        s.addResident(c);
        simulator.simulateTo(s, 1, 100);
        assertEquals(2, merchants(s));
        simulator.simulateTo(s, 2, 100);
        assertEquals(1, merchants(s));
        simulator.simulateTo(s, 3, 100);
        assertEquals(1, merchants(s), "one storefront, one merchant");
    }

    @Test
    void aStorefrontSurvivesASave() {
        Settlement s = plentiful(4);
        shop(s, 7);
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(1, loaded.buildingCount(BuildingType.SHOP));
    }
}
