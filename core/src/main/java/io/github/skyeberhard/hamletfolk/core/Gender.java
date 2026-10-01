package io.github.skyeberhard.hamletfolk.core;

/** A resident's gender (R4.14): decides which names they are drawn from and how they are referred to. */
public enum Gender {
    FEMALE("she", "her", "her", "woman", "girl"),
    MALE("he", "him", "his", "man", "boy");

    private final String subject;
    private final String object;
    private final String possessive;
    private final String adultNoun;
    private final String childNoun;

    Gender(String subject, String object, String possessive, String adultNoun, String childNoun) {
        this.subject = subject;
        this.object = object;
        this.possessive = possessive;
        this.adultNoun = adultNoun;
        this.childNoun = childNoun;
    }

    /** e.g. "she/her", for a list of residents. */
    public String pronouns() {
        return subject + "/" + object;
    }

    public String subject() {
        return subject;
    }

    public String object() {
        return object;
    }

    public String possessive() {
        return possessive;
    }

    /** "woman", "man" or "person"; "girl", "boy" or "child" for a child. */
    public String noun(boolean adult) {
        return adult ? adultNoun : childNoun;
    }
}
