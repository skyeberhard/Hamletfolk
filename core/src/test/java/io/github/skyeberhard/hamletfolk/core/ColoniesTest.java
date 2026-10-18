package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R10.1: a full, well-fed village sends a party of whole households with a builder to found a daughter village. */
class ColoniesTest {
    private static int n;
    private final Colonies.Rules rules = Colonies.defaults();

    private Resident person(Occupation job, boolean adult, UUID parent, int built, int days) {
        Resident r = new Resident(new UUID(40, n++), "Test", "Folk" + n, Gender.MALE, new Traits(50, 50, 50, 50), job, adult, adult ? 10_000 : 20_000,
                parent, null, Needs.initial());
        if (built > 0) {
            r.setBuilt(built);
        }
        if (days > 0) {
            r.restoreXp(job, days);
        }
        return r;
    }

    private void wed(Resident a, Resident b) {
        a.marry(b.id(), 100);
        b.marry(a.id(), 100);
    }

    /** A village of 34: a builder's couple with two children, four farmer couples (one child each), two lumberjacks, a lone toolsmith, two guards, a merchant, jobless. */
    private Settlement parent() {
        Settlement s = new Settlement(new UUID(41, n++), "Motherton", "world", 0, 0, 0);
        Resident builder = person(Occupation.BUILDER, true, null, 8, 0);
        Resident wife = person(Occupation.UNEMPLOYED, true, null, 0, 0);
        wed(builder, wife);
        s.addResident(builder);
        s.addResident(wife);
        s.addResident(person(Occupation.UNEMPLOYED, false, builder.id(), 0, 0));
        s.addResident(person(Occupation.UNEMPLOYED, false, builder.id(), 0, 0));
        for (int i = 0; i < 4; i++) {
            Resident farmer = person(Occupation.FARMER, true, null, 0, 20);
            Resident spouse = person(Occupation.FARMER, true, null, 0, 20);
            wed(farmer, spouse);
            s.addResident(farmer);
            s.addResident(spouse);
            s.addResident(person(Occupation.UNEMPLOYED, false, farmer.id(), 0, 0));
        }
        s.addResident(person(Occupation.LUMBERJACK, true, null, 0, 20));
        s.addResident(person(Occupation.LUMBERJACK, true, null, 0, 20));
        s.addResident(person(Occupation.TOOLSMITH, true, null, 0, 20));
        s.addResident(person(Occupation.GUARD, true, null, 0, 0));
        s.addResident(person(Occupation.GUARD, true, null, 0, 0));
        s.addResident(person(Occupation.MERCHANT, true, null, 0, 0));
        for (int i = 0; i < 12; i++) {
            s.addResident(person(Occupation.UNEMPLOYED, true, null, i == 0 ? 3 : 0, 0)); // (one of them has finished buildings: the village keeps a builder)
        }
        s.housing().setChunk(0, 0, s.population()); // every bed taken
        s.setLastSimulatedDay(200);
        s.conditions().put(SettlementSimulator.TREASURY_LEGACY, 100_000L);
        s.ledger().add(Commodity.BREAD, 5000);
        s.ledger().add(ResourceType.WOOD, 5000);
        s.ledger().add(ResourceType.STONE, 5000);
        s.ledger().addTreasury(200);
        return s;
    }

    @Test
    void aFullWellFedVillageIsReadyAndSendsAPartyWithTheBuildersHouseholdFirst() {
        Settlement s = parent();
        Colonies.Report report = Colonies.report(s, 200, 3, rules);
        assertTrue(report.ready(), report.problems().toString());
        Colonies.Party party = report.party().orElseThrow();
        assertTrue(party.size() >= rules.minParty() && party.size() <= rules.maxParty(), "party of " + party.size());
        assertTrue(s.population() - party.size() >= rules.minLeft());
        assertTrue(party.households().get(0).contains("household") || !party.households().isEmpty());
        List<Resident> people = party.members().stream().map(id -> s.resident(id).orElseThrow()).toList();
        assertTrue(people.stream().anyMatch(r -> r.built() >= 8), "the builder goes");
        assertTrue(people.stream().noneMatch(r -> r.adult() && (r.occupation() == Occupation.GUARD || r.occupation() == Occupation.MERCHANT
                || r.occupation() == Occupation.TOOLSMITH)), "no guard, merchant or only toolsmith");
        for (Resident r : people) { // whole households: a spouse or a young child is never left behind
            if (r.spouse() != null) {
                assertTrue(party.members().contains(r.spouse()), "a couple goes together");
            }
            if (!r.adult()) {
                assertTrue(party.members().contains(r.parentA()), "a child goes with its parent");
            }
        }
        assertEquals(Colonies.FOOD_PER_HEAD * party.size(), report.cost().orElseThrow().food());
    }

    @Test
    void aVillageThatIsSmallRecentGrowingAttackedOrCappedIsNotReadyAndSaysWhy() {
        Settlement small = new Settlement(new UUID(41, n++), "Smallton", "world", 0, 0, 0);
        for (int i = 0; i < 12; i++) {
            small.addResident(person(Occupation.FARMER, true, null, 0, 0));
        }
        assertTrue(small.population() < rules.minPopulation());
        assertTrue(Colonies.report(small, 200, 3, rules).problems().stream().anyMatch(p -> p.contains("sends a party from")));

        Settlement roomy = parent();
        roomy.housing().setChunk(0, 0, roomy.population() + 10);
        assertTrue(Colonies.report(roomy, 200, 3, rules).problems().stream().anyMatch(p -> p.contains("room to grow")));
        roomy.conditions().put(Construction.OUTGROWN, 195L);
        assertTrue(Colonies.report(roomy, 200, 3, rules).ready(), "a plan that was outgrown lately counts as full");

        Settlement attacked = parent();
        attacked.recordIncident(190);
        assertTrue(Colonies.report(attacked, 200, 3, rules).problems().stream().anyMatch(p -> p.contains("attacked")));

        assertTrue(Colonies.report(parent(), 200, rules.maxVillagesInWorld(), rules).problems().stream().anyMatch(p -> p.contains("The world already has")));
    }

    @Test
    void theKitAndGiftMustBeSparedOrItWaits() {
        Settlement poor = parent();
        poor.ledger().take(ResourceType.FOOD, 5000);
        poor.ledger().add(Commodity.BREAD, 10);
        assertTrue(Colonies.report(poor, 200, 3, rules).problems().stream().anyMatch(p -> p.contains("larder")));
        Settlement broke = parent();
        broke.ledger().spendTreasury(200);
        assertTrue(Colonies.report(broke, 200, 3, rules).problems().stream().anyMatch(p -> p.contains("treasury")));
    }

    @Test
    void theParentKeepsABuilderAndNoChildTravelsAlone() {
        Settlement s = parent();
        // only the one household can build: the party would leave the village with nobody who can
        for (Resident r : s.residents()) {
            if (r.built() > 0 && r.occupation() != Occupation.BUILDER) {
                r.setBuilt(0);
            }
        }
        assertTrue(Colonies.chooseParty(s, 200, rules).isEmpty(), "the only builder stays");
        Resident other = s.residents().stream().filter(r -> r.adult() && r.occupation() == Occupation.UNEMPLOYED && r.spouse() == null).findFirst().orElseThrow();
        other.setBuilt(3); // a second builder-capable resident stays behind
        Colonies.Party party = Colonies.chooseParty(s, 200, rules).orElseThrow();
        assertFalse(party.members().contains(other.id()) && party.members().size() == 1);
        Settlement orphans = parent();
        Resident orphan = person(Occupation.UNEMPLOYED, false, null, 0, 0);
        orphans.addResident(orphan);
        assertFalse(Colonies.chooseParty(orphans, 200, rules).orElseThrow().members().contains(orphan.id()), "a child with no parent is not sent alone");
    }

    @Test
    void aCoupleThatLeavesTogetherWritesNoWidowedLinesInTheParentsHistory() {
        SettlementRegistry registry = new SettlementRegistry();
        Settlement mother = parent();
        registry.add(mother);
        Settlement daughter = new Settlement(new UUID(42, n++), "Newfold", "world", 400, 0, 200);
        registry.add(daughter);
        assertTrue(Colonies.settle(registry, mother, daughter, 200, 3, rules));
        assertTrue(mother.history().stream().noneMatch(e -> e.text().contains("has no spouse now")), mother.history().toString());
        assertTrue(daughter.history().stream().noneMatch(e -> e.text().contains("has no spouse now")));
    }

    @Test
    void aPartyIsNotPossibleWithoutABuilder() {
        Settlement s = parent();
        for (Resident r : s.residents()) {
            r.setBuilt(0);
            if (r.occupation() == Occupation.BUILDER) {
                r.setOccupation(Occupation.UNEMPLOYED);
            }
        }
        assertTrue(Colonies.chooseParty(s, 200, rules).isEmpty());
    }

    @Test
    void settlingMovesThePeopleTheKitAndTheTieAndKeepsMarriages() {
        SettlementRegistry registry = new SettlementRegistry();
        Settlement mother = parent();
        registry.add(mother);
        Settlement daughter = new Settlement(new UUID(42, n++), "Newfold", "world", 400, 0, 200);
        registry.add(daughter);
        Colonies.Party party = Colonies.chooseParty(mother, 200, rules).orElseThrow();
        int before = mother.population();
        int food = mother.ledger().get(ResourceType.FOOD);
        int gift = mother.ledger().treasury();

        assertTrue(Colonies.settle(registry, mother, daughter, 200, 3, rules));

        assertEquals(before - party.size(), mother.population());
        assertEquals(party.size(), daughter.population());
        for (UUID id : party.members()) {
            Resident r = daughter.resident(id).orElseThrow();
            assertTrue(daughter.hasCondition(Migration.MOVING + id), "marked for the Minecraft layer to bring over");
            if (r.parentA() != null) {
                assertTrue(registry.settlementOf(id).orElseThrow() == daughter);
            }
        }
        assertTrue(daughter.residents().stream().filter(r -> r.spouse() != null).count() >= 2, "couples are still married");
        for (Resident r : daughter.residents()) {
            if (r.spouse() != null) {
                assertEquals(r.id(), daughter.resident(r.spouse()).orElseThrow().spouse());
            }
        }
        assertEquals(food - Colonies.FOOD_PER_HEAD * party.size(), mother.ledger().get(ResourceType.FOOD));
        assertEquals(Colonies.FOOD_PER_HEAD * party.size(), daughter.ledger().get(ResourceType.FOOD));
        assertEquals(gift - rules.treasuryGift(), mother.ledger().treasury());
        assertEquals(rules.treasuryGift(), daughter.ledger().treasury());
        assertEquals(List.of(daughter.id()), Colonies.daughters(mother));
        assertEquals(mother.id(), Colonies.parent(daughter).orElseThrow());
        assertTrue(Colonies.kin(mother, daughter) && Colonies.kin(daughter, mother));
        assertTrue(mother.history().stream().anyMatch(e -> e.text().contains("set out from Motherton to found Newfold")));
        assertTrue(daughter.history().stream().anyMatch(e -> e.text().contains("Newfold was founded from Motherton")));
        assertTrue(Colonies.describe(daughter, registry).contains("founded from Motherton"));
        assertTrue(Colonies.describe(mother, registry).contains("founded Newfold"));

        Settlement again = new Settlement(new UUID(42, n++), "Otherfold", "world", -400, 0, 200);
        registry.add(again);
        assertFalse(Colonies.settle(registry, mother, again, 201, 4, rules), "one party in 90 days");
        assertEquals(0, again.population());
    }
}
