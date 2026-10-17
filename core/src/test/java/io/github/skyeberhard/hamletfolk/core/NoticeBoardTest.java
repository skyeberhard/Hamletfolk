package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.53: what the notice board says. */
class NoticeBoardTest {
    private Settlement village() {
        Settlement s = new Settlement(new UUID(200, 1), "Boardham", "world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            s.addResident(new Resident(new UUID(201, i), "T", "P" + i, Gender.FEMALE, new Traits(50, 50, 50, 80), Occupation.FARMER, true, 0, null, null,
                    Needs.initial()));
        }
        s.setLastSimulatedDay(40);
        s.housing().setChunk(0, 0, 6);
        return s;
    }

    @Test
    void theBookHasACoverWantedBuildingAndNewsPagesEachShort() {
        List<String> pages = NoticeBoard.pages(village());
        assertEquals(4, pages.size());
        assertTrue(pages.get(0).startsWith("Boardham\nNotice Board"), pages.get(0));
        assertTrue(pages.get(0).contains("Day 40") && pages.get(0).contains("4 people"), pages.get(0));
        assertTrue(pages.get(1).startsWith("WANTED"));
        assertTrue(pages.get(2).startsWith("BUILDING"));
        assertTrue(pages.get(3).startsWith("NEWS"));
        for (String page : pages) {
            assertTrue(page.length() <= NoticeBoard.PAGE_CHARS, page.length() + ": " + page);
        }
    }

    @Test
    void whatTheVillageAsksForIsListedWithWhatItPays() {
        Settlement s = village();
        assertTrue(NoticeBoard.pages(s).get(1).contains("Nothing, for now"));
        s.requestMap().put(ResourceType.WOOD, new Request(ResourceType.WOOD, 40, 10, 6, 0, 30));
        s.requestMap().put(ResourceType.STONE, new Request(ResourceType.STONE, 20, 0, 0, 0, 30)); // no reward held: thanks only
        String wanted = NoticeBoard.pages(s).get(1);
        assertTrue(wanted.contains("30 wood (10 of 40 given)"), wanted);
        assertTrue(wanted.contains("6 emeralds"), wanted);
        assertTrue(wanted.contains("20 stone"), wanted);
        assertTrue(wanted.contains("thanks only"), wanted);
        assertTrue(wanted.contains("/settlement donate"));
    }

    @Test
    void aFilledRequestIsNotListed() {
        Settlement s = village();
        s.requestMap().put(ResourceType.WOOD, new Request(ResourceType.WOOD, 40, 40, 6, 0, 30));
        assertTrue(NoticeBoard.pages(s).get(1).contains("Nothing, for now"));
    }

    @Test
    void theNewsIsTheLatestFirstWithoutDonationsAndCapped() {
        Settlement s = village();
        for (int i = 0; i < 12; i++) {
            s.record(10 + i, HistoryEvent.Kind.BIRTH, "A child was born number " + i + ".");
        }
        s.record(30, HistoryEvent.Kind.DONATION, "Someone gave something.");
        String news = NoticeBoard.pages(s).get(3);
        assertTrue(news.indexOf("number 11") < news.indexOf("number 10"), "newest first: " + news);
        assertFalse(news.contains("Someone gave"), news);
        assertTrue(news.chars().filter(c -> c == ':').count() <= NoticeBoard.NEWS + 1, news);
        assertTrue(NoticeBoard.pages(new Settlement(new UUID(200, 2), "Quiet", "world", 0, 0, 0)).get(3).contains("Quiet times"));
    }

    @Test
    void aProjectUnderWayIsSaid() {
        Settlement s = village();
        s.addProject(new ConstructionProject(s.nextProjectId(), BuildingType.SHOP, 1, 0, "plains", 0, 64, 0, -1, 38));
        String work = NoticeBoard.pages(s).get(2);
        assertTrue(work.contains("shop"), work);
    }

    @Test
    void anEmptyOrFreshVillageStillHasABook() {
        List<String> pages = NoticeBoard.pages(new Settlement(new UUID(200, 3), "Newham", "world", 0, 0, 5));
        assertEquals(4, pages.size());
        assertTrue(pages.get(0).contains("0 people"), pages.get(0));
    }
}
