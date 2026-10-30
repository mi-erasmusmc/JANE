"""Static option lists for the web UI (ported from Languages.txt / AllowedPublicationTypes.txt)."""

# (display name, value) pairs.
LANGUAGES: list[tuple[str, str]] = [
    ("English", "eng"),
    ("French", "fre"),
    ("German", "ger"),
    ("Italian", "ita"),
    ("Japanese", "jpn"),
    ("Russian", "rus"),
    ("Spanish", "spa"),
]

PUBLICATION_TYPES: list[str] = [
    "Case Reports",
    "Classical Article",
    "Clinical Trial",
    "Clinical Trial, Phase I",
    "Clinical Trial, Phase II",
    "Clinical Trial, Phase III",
    "Clinical Trial, Phase IV",
    "Comparative Study",
    "Controlled Clinical Trial",
    "Evaluation Studies",
    "In Vitro",
    "Journal Article",
    "Letter",
    "Meta-Analysis",
    "Multicenter Study",
    "Randomized Controlled Trial",
    "Review",
    "Twin Study",
    "Validation Studies",
]
