package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.43: a project nobody can build does not hold up the rest of the village's building. */
class StuckProjectTest {
    private int next = 1;

    private Resident person(Occupation job) {
        return new Resident(new UUID(90, next++), "Test", "Person", Gender.FEMALE, new Traits(50, 50, 50, 80), job, true,
                0, null, null, Needs.initial());
    }

    /** Four people who cannot be spared: two farmers (food below twice the wanted level), the only toolsmith and the only miner. */
    private Settlement tightVillage() {
        Settlement s = new Settlement(new UUID(91, 91), "Tightham", "world", 0, 0, 0);
        s.addResident(person(Occupation.FARMER));
        s.addResident(person(Occupation.FARMER));
        s.addResident(person(Occupation.TOOLSMITH));
        s.addResident(person(Occupation.MINER));
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        s.ledger().add(ResourceType.FOOD, 70);
        return s;
    }

    private ConstructionProject queue(Settlement s, BuildingType type, long day) {
        VillagePlan.Lot lot = s.plan().lots().get(0);
        ConstructionProject project = new ConstructionProject(s.nextProjectId(), type, 1, 0, "plains", lot.rect().x(), 64,
                lot.rect().z(), lot.id(), day);
        s.addProject(project);
        return project;
    }

    @Test
    void aProjectNobodyCanBeSparedForIsGivenUpAfterTwentyDaysAndTheLotIsKept() {
        Settlement s = tightVillage();
        ConstructionProject project = queue(s, BuildingType.HOUSE, 1);
        Construction.staffBuilders(s, 1 + Construction.STUCK_DAYS - 1, false);
        assertEquals(ConstructionProject.Status.QUEUED, project.status(), "it waits");
        Construction.staffBuilders(s, 1 + Construction.STUCK_DAYS, false);
        assertEquals(ConstructionProject.Status.CANCELLED, project.status());
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("nobody could be spared")));
        assertEquals(VillagePlan.LotStatus.RESERVED, s.plan().lots().stream().filter(l -> l.id() == project.lotId())
                .findFirst().orElseThrow().status(), "the lot is not used up");
        assertTrue(s.openProject().isEmpty(), "so the village can decide something else");
    }

    @Test
    void aWorkGivenUpLikeThatIsNotAskedForAgainAtOnce() {
        Settlement s = tightVillage();
        ConstructionProject fence = queue(s, BuildingType.PALISADE, 1);
        Construction.staffBuilders(s, 1 + Construction.STUCK_DAYS, false);
        assertEquals(ConstructionProject.Status.CANCELLED, fence.status());
        s.recordIncident(5);
        assertTrue(Construction.worksSettled(s, BuildingType.PALISADE, 1 + Construction.STUCK_DAYS + 1), "settled for a while");
        assertFalse(Construction.worksSettled(s, BuildingType.PALISADE, 1 + Construction.STUCK_DAYS + Construction.WORKS_RETRY_DAYS),
                "and asked for again after the usual wait");
    }

    @Test
    void aSupplierWithFullStoresIsDraftedAsTheBuilder() {
        Settlement s = tightVillage();
        s.addResident(person(Occupation.LUMBERJACK));
        s.ledger().add(ResourceType.WOOD, SettlementSimulator.capacity(s, ResourceType.WOOD));
        s.conditions().put(SettlementSimulator.RESTING + "LUMBERJACK", 1L); // resting at the storage limit
        ConstructionProject project = queue(s, BuildingType.HOUSE, 1);
        Construction.staffBuilders(s, 1 + Construction.DRAFT_AFTER_DAYS, false);
        assertEquals(ConstructionProject.Status.ACTIVE, project.status());
        assertEquals(Occupation.BUILDER, s.resident(project.builder()).orElseThrow().occupation());
        assertEquals(0, s.residents().stream().filter(r -> r.occupation() == Occupation.LUMBERJACK).count());
    }

    @Test
    void aProjectThatHasBeenStartedIsNotGivenUpForWantOfABuilder() {
        Settlement s = tightVillage();
        ConstructionProject project = queue(s, BuildingType.HOUSE, 1);
        project.credit().put(ResourceType.WOOD, 100); // some of it is paid for
        Construction.staffBuilders(s, 1 + Construction.STUCK_DAYS + 5, false);
        assertEquals(ConstructionProject.Status.QUEUED, project.status());
    }

    @Test
    void aKindGivenUpIsNotProposedAgainForTwentyDaysAndOldGivenUpProjectsAreForgotten() {
        Settlement s = tightVillage();
        ConstructionProject project = queue(s, BuildingType.HOUSE, 1);
        Construction.staffBuilders(s, 1 + Construction.STUCK_DAYS, false);
        assertTrue(s.hasCondition(Construction.STUCK + "HOUSE"));
        assertEquals(ConstructionProject.Status.CANCELLED, project.status());
        Construction.staffBuilders(s, 1 + Construction.STUCK_DAYS + Settlement.CANCELLED_KEPT_DAYS + 1, false);
        assertTrue(s.projects().isEmpty(), "forgotten after a while, so the list does not grow for ever");
    }
}
