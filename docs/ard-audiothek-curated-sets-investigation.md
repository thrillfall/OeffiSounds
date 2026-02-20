# ARD Audiothek Curated Sets Investigation

## Approach

This investigation aimed to identify curated content sets in the ARD Audiothek GraphQL API beyond the "what's new" and "what's popular" sections already implemented in AntennaPod.

### Methodology

1. **Examined existing implementation** - Analyzed current AudiothekSection classes to understand API usage patterns
2. **API endpoint analysis** - Investigated the `/homescreen` GraphQL endpoint to discover available curated sets
3. **Content enumeration** - Queried the API to retrieve current content for each identified curated set
4. **Documentation** - Tracked findings and progress throughout the investigation

## Current Implementation Analysis

AntennaPod currently implements several ARD Audiothek sections:

### Existing Sections
- **AudiothekSection** - Main section with featured and most played content
- **AudiothekLiveSection** - Live content section  
- **AudiothekStageSection** - Stage content section
- **AudiothekChartsSection** - Charts/popular content section
- **AudiothekFeaturedSection** - Featured content section
- **AudiothekHotSection** - Hot/trending content section

### API Usage
All sections use the `https://api.ardaudiothek.de/homescreen` endpoint and parse different embedded content types from the response.

## Findings

### Available Curated Sets in `/homescreen` Endpoint

The ARD Audiothek homescreen API returns **4 main curated sets**:

#### 1. `mt:featuredProgramSets` - "Einfach mehr wissen" (Featured Content)
- **Purpose**: Editorial selection of noteworthy programs and podcasts
- **Current Content Count**: 7 items
- **Content Type**: Program sets (podcasts/shows)
- **Status**: ✅ **Already implemented** in AntennaPod

**Current Content (as of Feb 19, 2026)**:
1. "10 Dinge, die du über die Liebe wissen musst" - Relationship advice and topics
2. "10 wichtige Fragen des Lebens" - Life's big questions and philosophy  
3. "Aus der Geschichte lernen: Von Weimar bis USA" - Historical lessons and current events
4. "10 Hacks, die dein Leben verbessern" - Life improvement tips
5. "Die Ernährungs-Docs – eure Lieblingsfolgen" - Nutrition and health
6. "Die 10 mächtigsten Frauen der Geschichte" - Historical female leaders
7. "10 Menschen, die uns inspirieren" - Inspirational figures

#### 2. `mt:mostPlayed` - "Podcasts-Charts" (Popular Content)
- **Purpose**: Most played/trending episodes and shows
- **Current Content Count**: 18 items
- **Content Type**: Individual episodes from various shows
- **Status**: ✅ **Already implemented** in AntennaPod

**Current Content Highlights**:
- "Holzschlange sei wachsam" (Kalk & Welk)
- "Das vierte Skalpell" (Kein Mucks! Krimi-Podcast)
- "Macht, Missbrauch, Milliarden" (nah dran)
- Various crime podcasts, political commentary, and thriller content

#### 3. `mt:stageItems` - "Stage" 
- **Purpose**: Featured/stage content (likely premium or highlighted content)
- **Current Content Count**: 0 items (empty)
- **Status**: ⚠️ **Implemented but empty** - may be dynamic/time-based

#### 4. `mt:items` - "LIVE: Bundesliga am Sonntag"
- **Purpose**: Live content and events
- **Current Content Count**: 0 items (empty)  
- **Status**: ⚠️ **Implemented but empty** - appears to be event-specific

### Key Observations

1. **Limited Active Sets**: Only 2 out of 4 curated sets currently contain content
2. **Dynamic Nature**: The empty sets (`mt:stageItems`, `mt:items`) suggest time-sensitive or event-based content
3. **Content Focus**: Active sets emphasize educational content (featured) and popular entertainment (charts)
4. **No Additional Curated Sets**: The `/homescreen` endpoint appears to contain all available curated content for the main discovery interface

## Content Analysis

### Featured Program Sets Theme Analysis
The "Einfach mehr wissen" section focuses on:
- **Educational content**: Life advice, history, nutrition
- **Self-improvement**: Life hacks, inspirational stories  
- **Current affairs**: Historical parallels to modern events
- **German-language content**: All titles in German

### Most Played Content Theme Analysis
The "Podcasts-Charts" section emphasizes:
- **True crime**: Multiple crime podcasts and thrillers
- **Political commentary**: Shows like "Amerika, wir müssen reden!"
- **Comedy/Satire**: "Kalk & Welk", "extra 3"
- **Current events**: Timely political and social topics

## Technical Implementation Notes

### API Structure
```json
{
  "_embedded": {
    "mt:featuredProgramSets": {
      "title": "Einfach mehr wissen",
      "_embedded": {
        "mt:programSets": [...]
      }
    },
    "mt:mostPlayed": {
      "title": "Podcasts-Charts", 
      "_embedded": {
        "mt:items": [...]
      }
    },
    "mt:stageItems": {...},
    "mt:items": {...}
  }
}
```

### Content Types
- **Program Sets**: Complete shows/podcasts with metadata
- **Items**: Individual episodes with embedded program set information

## Conclusion

### Summary of Available Curated Sets

| Set Name | API Key | Title | Content Count | Status |
|----------|---------|-------|---------------|---------|
| Featured Programs | `mt:featuredProgramSets` | "Einfach mehr wissen" | 7 items | ✅ Implemented |
| Popular Charts | `mt:mostPlayed` | "Podcasts-Charts" | 18 items | ✅ Implemented |
| Stage Content | `mt:stageItems` | "Stage" | 0 items | ⚠️ Empty |
| Live Events | `mt:items` | "LIVE: Bundesliga am Sonntag" | 0 items | ⚠️ Empty |

### Additional Discovery: Category-Based Curated Content

**Found "Heute wichtig" section in "Politik & Hintergrund" category!**

#### Category: "Politik & Hintergrund" (ID: 51850530)
**API Endpoint**: `https://api.ardaudiothek.de/editorialcategories/51850530`

This category contains **11 curated sections**:

| Section | API Key | Type | Content Count | Status |
|---------|---------|------|---------------|---------|
| **Heute wichtig** | `mt:items` | featured_item | 0 items | ⚠️ Empty (time-sensitive) |
| Top Podcasts | `mt:items` | featured_programset | 10 items | ✅ Available |
| Neueste Episoden | `mt:items` | newest_episodes | 0 items | ⚠️ Empty |
| Neue Politik-Podcasts | - | - | - | Available |
| Newsletter Promo | - | - | - | Available |
| Immer gut informiert | - | - | - | Available |
| Aus den ARD-Auslandsstudios | - | - | - | Available |
| Mehr spannende Themen | - | - | - | Available |
| Podcast-Charts: Politik | `mt:mostPlayed` | most_played | 0 items | ⚠️ Empty |
| Alle Sendungen aus dieser Rubrik | - | - | - | Available |

#### "Top Podcasts" Content (Politics Category)
**Current Active Content**:
1. "11KM: der tagesschau-Podcast" (12200383)
2. "Amerika, wir müssen reden!" (82222746) 
3. "Was tun, Herr General? - Der Podcast zum Ukraine-Krieg" (10349279)
4. "Streitkräfte und Strategien" (7852196)
5. "0630 - der News-Podcast" (79906662)
6. "Berlin Code - mit Linda Zervakis" (14053111)
7. "Dark Matters – Geheimnisse der Geheimdienste" (12449787)
8. "Deutschlandfunk - Der Tag" (46142064)
9. "Weltspiegel Podcast" (61593768)
10. "Die Entscheidung. Politik, die uns bis heute prägt" (57448438)

### Key Findings About "Heute wichtig"

- **Location**: Found as section #2 in "Politik & Hintergrund" category
- **Type**: `featured_item` (individual episodes/items)
- **Current Status**: **Empty** - likely time-sensitive daily content
- **Expected Content**: Important daily news/political stories
- **Pattern**: Similar to empty sets on homescreen, suggests dynamic/time-based content

### Expanded Assessment

**AntennaPod has access to most curated content, but category-specific sections like "Heute wichtig" are not currently implemented.** The investigation revealed:

1. **Category-based curation**: Beyond homescreen, there are category-specific curated sections
2. **"Heute wichtig" exists but is empty**: Likely daily/time-sensitive content that updates regularly
3. **Rich category content**: "Politik & Hintergrund" alone contains 11 curated sections
4. **Multiple content types**: Categories mix featured items, program sets, episodes, and charts

### Recommendations

1. **Implement category browsing**: Add functionality to explore editorial categories
2. **Monitor dynamic content**: "Heute wichtig" and similar sections may contain content at different times
3. **Category-specific sections**: Each category may have its own curated subsections
4. **Time-based content**: Some curated sets appear to be daily/weekly and may be empty at certain times

---

*Investigation completed February 19, 2026*  
*API endpoint: https://api.ardaudiothek.de/homescreen*
