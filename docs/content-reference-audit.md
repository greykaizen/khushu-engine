# Content Reference Audit — QuranApp · SunnahApp · khushu-quran-data

Input for the `khushu-data-api` design. All targets verified GPLv3.
Audit date: 2026-08. Evidence cited as exact paths.

---

## 1. QuranApp (`reference/QuranApp`, upstream via com.alfaazplus lineage)

### 1.1 Databases (`app/src/main/assets/db/`)

**quranapp.db** — the core relational model (~24 tables):

| Table | Purpose |
|---|---|
| `surahs` | surah_no PK, ayah_count, revelation_order/type, rukus_count |
| `ayahs` | ayah_id PK, surah_no, ayah_no, juz/hizb/rub/manzil numbers (+ more cols) — 6236 rows ✓ |
| `mushafs` / `mushaf_map` | script registry: qpc(604p), indopak_13(847p), indopak_15(610p), indopak_16(548p), kfqpc_v1(604p) — page counts + line counts per mushaf |
| `ayah_words` | word-level data (feeds page layout + WBW) |
| `navigation_ranges` | juz/hizb/rub navigation |
| `arabic_search*` | FTS5 virtual-table cluster for full-text Arabic search |
| `surah_localizations`, `surah_search_aliases(+fts)` | localized names + search aliases |
| `similar_verses`, `mutashabihat_phrases(+phrase_ayah)` | similar-verse cross-references |

**page_info.db** — mushaf LINE LAYOUT (the atlas companion):
`pages(script, page_number, line_number, line_type, is_centered, first_word_id, last_word_id, surah_number)`
— 37,914 rows; `line_type` ∈ {surah_name, ayah, …}; first/last word-id bound each
line to `ayah_words`. This table IS the page-rendering spec.

**topics.db** — `topics / topic_ayahs / topic_localizations / relationships`
(curated topic → ayah collections).

### 1.2 Atlas bundles (`assets/atlas/uthmani/6x.zip`)
Glyph-texture atlases (PNG sheets `{script}_tex{n}.png`) + glyph metrics so
mushaf pages composite pixel-perfectly without font shaping. Rendering tech →
host layer (see ar-transplant decision); **glyph metrics + `page_info.db` +
`ayah_words` together form the pure layout spec** (future `mushaf-layout` candidate,
license-clear under GPLv3).

### 1.3 Translations (`prebuilt_translations/<lang_id>_<name>_<version>/`)
Per-pack: `manifest.json` (book, author, displayName, langCode, version,
downloadPath) + flat JSON of ayah→translation. Versioned by directory suffix.
Languages seen: en (Saheeh Intl, Clear Quran), ur (Junagarhi), de, bn, ckb …

### 1.4 Other assets
- `verses/type0|type1|type2/recommended/` — curated verse-set JSONs keyed by
  topic id (`map.json`: topic → "surah:ayah-range" lists)
- `chapter_info/*.json` — surah background info (empty in this copy)
- `tafsir/` — here only HTML/CSS/JS shells (3 files); real tafsir content
  ships via khushu-quran-data `inventory/tafsirs` instead
- `science/topics+img` — topical content
- `common/` — shared bits

## 2. SunnahApp (`reference/SunnahApp`, upstream com.alfaazplus.sunnah)

**Format verdict: modern and excellent** — versioned protobuf corpus bundles.

- Schema at `app/src/main/proto/sunnahapp/deliverable/v1/deliverable.proto`
- One gzipped bundle per collection:
  `prebuilt-hadiths/{bukhari,muslim,malik,nasai,ibnmajah,abudawud,tirmidhi,riyadussalihin,forty}/corpus.pb.gz`
  (bukhari: 4.5 MB gz → 22.2 MB raw)
- `CorpusBundle{schema_version, corpus_id, content_version}` containing:
  collections + collection_translations, books + book_translations, chapters +
  chapter_translations, hadiths (id/urn/collection/book/chapter/number),
  hadith_contents (**blocks_json** per lang — structured content blocks),
  hadith_references, hadith_related (cross-corpus edges, explicitly nullable
  targets), hadith_grades (per-lang grading labels), hadith_narrators
- `scholars_info.db` — single `scholars` table (narrator bios)

Wire-version discipline ("reject unknown values") + content_version field =
clean upgrade story. **Adopt this shape as the canonical Sunnah format.**

## 3. khushu-quran-data (greykaizen/khushu-quran-data, master branch)

Two-tier layout:

```
assets/          asma_ul_husna(+audio) · dua_dhikr/(dhikr-dua/articles/related-articles)
                 adhan · islamic_calendar(islamic_events.json)
inventory/       912 MB distribution tier:
  translations/  <lang>/<pack>… + available_translations_info.json
  tafsirs/       + available_tafsirs_info.json
  wbw/           word-by-word packs + available_wbw_info(_v2).json
  recitations/   audio
  hadiths/       {collection}.db (SQLite downloads — Osprey's offline path)
  quran_scripts/ fonts/scripts · fonts/ · chapters/ · other/ · versions/
```

Consumption pattern (proven in Osprey):
online = `raw.githubusercontent.com/greykaizen/khushu-quran-data/master/<path>`
offline = downloaded `.db`/pack files into app storage.
Catalog manifests (`available_*_info.json`) drive discovery UIs.

## 4. Recommendation matrix

| Artifact | Verdict |
|---|---|
| quranapp.db core (surahs/ayahs/navigation/mushafs) | **keep schema concepts**, re-export canonical |
| arabic_search FTS cluster | modernize: rebuild over chosen schema |
| page_info.db + ayah_words | keep — mushaf-layout spec (post-transplant module input) |
| topics.db | keep, re-export |
| atlas textures | host-layer (unchanged) |
| translations manifest+JSON | adopt format as canonical pack spec |
| verses/type*/topic JSONs | migrate into topics.db-style store |
| Sunnah corpus.pb.gz | **adopt wholesale** (protobuf + schema_version) |
| scholars_info.db | fold into corpus or keep side-db |
| khushu-quran-data inventory catalogs | keep; extend with checksums/sizes |

## 5. Design implications for khushu-data-api

1. One `ContentRepository` interface, two source impls: `RemoteSource(raw.githubusercontent)`
   and `LocalSource(downloaded files)` — identical query surface.
2. Typed models mirror the best formats found: Sunnah protobuf entities; Quran
   entities from quranapp.db tables; translation packs as manifest+JSON.
3. Download-state tracking (available/downloaded/version) mirrors the
   `available_*_info.json` catalog pattern.
4. Search: FTS concept retained; engine-side interface, implementation detail
   per storage backend.
5. No rendering, no network inside the API's *domain* types — network/file IO
   live behind injected transport interfaces so JVM tests run offline.
