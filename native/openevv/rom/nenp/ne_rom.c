/* Nepali (ne-NP, 0x00120000) Devanagari & mixed-script romanizer for ECI.
 *
 * Implements Standard Spoken Nepali phonology (Khatiwada 2009 JIPA, Acharya
 * 1991, Pokharel):
 *   - 6 oral monophthongs (/ʌ a i u e o/ with merged इ/ई -> /i/ and उ/ऊ -> /u/)
 *     + 2 true rising diphthongs (ऐ /ʌi̯/ and औ /ʌu̯/) + full phonemic nasal
 *     counterparts (ँ).
 *   - Dental-alveolar affricates [ts, tsʰ, dz, dzʱ] for च छ ज झ, bilabial /b/
 *     default for व ([w] post-consonantally and in lexical glides), merged
 *     alveolar sibilant /s/ for श ष स, intervocalic retroflex flaps [ɽ, ɽʱ],
 *     word-initial क्ष -> [tsʰ] vs medial [ktsʰ], ज्ञ -> [ɡj] + nasalized V,
 *     and murmured sonorants (बाह्र, तेह्र, सोह्र, ह्रस्व).
 *   - Nepali morphophonemic /ʌ/ retention vs deletion: medial open-syllable
 *     /ʌ/ retained; agglutinative case clitics (-मा, -को/-का/-की, -ले, -लाई,
 *     -बाट, -देखि, -सम्म, -सँग, -हरू) trigger stem-final /ʌ/ apocope; word-final
 *     /ʌ/ retained in monosyllables, -छ verbs, negatives (-ऐन/-एन), -एर
 *     participles, verb infinitives (-न), clusters, and -य stems, and deleted
 *     in citation nouns.
 *   - Root-initial primary stress, complete Nepali number system (0..99 +
 *     सय/हजार/लाख/करोड/अर्ब/खर्ब), currencies, and abbreviations.
 */

#include "indic_rom.h"

typedef struct {
    const char *word;
    const char *spr;
} NeLexEntry;

static const NeLexEntry ne_lex[] = {
    { "अझ", "1\xf8.\xd0\xf8" },
    { "अठार", "1\xf8.\xf1" "a.r\xf8" },
    { "अब", "1\xf8.b\xf8" },
    { "आउनुहोस्", "1a.u.nu.hos" },
    { "आवाज", "1a.waD" },
    { "आज", "1a.D\xf8" },
    { "उहाँ", "1u.h\xe3" },
    { "एघार", "1e.Ya.r\xf8" },
    { "कति", "1k\xf8.ti" },
    { "काठमाडौँ", "1ka\xf1.ma.j\xd6" },
    { "किन", "1ki.n\xf8" },
    { "कुनै", "1ku.n\xe4" },
    { "गएन", "1g\xf8.e.n\xf8" },
    { "गर्न", "1g\xf8r.n\xf8" },
    { "खान", "1Ka.n\xf8" },
    { "छ", "1\xf0\xf8" },
    { "छैन", "1\xf0\xe4.n\xf8" },
    { "छु", "1\xf0u" },
    { "चौध", "1T\xf6.V\xf8" },
    { "जब", "1D\xf8.b\xf8" },
    { "जान", "1Da.n\xf8" },
    { "त", "1t\xf8" },
    { "तब", "1t\xf8.b\xf8" },
    { "तपाईं", "1t\xf8.pa.\xef" },
    { "तपाईंको", "1t\xf8.pa.\xef.ko" },
    { "तपाईंलाई", "1t\xf8.pa.\xef.la.i" },
    { "तर", "1t\xf8.r\xf8" },
    { "तल", "1t\xf8.l\xf8" },
    { "तेह्र", "1te.Qr\xf8" },
    { "दुःख", "1duk.K\xf8" },
    { "न", "1n\xf8" },
    { "नमस्कार", "1n\xf8.m\xf8s.kar" },
    { "नमस्ते", "1n\xf8.m\xf8s.te" },
    { "पन्ध्र", "1p\xf8n.Vr\xf8" },
    { "पर", "1p\xf8.r\xf8" },
    { "पढ्न", "1p\xf8\xfd.n\xf8" },
    { "बस्न", "1b\xf8s.n\xf8" },
    { "बाहिर", "1ba.hi.r\xf8" },
    { "बाह्र", "1ba.Qr\xf8" },
    { "बोल्न", "1bol.n\xf8" },
    { "भएन", "1B\xf8.e.n\xf8" },
    { "भन्न", "1B\xf8n.n\xf8" },
    { "भित्र", "1Bi.tr\xf8" },
    { "म", "1m\xf8" },
    { "मलाई", "1m\xf8.la.i" },
    { "मेरो", "1me.ro" },
    { "यहाँ", "1y\xf8.h\xe3" },
    { "र", "1r\xf8" },
    { "राजा", "1ra.Da" },
    { "लेख्न", "1leK.n\xf8" },
    { "वर", "1b\xf8.r\xf8" },
    { "वहाँ", "1w\xf8.h\xe3" },
    { "विराटनगर", "1bi.raM.n\xf8.g\xf8r" },
    { "सगरमाथा", "1s\xf8.g\xf8r.ma.Fa" },
    { "सत्र", "1s\xf8.tr\xf8" },
    { "सँग", "1s\xe2.g\xf8" },
    { "सुन्न", "1sun.n\xf8" },
    { "सोह्र", "1so.Qr\xf8" },
    { "हावा", "1ha.wa" },
    { "हुन", "1hu.n\xf8" },
    { "हुन्छ", "1hun.\xf0\xf8" },
    { "हुँदैन", "1h\xfc.d\xe4.n\xf8" },
    { "हेर्न", "1her.n\xf8" },
    { "हो", "1ho" },
    { "होइन", "1ho.i.n\xf8" },
    /* ---- high-frequency Nepali function words and everyday nouns.
       Nepali phonology: च छ ज झ are dental-alveolar affricates
       (T \xf0 D \xd0), श ष स merge to /s/, व is [b] initially and
       [w] in clusters, इ/ई and उ/ऊ merge, and lexical stress is
       root-initial. ---- */
    { "यो", "1jo" },
    { "त्यो", "1tjo" },
    { "के", "1ke" },
    { "को", "1ko" },
    { "कहाँ", "1k\xf8.h\xe3" },
    { "घर", "1Y\xf8r" },
    { "पानी", "1pa.ni" },
    { "दूध", "1duV" },
    { "भात", "1Bat" },
    { "दाल", "1dal" },
    { "तरकारी", "1t\xf8r.ka.ri" },
    { "राम्रो", "1r\xf8m.ro" },
    { "नराम्रो", "1n\xf8.ram.ro" },
    { "ठूलो", "1\xf1u.lo" },
    { "सानो", "1sa.no" },
    { "पछि", "1p\xf8.\xf0i" },
    { "अगाडि", "1\xf8Ga.Wi" },
    { "मान्छे", "1man.\xf0" "e" },
    { "मानिस", "1ma.nis" },
    { "देश", "1des" },
    { "नेपाली", "1ne.pa.li" },
    { "धन्यवाद", "1V\xf8.ny\xf8.bad" },
    { "स्वागत", "1swa.G\xf8t" },
    { "भयो", "1B\xf8.jo" },
    { "थियो", "1Fi.jo" },
    { "भएको", "1B\xf8.e.ko" },
    { "गर्छु", "1g\xf8r.\xf0u" },
    { "गर्छन्", "1g\xf8r.\xf0\xf8n" },
    { "हुने", "1hu.ne" },
    { "हाम्रो", "1ham.ro" },
    { "तिम्रो", "1tim.ro" },
    { "बुबा", "1bu.ba" },
    { "आमा", "1a.ma" },
    { "दाजु", "1da.Du" },
    { "भाइ", "1Bai" },
    { "बहिनी", "1B\xf8.hi.ni" },
    { "छोरा", "1\xf0o.ra" },
    { "छोरी", "1\xf0o.ri" },
    { "पुस्तक", "1pus.t\xf8k" },
    { "कलम", "1k\xf8.l\xf8m" },
    { "कागज", "1kaG.\xf8" "D" },
    { "शुभकामना", "1suV.k\xf8.ma.na" },
    { "बिदाई", "1bi.da.i" },
    { "खाना", "1Ka.na" },
    { "पैसा", "1p\xe4.sa" },
    { "बाटो", "1ba.Mo" },
    { "गाउँ", "1G\xd6" },
    { "शहर", "1s\xf8.h\xf8r" },
    { "पहाड", "1p\xf8.ha.W" },
    { "हिमाल", "1hi.mal" },
    { "नदी", "1n\xf8.di" },
    { "ताल", "1tal" },
    { "जंगल", "1D\xf8G.\xf8l" },
    { "फूल", "1Pul" },
    { "रुख", "1ruK" },
    { "पात", "1pat" },
    { "आकाश", "1a.kas" },
    { "धर्ती", "1V\xf8r.ti" },
    { "महिला", "1m\xf8.hi.la" },
    { "पुरुष", "1pu.rus" },
    { "केटा", "1ke.Ma" },
    { "केटी", "1ke.ti" },
    { "स्कुल", "1skul" },
    { "काम", "1kam" },
    { "नयाँ", "1n\xf8.j\xe3" },
    { "पुरानो", "1pu.ra.no" },
    { "सुरक्षा", "1su.r\xf8k.sa" },
    { "सरकार", "1s\xf8r.kar" },
    { "राष्ट्र", "1ras.Mr" },
    { "इतिहास", "1i.ti.has" },
    { "विज्ञान", "1biG.gan" },
    { "सूचना", "1suT.na" },
    { "प्रविधि", "1pr\xf8.bi.Vi" },
    { "इन्टरनेट", "1in.M\xf8r.ne.M" },
    { "मोबाइल", "1mo.bail" },
    { "फोन", "1Pon" },
    { "समाचार", "1s\xf8.ma.Tar" },
    { "रेडियो", "1re.Wi.jo" },
    { "टेलिभिजन", "1Meli.bi.D\xf8n" },
    { "अस्पताल", "1\xf8s.pa.tal" },
    { "औषधि", "1\xf6s\xf8.Vi" },
    { "शिक्षा", "1sik.sa" },
    { "विकास", "1bi.kas" },
    { "भाषा", "1Ba.sa" },
    { "शब्द", "1s\xf8" "b.d\xf8" },
    { "अर्थ", "1\xf8rF" },
    { "नियम", "1ni.j\xf8m" },
    { "सत्य", "1s\xf8t.j\xf8" },
    { "शान्ति", "1san.ti" },
    { "युद्ध", "1yud.V\xf8" },
    { "दया", "1d\xf8.ja" },
    { "माया", "1ma.ja" },
    { "सपना", "1s\xf8p.na" },
    { "यात्रा", "1ya.tra" },
    { "समय", "1s\xf8m\xf8j" },
    { "आज्ञा", "1aG.ja" },
    { "बजार", "1b.Dar" },
    { "पसल", "1p.sal" },
    { "चोक", "1Tok" }
};

static const char *ne_lex_lookup(const uint32_t *cps, size_t n)
{
    IndicBuf tmp = { NULL, 0, 0 };
    size_t i;
    const char *hit = NULL;

    for (i = 0; i < n; i++)
        indic_utf8_encode(&tmp, cps[i]);
    if (!tmp.data)
        return NULL;
    for (i = 0; i < sizeof ne_lex / sizeof ne_lex[0]; i++) {
        if (strcmp(tmp.data, ne_lex[i].word) == 0) {
            hit = ne_lex[i].spr;
            break;
        }
    }
    ibuf_free(&tmp);
    return hit;
}

static char ne_map_vowel(uint32_t vcp)
{
    switch (vcp) {
    case 0x0905: return PH_SCHWA;   /* अ /ʌ/ */
    case 0x0906: return PH_AA;      /* आ /a/ */
    case 0x0907:
    case 0x0908: return PH_II;      /* इ/ई merged /i/ */
    case 0x0909:
    case 0x090a: return PH_UU;      /* उ/ऊ merged /u/ */
    case 0x090d: return PH_AE;      /* ऍ */
    case 0x090f: return PH_EE;      /* ए /e/ */
    case 0x0910: return PH_DIP_AI;  /* ऐ /ʌi̯/ */
    case 0x0911: return PH_OPEN_O;  /* ऑ */
    case 0x0913: return PH_OO;      /* ओ /o/ */
    case 0x0914: return PH_DIP_AU;  /* औ /ʌu̯/ */
    default:     return PH_SCHWA;
    }
}

static void ne_emit_cons(char *phs, int *nph, int max_ph, uint32_t cp,
                         int after_cons, int post_vocalic, uint32_t next_cp)
{
    if (*nph >= max_ph - 3)
        return;
    switch (cp) {
    case 0x0915: phs[(*nph)++] = PH_K; break;
    case 0x0916: phs[(*nph)++] = PH_KH; break;
    case 0x0917: phs[(*nph)++] = PH_G; break;
    case 0x0918: phs[(*nph)++] = PH_GH; break;
    case 0x0919: phs[(*nph)++] = PH_NG; break;

    /* Nepali dental-alveolar affricates [ts, tsʰ, dz, dzʱ] */
    case 0x091a: phs[(*nph)++] = PH_TS; break;
    case 0x091b: phs[(*nph)++] = PH_TSH; break;
    case 0x091c: phs[(*nph)++] = PH_DZ; break;
    case 0x091d: phs[(*nph)++] = PH_DZH; break;
    case 0x091e: phs[(*nph)++] = PH_N; break;

    case 0x091f: phs[(*nph)++] = PH_T_RET; break;
    case 0x0920: phs[(*nph)++] = PH_TH_RET; break;
    case 0x0921:
        phs[(*nph)++] = (post_vocalic && !after_cons) ? PH_FLAP : PH_D_RET;
        break;
    case 0x0922:
        phs[(*nph)++] = (post_vocalic && !after_cons) ? PH_FLAP_H : PH_DH_RET;
        break;
    case 0x0923:
        /* ण stays retroflex [ɳ] immediately before a retroflex stop (घण्टा, कण्ठ),
           and merges to alveolar [n] elsewhere in spoken Nepali (कारण, नारायण). */
        phs[(*nph)++] = (next_cp >= 0x091f && next_cp <= 0x0922)
                      ? PH_N_RET : PH_N;
        break;

    case 0x0924: phs[(*nph)++] = PH_T_DENT; break;
    case 0x0925: phs[(*nph)++] = PH_TH_DENT; break;
    case 0x0926: phs[(*nph)++] = PH_D_DENT; break;
    case 0x0927: phs[(*nph)++] = PH_DH_DENT; break;
    case 0x0928:
    case 0x0929: phs[(*nph)++] = PH_N; break;

    case 0x092a: phs[(*nph)++] = PH_P; break;
    case 0x092b: phs[(*nph)++] = PH_PH; break;
    case 0x092c: phs[(*nph)++] = PH_B; break;
    case 0x092d: phs[(*nph)++] = PH_BH; break;
    case 0x092e: phs[(*nph)++] = PH_M; break;

    case 0x092f:
    case 0x095f: phs[(*nph)++] = PH_Y; break;
    case 0x0930:
    case 0x0931: phs[(*nph)++] = PH_R_TAP; break;
    case 0x0932:
    case 0x0933:
    case 0x0934: phs[(*nph)++] = PH_L; break;
    /* Nepali व: [w] after a consonant in an onset cluster (स्वागत, विश्व, द्वार),
       [b] word-initially and post-vocalically (विकास, विराटनगर, वातावरण). */
    case 0x0935: phs[(*nph)++] = after_cons ? PH_W : PH_B; break;

    /* Merged alveolar sibilant /s/ across श, ष, स in spoken Nepali */
    case 0x0936:
    case 0x0937:
    case 0x0938: phs[(*nph)++] = PH_S; break;
    case 0x0939: phs[(*nph)++] = PH_H; break;

    case 0x0958: phs[(*nph)++] = PH_K; break;
    case 0x0959: phs[(*nph)++] = PH_KH; break;
    case 0x095a: phs[(*nph)++] = PH_G; break;
    case 0x095b: phs[(*nph)++] = PH_DZ; break;
    case 0x095c: phs[(*nph)++] = PH_FLAP; break;
    case 0x095d: phs[(*nph)++] = PH_FLAP_H; break;
    case 0x095e: phs[(*nph)++] = PH_PH; break;
    default: break;
    }
}

static char ne_homorganic_nasal(uint32_t next_cp)
{
    switch (indic_cons_place(next_cp)) {
    case PL_VELAR:   return PH_NG;
    /* Before Nepali dental-alveolar च छ ज झ, nasal assimilates to alveolar /n/ */
    case PL_PALATAL: return PH_N;
    case PL_RETRO:   return PH_N_RET;
    case PL_DENTAL:  return PH_N;
    case PL_LABIAL:  return PH_M;
    default:         return PH_N;
    }
}

/* Match attached Nepali case clitics & plural marker (-हरू, -लाई, -बाट, -देखि,
   -सम्म, -सँग, -भित्र, -बाहिर, -माथि, -मुनि, -को/-का/-की, -मा, -ले) from the
   right edge of `aks[0..end-1]`, returning the stem boundary index (and
   setting `*clitic_keeps_final_v = 1` for clitics like -बाट, -सँग, -भित्र,
   -बाहिर whose own final /ʌ/ is pronounced!). */
static int ne_strip_one_clitic(const IndicAkshara *aks, int end,
                               int *clitic_keeps_final_v)
{
    if (end < 3)
        return -1;

    /* 2-akshara clitics (require >= 2 aksharas in preceding stem -> end >= 4
       or 1 heavy akshara stem -> end >= 3): */
    {
        const IndicAkshara *a1 = &aks[end - 2];
        const IndicAkshara *a2 = &aks[end - 1];

        /* -हरू (ह + रू/रु) */
        if (a1->ncons == 1 && a1->cons[0] == 0x0939 && a1->inherent
            && a2->ncons == 1 && a2->cons[0] == 0x0930
            && (a2->vowel_cp == 0x0909 || a2->vowel_cp == 0x090a))
            return end - 2;

        /* -लाई (ला + ई/इ) */
        if (a1->ncons == 1 && a1->cons[0] == 0x0932 && a1->vowel_cp == 0x0906
            && a2->ncons == 0
            && (a2->vowel_cp == 0x0907 || a2->vowel_cp == 0x0908))
            return end - 2;

        /* -बाट (बा + ट) -> keeps final /ʌ/ [baʈʌ]! */
        if (a1->ncons == 1 && a1->cons[0] == 0x092c && a1->vowel_cp == 0x0906
            && a2->ncons == 1 && a2->cons[0] == 0x091f && a2->inherent) {
            if (clitic_keeps_final_v) *clitic_keeps_final_v = 1;
            return end - 2;
        }

        /* -देखि (दे + खि) */
        if (a1->ncons == 1 && a1->cons[0] == 0x0926 && a1->vowel_cp == 0x090f
            && a2->ncons == 1 && a2->cons[0] == 0x0916 && a2->vowel_cp == 0x0907)
            return end - 2;

        /* -सम्म (स + म्म) -> cluster keeps final /ʌ/ [sʌmmʌ]! */
        if (a1->ncons == 1 && a1->cons[0] == 0x0938 && a1->inherent
            && a2->ncons == 2 && a2->cons[0] == 0x092e && a2->cons[1] == 0x092e) {
            if (clitic_keeps_final_v) *clitic_keeps_final_v = 1;
            return end - 2;
        }

        /* -सँग (सँ + ग) -> keeps final /ʌ/ [sʌ̃ɡʌ]! */
        if (a1->ncons == 1 && a1->cons[0] == 0x0938 && a1->inherent
            && (a1->chandrabindu || a1->anusvara)
            && a2->ncons == 1 && a2->cons[0] == 0x0917 && a2->inherent) {
            if (clitic_keeps_final_v) *clitic_keeps_final_v = 1;
            return end - 2;
        }

        /* -माथि (मा + थि) / -मुनि (मु + नि) */
        if (a1->ncons == 1 && a1->cons[0] == 0x092e && a1->vowel_cp == 0x0906
            && a2->ncons == 1 && a2->cons[0] == 0x0925 && a2->vowel_cp == 0x0907)
            return end - 2;
        if (a1->ncons == 1 && a1->cons[0] == 0x092e && a1->vowel_cp == 0x0909
            && a2->ncons == 1 && a2->cons[0] == 0x0928 && a2->vowel_cp == 0x0907)
            return end - 2;
    }

    /* 1-akshara case clitics (-को, -का, -की, -मा, -ले), requiring stem >= 2 aksharas */
    if (end >= 3) {
        const IndicAkshara *last = &aks[end - 1];
        if (last->ncons == 1 && !last->anusvara && !last->chandrabindu) {
            uint32_t c = last->cons[0];
            uint32_t v = last->vowel_cp;
            if (c == 0x0915 && (v == 0x0913 || v == 0x0906 || v == 0x0908))
                return end - 1; /* -को / -का / -की */
            if (c == 0x092e && v == 0x0906)
                return end - 1; /* -मा */
            if (c == 0x0932 && v == 0x090f)
                return end - 1; /* -ले */
        }
    }
    return -1;
}

static int ne_stem_drops_final_v(const IndicAkshara *aks, int stem_len)
{
    const IndicAkshara *last;

    if (stem_len <= 1)
        return 0;
    last = &aks[stem_len - 1];
    if (!last->inherent || last->anusvara || last->chandrabindu || last->visarga)
        return 0;
    /* Keep final /ʌ/ after consonant clusters (मन्त्र, राष्ट्र, धर्म, शब्द) */
    if (last->ncons >= 2)
        return 0;
    /* Keep final /ʌ/ on -य (समय, विद्यालय, हिमालय) */
    if (last->ncons == 1 && last->cons[0] == 0x092f)
        return 0;
    /* Keep final /ʌ/ on finite -छ verbs (हुन्छ, गर्छ, आउँछ, जान्छ, पर्छ, सक्छ) */
    if (last->ncons == 1 && last->cons[0] == 0x091b)
        return 0;
    /* Keep final /ʌ/ on negative verbs ending in -ऐन / -एन (गर्दैन, हुँदैन, आएन) */
    if (last->ncons == 1 && last->cons[0] == 0x0928
        && (aks[stem_len - 2].vowel_cp == 0x0910
            || aks[stem_len - 2].vowel_cp == 0x090f))
        return 0;
    /* Keep final /ʌ/ on conjunctive participles ending in -एर (गरेर, खाएर, गएर, भनेर) */
    if (last->ncons == 1 && last->cons[0] == 0x0930
        && aks[stem_len - 2].vowel_cp == 0x090f)
        return 0;
    return 1;
}

static void ne_apply_morphophonology(IndicAkshara *aks, int nak, int *sec_aksh)
{
    int cur_end = nak;
    int outer_keeps_final = 0;

    *sec_aksh = -1;
    if (nak <= 1)
        return; /* Nepali monosyllables (म, त, र, न, छ) ALWAYS keep /ʌ/ */

    /* Strip stacked case/plural clitics from right to left (e.g. मानिसहरूलाई ->
       मानिस + हरू + लाई), dropping stem-final /ʌ/ on the noun base! */
    for (;;) {
        int keep_v = 0;
        int bnd = ne_strip_one_clitic(aks, cur_end, &keep_v);
        if (bnd < 2)
            break;
        if (cur_end == nak && keep_v)
            outer_keeps_final = 1;
        *sec_aksh = bnd;
        cur_end = bnd;
    }

    if (ne_stem_drops_final_v(aks, cur_end))
        aks[cur_end - 1].drop_schwa = 1;

    /* If no clitic was stripped, apply word-final /ʌ/ retention/deletion directly */
    if (cur_end == nak && !outer_keeps_final) {
        if (ne_stem_drops_final_v(aks, nak))
            aks[nak - 1].drop_schwa = 1;
    }
}

static void ne_emit_dev_word(IndicRom *rom, IndicBuf *out,
                             const uint32_t *cps, size_t n)
{
    IndicAkshara aks[MAX_AKSHARAS];
    char phs[256];
    int nph = 0;
    int nak, i, c;
    int sec_aksh = -1;
    int sec_syl = -1;
    int cur_syl = 0;
    const char *lex;

    (void)rom;
    lex = ne_lex_lookup(cps, n);
    if (lex) {
        ibuf_puts(out, "`[");
        ibuf_puts(out, lex);
        ibuf_putc(out, ']');
        return;
    }

    nak = indic_parse_aksharas(cps, n, aks, MAX_AKSHARAS);
    if (nak <= 0)
        return;

    ne_apply_morphophonology(aks, nak, &sec_aksh);

    for (i = 0; i < nak && nph < (int)sizeof phs - 8; i++) {
        IndicAkshara *ak = &aks[i];
        int post_voc = (i > 0 && aks[i - 1].vowel_cp != 0
                        && !aks[i - 1].drop_schwa && !aks[i - 1].anusvara);
        int nasalize_from_gya = 0;

        if (i == sec_aksh)
            sec_syl = cur_syl;

        c = 0;
        while (c < ak->ncons && nph < (int)sizeof phs - 6) {
            uint32_t c0 = ak->cons[c];
            uint32_t c1 = (c + 1 < ak->ncons) ? ak->cons[c + 1] : 0;
            uint32_t next_cp = c1 ? c1
                             : ((i + 1 < nak && aks[i + 1].ncons > 0)
                                ? aks[i + 1].cons[0] : 0);

            /* ज्ञ (ज् + ञ) -> Nepali [ɡj] + nasalized following vowel (ज्ञान ['ɡjãn]) */
            if (c0 == 0x091c && c1 == 0x091e) {
                phs[nph++] = PH_G;
                phs[nph++] = PH_Y;
                nasalize_from_gya = 1;
                c += 2;
                continue;
            }
            /* क्ष (क् + ष) -> Nepali word-initial [tsʰ] (क्षमा, क्षेत्र),
               word-medial [ktsʰ] (शिक्षा, परीक्षा, कक्षा) */
            if (c0 == 0x0915 && c1 == 0x0937) {
                if (i > 0 || c > 0)
                    phs[nph++] = PH_K;
                phs[nph++] = PH_TSH;
                c += 2;
                continue;
            }
            /* ह + sonorant (ह्र, ह्म, ह्न, ह्ल, ह्व, ह्य) -> murmured [ʱ] + sonorant */
            if (c0 == 0x0939 && (c1 == 0x0930 || c1 == 0x092e || c1 == 0x0928
                                 || c1 == 0x0932 || c1 == 0x0935 || c1 == 0x092f)) {
                phs[nph++] = PH_ASP_VD;
                ne_emit_cons(phs, &nph, (int)sizeof phs, c1, 1, 0, 0);
                c += 2;
                continue;
            }
            if ((c0 == 0x0921 || c0 == 0x0922) && c1 == c0) {
                phs[nph++] = (c0 == 0x0921) ? PH_D_RET : PH_DH_RET;
                c += 2;
                continue;
            }

            ne_emit_cons(phs, &nph, (int)sizeof phs, c0,
                         c > 0, (c == 0) ? post_voc : 0, next_cp);
            c++;
        }

        if (ak->vowel_cp != 0 && !ak->drop_schwa) {
            char vch;

            if (ak->vowel_cp == 0x090b) {
                /* ऋ / ृ -> Nepali [ri] (कृषि ['krisi], पृथ्वी ['pritʰwi]) */
                phs[nph++] = PH_R_TAP;
                vch = PH_II;
            } else if (ak->vowel_cp == 0x090c) {
                phs[nph++] = PH_L;
                vch = PH_II;
            } else {
                vch = ne_map_vowel(ak->vowel_cp);
            }

            if (ak->chandrabindu || nasalize_from_gya)
                vch = indic_nasalize_vowel(vch);

            if (ak->anusvara) {
                if (i + 1 < nak && aks[i + 1].ncons > 0) {
                    uint32_t nx = aks[i + 1].cons[0];
                    if (nx == 0x091c && aks[i + 1].ncons >= 2
                        && aks[i + 1].cons[1] == 0x091e) {
                        phs[nph++] = vch;
                        phs[nph++] = PH_NG;
                    } else if (indic_cons_is_stop_or_affricate(nx)) {
                        phs[nph++] = vch;
                        phs[nph++] = ne_homorganic_nasal(nx);
                    } else if (nx == 0x0935 || nx == 0x092f || nx == 0x0939) {
                        phs[nph++] = indic_nasalize_vowel(vch);
                    } else {
                        /* संसार, संस्कृत -> [sʌnsar], [sʌnskrit] */
                        phs[nph++] = vch;
                        phs[nph++] = PH_N;
                    }
                } else {
                    if (ak->inherent) {
                        phs[nph++] = vch;
                        phs[nph++] = PH_M;
                    } else {
                        phs[nph++] = indic_nasalize_vowel(vch);
                    }
                }
            } else {
                phs[nph++] = vch;
            }

            if (ak->visarga && nph < (int)sizeof phs - 2)
                phs[nph++] = PH_H;
            cur_syl++;
        }
    }

    if (nph > 0) {
        /* Nepali has consistent root-initial primary stress (syl 0), with
           optional secondary stress on compound/clitic constituents. */
        indic_emit_spr_word(out, phs, nph, 0, sec_syl >= 2 ? sec_syl : -1);
    }
}

/* ---- Complete Nepali Number System (0..99 + सय/हजार/लाख/करोड/अर्ब/खर्ब) - */

static const char * const ne_num_0_99[100] = {
    "शून्य", "एक", "दुई", "तीन", "चार", "पाँच", "छ", "सात", "आठ", "नौ",
    "दस", "एघार", "बाह्र", "तेह्र", "चौध", "पन्ध्र", "सोह्र", "सत्र", "अठार", "उन्नाइस",
    "बीस", "एक्काइस", "बाइस", "तेइस", "चौबीस", "पच्चीस", "छब्बीस", "सत्ताइस", "अठ्ठाइस", "उनन्तीस",
    "तीस", "एकतीस", "बत्तीस", "तेत्तीस", "चौँतीस", "पैँतीस", "छत्तीस", "सैँतीस", "अठतीस", "उनन्चालीस",
    "चालीस", "एकचालीस", "बयालीस", "त्रिचालीस", "चौवालीस", "पैँतालीस", "छयालीस", "सतचालीस", "अठचालीस", "उनन्पचास",
    "पचास", "एकाउन्न", "बाउन्न", "त्रिपन्न", "चौवन्न", "पचपन्न", "छपन्न", "सन्ताउन्न", "अन्ठाउन्न", "उनन्साठी",
    "साठी", "एकसाठी", "बयसट्ठी", "त्रिसट्ठी", "चौसट्ठी", "पैँसट्ठी", "छयसट्ठी", "सतसट्ठी", "अठसट्ठी", "उनन्सत्तरी",
    "सत्तरी", "एकहत्तर", "बहत्तर", "त्रिहत्तर", "चौहत्तर", "पचहत्तर", "छयहत्तर", "सतहत्तर", "अठहत्तर", "उनासी",
    "अस्सी", "एकासी", "बयासी", "त्रियासी", "चौरासी", "पचासी", "छयासी", "सत्तासी", "अठ्ठासी", "उनान्नब्बे",
    "नब्बे", "एकानब्बे", "बयानब्बे", "त्रियानब्बे", "चौरानब्बे", "पन्चानब्बे", "छयानब्बे", "सन्तानब्बे", "अन्ठानब्बे", "उनान्सय"
};

static void ne_append_word(IndicRom *rom, IndicBuf *out, const char *w)
{
    indic_space_before(out);
    indic_convert_span(rom, out, w, strlen(w), 1);
}

static void ne_emit_u64(IndicRom *rom, IndicBuf *out, uint64_t v)
{
    static const struct { uint64_t div; const char *name; } units[] = {
        { 100000000000ULL, "खर्ब" },
        { 1000000000ULL,   "अर्ब" },
        { 10000000ULL,     "करोड" },
        { 100000ULL,       "लाख" },
        { 1000ULL,         "हजार" },
        { 100ULL,          "सय" }
    };
    size_t u;

    if (v < 100) {
        ne_append_word(rom, out, ne_num_0_99[v]);
        return;
    }
    for (u = 0; u < sizeof units / sizeof units[0]; u++) {
        if (v >= units[u].div) {
            uint64_t q = v / units[u].div;
            uint64_t r = v % units[u].div;
            ne_emit_u64(rom, out, q);
            ne_append_word(rom, out, units[u].name);
            if (r > 0)
                ne_emit_u64(rom, out, r);
            return;
        }
    }
}

static void ne_emit_integer(IndicRom *rom, IndicBuf *out,
                            const char *digits, size_t ndigits)
{
    size_t i = 0;
    uint64_t val = 0;

    if (ndigits == 0)
        return;

    if ((ndigits > 1 && digits[0] == '0') || ndigits > 13) {
        for (i = 0; i < ndigits; i++)
            ne_append_word(rom, out, ne_num_0_99[digits[i] - '0']);
        return;
    }

    for (i = 0; i < ndigits; i++)
        val = val * 10 + (uint64_t)(digits[i] - '0');
    ne_emit_u64(rom, out, val);
}

static const char *ne_lookup_abbrev(const char *tok, size_t len)
{
    static const struct { const char *k; const char *v; } tab[] = {
        { "डा.", "डाक्टर" }, { "डा॰", "डाक्टर" },
        { "डॉ.", "डाक्टर" },
        { "प्रा.", "प्राध्यापक" }, { "प्रा॰", "प्राध्यापक" },
        { "श्री.", "श्रीमान्" },
        { "श्रीमती.", "श्रीमती" },
        { "स्व.", "स्वर्गीय" },
        { "रु.", "रुपैयाँ" }, { "रू.", "रुपैयाँ" },
        { "वि.", "विक्रम" }, { "सं.", "संवत्" },
        { "ई.", "ईस्वी" },
        { "कि.", "किलो" }, { "मी.", "मीटर" }, { "से.", "सेन्टि" },
        /* Titles, units and administrative shorthand, accepting both the
           Latin dot and the Devanagari abbreviation dot ॰ (U+0970).
           Values are ordinary Nepali words and go back through the
           converter, so the rules decide their sounds. */
        { "प्रो.", "प्रोफेसर" }, { "प्रो॰", "प्रोफेसर" },
        { "मि.", "मिनेट" }, { "मि॰", "मिनेट" },
        { "घं.", "घण्टा" }, { "घं॰", "घण्टा" },
        { "नं.", "नम्बर" }, { "नं॰", "नम्बर" },
        { "क्र.", "क्रम" }, { "क्र॰", "क्रम" },
        { "पृ.", "पृष्ठ" }, { "पृ॰", "पृष्ठ" },
        { "अ.", "अध्याय" }, { "अ॰", "अध्याय" },
        { "सुश्री.", "सुश्री" },
        { "किमी.", "किलोमीटर" }, { "किमी॰", "किलोमीटर" },
        { "सेमी.", "सेन्टिमीटर" }, { "सेमी॰", "सेन्टिमीटर" },
        { "मिमी.", "मिलिमीटर" }, { "मिमी॰", "मिलिमीटर" },
        { "केजि.", "किलोग्राम" }, { "केजि॰", "किलोग्राम" },
        { "लि.", "लिटर" }, { "लि॰", "लिटर" },
        { "सेक.", "सेकेन्ड" }, { "सेक॰", "सेकेन्ड" },
        { "ने.रू.", "नेपाली रुपैयाँ" }, { "ने.रू॰", "नेपाली रुपैयाँ" }
    };
    size_t i;
    for (i = 0; i < sizeof tab / sizeof tab[0]; i++) {
        if (strlen(tab[i].k) == len && memcmp(tok, tab[i].k, len) == 0)
            return tab[i].v;
    }
    return NULL;
}

static const char *ne_lookup_symbol(uint32_t cp)
{
    switch (cp) {
    case '=': return "बराबर";
    case '@': return "एट";
    case '&': return "र";
    case '*': return "स्टार";
    case '#': return "ह्यास";
    case '/': return "भाग";
    case '$': return "डलर";
    case '+': return "धन";
    case '%': return "प्रतिशत";
    case '^': return "क्यारेट";
    case '~': return "लगभग";
    case '\\': return "ब्याकस्ल्यास";
    case '|': return "पाइप";
    case '<': return "भन्दा कम";
    case '>': return "भन्दा बढी";
    case '_': return "अन्डरस्कोर";
    case '`': return "ब्याकटिक";
    case '{': return "खुलो घुँग्रो";
    case '}': return "बन्द घुँग्रो";
    case '[': return "खुलो ब्राकेट";
    case ']': return "बन्द ब्राकेट";
    case 0x20ac: return "युरो";
    case 0x00a3: return "पाउन्ड";
    case 0x00a5: return "येन";
    case 0x20b9: return "रुपैयाँ";
    case 0x00b0: return "डिग्री";
    case 0x00b1: return "धन वा ऋण";
    case 0x00d7: return "गुणन";
    case 0x00f7: return "भाग";
    case 0x2026: return "इत्यादि";
    case 0x2122: return "ट्रेडमार्क";
    case 0x00ae: return "दर्ता गरिएको";
    case 0x00a9: return "प्रतिलिपि अधिकार";
    case 0x00b6: return "अनुच्छेद";
    case 0x00a7: return "दफा";
    case 0x2192: return "तीर";
    case 0x2605: return "तारा";
    case 0x2665: return "मुटु";
    case 0x2713: return "ठीक चिह्न";
    case 0x2717: return "गलत चिह्न";
    default:  return NULL;
    }
}

EvvRom *ne_rom_new(const char *dir)
{
    IndicRom *rom;

    (void)dir;
    rom = (IndicRom *)calloc(1, sizeof *rom);
    if (!rom)
        return NULL;
    rom->base.ops      = &indic_rom_ops;
    rom->emit_dev_word = ne_emit_dev_word;
    rom->emit_integer  = ne_emit_integer;
    rom->decimal_word  = "दशमलव";
    rom->percent_word  = "प्रतिशत";
    rom->minus_word    = "ऋण";
    rom->plus_word     = "धन";
    rom->rupee_sg      = "रुपैयाँ";
    rom->rupee_pl      = "रुपैयाँ";
    rom->lookup_abbrev = ne_lookup_abbrev;
    rom->lookup_symbol = ne_lookup_symbol;
    return &rom->base;
}
