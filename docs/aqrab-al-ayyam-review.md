# Fiqh Review Dossier: Fajr During Persistent Twilight (Anti-Transit Position)

Status: PARKED BEHIND FIQH REVIEW — not a default, not implemented as a
policy. The astronomical FACT it rests on is already shipped:
`sun.antiTransit` (v1.14, validated vs Muwaqqit <2s). This dossier exists
so a knowledgeable reader can rule on the POLICY without re-doing research.

Origin: Muwaqqit's "A misapplication of Aqrab al-Ayyām during persistent
twilight" (docs.muwaqqit.com/aqrab-al-ayyam, 11 Nov 2017) — fetched in full
2026-08-30. Attribution: their thesis and citations, summarized here with
our assessment; no code involved.

## The claim under review

During persistent twilight (sun never reaches the Fajr depression angle —
UK-style latitudes in summer), **Fajr = solar midnight (anti-transit)**, the
moment twilight stops decreasing and begins to spread/brighten. NOT the
angle-fraction rules (Aqrab al-Ayyām as commonly implemented, 1/7 night,
etc.), and NOT a fixed clock time.

This CONTRADICTS the adhan2 defaults our prayer module uses
(HighLatitudeRule: MIDDLE_OF_NIGHT / SEVENTH_OF_NIGHT / TWILIGHT_ANGLE).
Both cannot be "the" Fajr; a host must choose. That is why this is gated.

## Their sources (as cited in the article — NOT independently pulled by us)

| Authority | Statement cited | Source work |
|---|---|---|
| Ibn ʿĀbidīn | "We have never seen anyone who said Fajr is also qadāʾ… Fajr is the name of spreading light on the horizon… without any condition of darkness preceding it" | *Radd al-Muḥtār* |
| al-Taḥṭāwī | "Fajr occurs with the manifestation of whiteness spreading from the eastern side"; dawn-before-red-shafaq fasting discussion | *Ḥāshiyat al-Taḥṭāwī ʿalā al-Durr* |
| Ibn Ḥajar al-Haytamī | Nearest-place Fajr estimation is "very far-fetched" — local manifestation cannot be cancelled; ratio-based ʿIshāʾ keeps order | *Tuḥfat al-Muḥtāj* |
| Qāḍī Ḥusayn al-Murūzī | ʿIshāʾ at nearest-place ratio; nothing said about estimating Fajr | fatwā via *al-ʿAzīz* |
| al-Ramlī | no artificial Fajr except no-sun-cycle or life-preserving suḥūr cases | *Nihāyat al-Muḥtāj* |
| Badr al-Dīn al-ʿAynī | Bolghar: "as the sun sets from the west, dawn appears from the east" | *Ḥāshiyat al-Shalbī* |
| al-Zaylaʿī | ʿIshāʾ/ Witr dropped when no time occurs; no Fajr discussion | *Sharḥ al-Kanz* |
| Quṭb al-Dīn al-Shīrāzī | lat > 48.5°: dawn/shafaq intertwine; "east of meridian = dawn, west = shafaq" | *al-Tuḥfa al-Shāhiyya* |
| al-Barjandī | same east/west classification | *Ḥāshiya Sharḥ Jaghmīnī* |
| al-Marjānī | Bolghar practice survey: 4 ʿIshāʾ positions, Fajr only ever at solar midnight | *Nāẓūrat al-Ḥaqq* |
| Muftī Rashīd Ludhiānwī | "twilight before solar midnight is Maghrib's, after it is Fajr's" | *Aḥsan al-Fatāwā* |
| Astronomical anchor | "the sun moves from the western to the eastern half of the sky at lower transit (nadir)" | HM Nautical Almanac Office (Bell, Twilights) |

Qurʾānic framing they offer: 74:34/81:18 (dawn "brightens/breathes") vs
92:1/93:2 (night "envelops/is still") — spreading light cannot be night.

## Our assessment (for the reader, not a ruling)

**Strong points of the thesis:**
- Multiple classical authorities across schools are cited treating Fajr as
  OCCURRING (not estimated) during persistent twilight, with the spreading
  light definition.
- The symmetry argument (shafaq duration = subḥ duration, Ẓafar Aḥmad
  ʿUthmānī's *Īlāʾ al-Sunan* citation) is astronomically real — our own
  dawn/dusk pairs confirm it.
- The historical Bolghar (lat 55°) practice record is direct evidence of a
  Muslim community solving exactly this problem with solar midnight.
- The "no one extended qadāʾ to Fajr" claim from Ibn ʿĀbidīn is a strong
  negative-evidence citation.

**Weak points / open questions for the reader:**
1. **Anti-transit ≠ visible dawn by their own logic.** Solar midnight is
   when twilight INFLECTS (mathematically), but at 51–55° the pre-dawn sky
   barely differs across hours around inflection. "Spreading" becomes
   observational, not calculable — the same objection they raise against
   fixed 1:20am applies in reduced degree.
2. **Practical ʿamal divergence.** Major UK bodies (e.g. those criticized in
   the article) still teach Aqrab al-Ayyām/clock estimates; communities
   follow local committees — our sighted-calendar mode (v1.15) is the
   mechanism for "my community's actual practice", independent of which
   astronomical policy is "correct".
3. **adhan2 ecosystem.** Our prayer goldens are adhan2-locked; adopting this
   as anything but an opt-in policy would re-golden the entire matrix.
4. The article's Usūl stretch (Qurʾān verses as astronomical classification)
   is rhetoric-weighted; the fiqh core stands or falls on the fiqh citations.

**Engine consequence if approved:** an opt-in `HighLatitudeRule.ANTI_TRANSIT`
variant (never default) — Fajr := `sun.antiTransit` when the depression
angle is unreachable; goldens extended with a persistent-twilight site
(Central England ~52°, June–July window) validated against Muwaqqit's
published values. No other prayer times touched; ʿIshāʾ during persistent
twilight stays a SEPARATE question (their thesis: ratio-at-nearest-place /
qadāʾ — not bundled into this dossier).

## Decision requested from the knowledgeable reader

1. Is the anti-transit Fajr position (as sourced above) acceptable as an
   OPT-IN high-latitude policy for this engine — yes/no/needs-more-sources?
2. If yes: does ʿIshāʾ during persistent twilight get its own dossier
   (nearest-place-ratio per Shāfiʿī framing vs. qadāʾ per Ḥanafī framing)?
3. Are the article's citations acceptable as-carried, or must we pull the
   primary texts (Radd al-Muḥtār, Tuḥfa, Nihāya, Īlāʾ al-Sunan) first?
