package com.omar.wheel;

import java.util.TimeZone;

/** A word of the day for the card: just the word. Built in, so it works with no internet. */
final class Words {

    private static final String[] ALL = {
            "serendipity", "petrichor", "ephemeral", "luminous", "quintessential",
            "mellifluous", "sonorous", "ebullient", "halcyon", "laconic",
            "perspicacious", "loquacious", "sanguine", "equanimity", "ineffable",
            "zephyr", "panacea", "nebulous", "obfuscate", "plethora",
            "quixotic", "resilience", "sagacious", "tenacious", "ubiquitous",
            "verisimilitude", "wanderlust", "zenith", "aurora", "bucolic",
            "cacophony", "defenestration", "effervescent", "flabbergasted", "gregarious",
            "idyllic", "jubilant", "kaleidoscope", "labyrinth", "magnanimous",
            "nostalgia", "opulent", "palimpsest", "quagmire", "reverie",
            "soliloquy", "tintinnabulation", "umbra", "vivacious", "whimsical",
            "yonder", "zealous", "alacrity", "candor", "dulcet",
            "eloquent", "felicity", "gossamer", "haphazard", "incandescent",
            "juxtapose", "kindred", "lucid", "meridian", "nonchalant",
            "oblivion", "panorama", "quietude", "resplendent", "tranquil",
            "uncanny", "verve", "wistful", "zigzag", "aberration",
            "brouhaha", "cahoots", "doldrums", "eureka", "fiasco",
            "galvanize", "hullabaloo", "inkling", "jamboree", "kerfuffle",
            "lollygag", "malarkey", "nincompoop", "oodles", "persnickety",
            "rigmarole", "skedaddle", "thingamajig", "whippersnapper", "bamboozle",
            "catawampus", "dillydally", "flummox", "gobbledygook", "hodgepodge",
            "lackadaisical", "mollycoddle", "shenanigans", "zeitgeist", "cynosure",
            "denouement", "ethereal", "fortuitous", "illuminate", "luminary",
            "maverick", "nocturne", "ouroboros", "paradigm", "quasar",
            "rhapsody", "syzygy", "talisman", "utopia", "vortex",
            "wayfarer", "bravado", "crescendo", "eclectic", "finesse",
    };

    /** Today's word (changes at midnight, same one all day). */
    static String today(long now, TimeZone tz) {
        // Day count since 1970, so it walks through the whole list before repeating.
        long day = (now + tz.getOffset(now)) / 86400000L;
        return ALL[(int) (day % ALL.length)];
    }

    private Words() {}
}
