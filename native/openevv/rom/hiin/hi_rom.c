/* Hindi (hi-IN, 0x00100000) Devanagari & mixed-script romanizer for ECI.
 *
 * Implements Standard Hindi phonology:
 *   - 4-way stop contrast across 5 places (velar, palato-alveolar, retroflex,
 *     dental, labial), retroflex flaps /ɽ ɽʱ/, Perso-Arabic/English nuqta
 *     fricatives & uvulars (/q x ɣ z f/), labiodental approximant /ʋ/ vs
 *     post-consonantal [w], and murmured sonorants.
 *   - 11 oral vowels (/ə ɑː ɪ iː ʊ uː eː ɛː oː ɔː æ ɒ/) + full phonemic
 *     nasal vowel inventory, homorganic nasal assimilation for anusvara,
 *     /əɦ/ fronting ([ɛɦ] in कहना, बहन, शहर, जगह), Ohala's right-to-left
 *     schwa deletion with prefix/suffix boundary awareness, and syllable-weight
 *     stress assignment.
 *   - Complete Hindi number system (0..99 + सौ/हज़ार/लाख/करोड़/अरब/खरब),
 *     currencies, abbreviations, user dictionaries, and English fallback G2P.
 */

#include "indic_rom.h"

/* ---- High-frequency Hindi lexical exceptions (orthography != surface) - */

typedef struct {
    const char *word;
    const char *spr;
} HiLexEntry;

static const HiLexEntry hi_lex[] = {
    { "इन्हें", "I.1nh\xea" },
    { "इसलिये", "1Is.lI.ye" },
    { "इसलिए", "1Is.lI.e" },
    { "उन्हें", "U.1nh\xea" },
    { "एवं", "1e.\xe5\xf8m" },
    { "कहाँ", "k\xf8.1h\xe3" },
    { "कहां", "k\xf8.1h\xe3" },
    { "किन्हें", "kI.1nh\xea" },
    { "क्यों", "1ky\xf4" },
    { "क्योंकि", "1ky\xf4.kI" },
    { "चाहिए", "1Ca.hI.e" },
    { "छः", "1\xe7" "Eh" },
    { "छह", "1\xe7" "Eh" },
    { "जहाँ", "J\xf8.1h\xe3" },
    { "जहां", "J\xf8.1h\xe3" },
    { "जिन्हें", "JI.1nh\xea" },
    { "तुम्हें", "tU.1mh\xea" },
    { "दुःख", "1dUK" },
    { "नहीं", "n\xf8.1h\xef" },
    { "पुनः", "1pU.n\xf8h" },
    { "प्रायः", "1pra.y\xf8h" },
    { "बल्कि", "1b\xf8l.kI" },
    { "बहुत", "b\xf8.1hUt" },
    { "मुझे", "mU.1\xc7" "e" },
    { "में", "1m\xea" },
    { "मैं", "1m\xeb" },
    { "मैंने", "1m\xeb.ne" },
    { "यह", "1ye" },
    { "यहाँ", "y\xf8.1h\xe3" },
    { "यहां", "y\xf8.1h\xe3" },
    { "वहाँ", "\xe5\xf8.1h\xe3" },
    { "वहां", "\xe5\xf8.1h\xe3" },
    { "वह", "1\xe5o" },
    { "वे", "1\xe5" "e" },
    { "स्वयं", "sw\xf8.1y\xf8m" },
    { "हमें", "h\xf8.1m\xea" },
    { "हाँ", "1h\xe3" },
    { "हां", "1h\xe3" },
    { "हूँ", "1h\xfc" },
    { "हूं", "1h\xfc" },
    { "है", "1hE" },
    { "हैं", "1h\xeb" },
    /* ---- high-frequency function words, numerals and everyday nouns.
       SPR uses the same one-byte phoneme alphabet as the entries above;
       every code is one declared in indic_rom.h. ---- */
    { "और", "1cr" },
    { "एक", "1ek" },
    { "दो", "1do" },
    { "तीन", "1tin" },
    { "चार", "1Car" },
    { "पाँच", "1p\xe3C" },
    { "पांच", "1p\xe3C" },
    { "सात", "1sat" },
    { "आठ", "1a\xf1" },
    { "नौ", "1nc" },
    { "दस", "1d\xf8s" },
    { "ग्यारह", "1gya.rEh" },
    { "बारह", "1ba.rEh" },
    { "आप", "1ap" },
    { "हम", "1h\xf8m" },
    { "घर", "1Y\xf8r" },
    { "करना", "1k\xf8r.na" },
    { "करने", "1k\xf8r.ne" },
    { "करें", "1k\xf8r\xea" },
    { "होना", "1ho.na" },
    { "जाना", "1Ja.na" },
    { "क्या", "1kya" },
    { "साथ", "1saF" },
    { "लोग", "1loG" },
    { "आज", "1aJ" },
    { "कल", "1k\xf8l" },
    { "परसों", "1p\xf8r.so" },
    { "नमस्ते", "1n\xf8m\xf8s.te" },
    { "धन्यवाद", "1V\xf8.ny\xf8.\xe5ad" },
    { "शुक्रिया", "1SU.kri.ya" },
    { "अलविदा", "1\xf8l.\xe5i.da" },
    { "स्वागत", "1swa.G\xf8t" },
    { "अच्छा", "1\xf8C\xe7a" },
    { "अच्छी", "1\xf8C\xe7i" },
    { "बड़ा", "1b\xf8.ja" },
    { "बड़ी", "1b\xf8.ji" },
    { "छोटा", "1\xe7o.Ma" },
    { "छोटी", "1\xe7o.Mi" },
    { "लड़का", "1l\xf8j.ka" },
    { "लड़की", "1l\xf8j.ki" },
    { "आदमी", "1ad.mi" },
    { "औरत", "1cr\xf8t" },
    { "बच्चा", "1b\xf8CCa" },
    { "बच्ची", "1b\xf8CCi" },
    { "काम", "1kam" },
    { "बात", "1bat" },
    { "स्कूल", "1skul" },
    { "देश", "1deS" },
    { "शाम", "1Sam" },
    { "रात", "1rat" },
    { "दिन", "1din" },
    { "पानी", "1pa.ni" },
    { "मित्र", "1mitr" },
    { "जी", "1Ji" },
    { "सब", "1s\xf8b" },
    { "सबसे", "1s\xf8b.se" },
    { "यही", "1y\xf8.hi" },
    { "वही", "1\xe5\xf8.hi" },
    { "कोई", "1ko.i" },
    { "कुछ", "1kU\xe7" },
    { "बहन", "1bEhEn" },
    { "भाई", "1Bai" },
    { "माँ", "1m\xe3" },
    { "मां", "1m\xe3" },
    { "पिता", "1pi.ta" },
    { "माता", "1ma.ta" },
    { "बेटा", "1be.Ma" },
    { "बेटी", "1be.Mi" },
    { "रुपया", "1rUp.ya" },
    { "रुपये", "1rUp.ye" },
    { "रुपए", "1rUp.\xf8e" },
    { "पैसा", "1pE.sa" },
    { "पैसे", "1pE.se" },
    { "सुबह", "1sU.bEh" },
    { "दोपहर", "1do.pEh\xf8r" },
    { "साल", "1sal" },
    { "महीना", "1m\xf8.hi.na" },
    { "महीने", "1m\xf8.hi.ne" },
    { "हफ़्ता", "1h\xf8f.ta" },
    { "हफ्ता", "1h\xf8f.ta" },
    { "सप्ताह", "1s\xf8p.ta.h" },
    { "समय", "1s\xf8m\xf8y" },
    { "वक़्त", "1\xe5\xf8qt" },
    { "वक्त", "1\xe5\xf8kt" },
    { "नया", "1n.ya" },
    { "नई", "1n.i" },
    { "नए", "1n.\xe5e" },
    { "पुराना", "1pU.ra.na" },
    { "अच्छे", "1\xf8C\xe7e" },
    { "मेरा", "1me.ra" },
    { "मेरी", "1me.ri" },
    { "तेरा", "1te.ra" },
    { "हमारा", "1h\xf8.ma.ra" },
    { "तुम्हारा", "1tUm.ha.ra" },
    { "आपका", "1ap.ka" },
    { "नाम", "1nam" },
    { "कब", "1k\xf8b" },
    { "कैसे", "1kE.se" },
    { "कैसा", "1kE.sa" },
    { "कैसी", "1kE.si" },
    { "इधर", "1IV\xf8r" },
    { "उधर", "1UV\xf8r" },
    { "अंदर", "1\xf8n.d\xf8r" },
    { "बाहर", "1ba.h\xf8r" },
    { "ऊपर", "1u.p\xf8r" },
    { "नीचे", "1ni.Ce" },
    { "सामने", "1sam.ne" },
    { "पास", "1pas" },
    { "दूर", "1dur" },
    { "प्यार", "1pyar" },
    { "खाना", "1Ka.na" },
    { "पीना", "1pi.na" },
    { "सोना", "1so.na" },
    { "आना", "1a.na" },
    { "जाने", "1Ja.ne" },
    { "देना", "1de.na" },
    { "लेना", "1le.na" },
    { "बोलना", "1bol.na" },
    { "समझना", "1s\xf8m.\xc7na" },
    { "सीखना", "1siK.na" },
    { "पढ़ना", "1p\xf8j.na" },
    { "लिखना", "1lik.na" },
    { "ख़ुशी", "1xU.Si" },
    { "खुशी", "1xU.Si" },
    { "दुख", "1dUK" },
    { "ज़िंदगी", "1zIn.dgi" },
    { "जिन्दगी", "1JIn.dgi" },
    { "दुनिया", "1dU.ni.ya" },
    { "भारत", "1Bar\xf8t" },
    { "हिंदुस्तान", "1hIn.dUs.tan" },
    { "हिन्दुस्तान", "1hIn.dUs.tan" },
    { "नेपाल", "1ne.pal" },
    { "काठमांडू", "1ka\xf1.ma.XWu" },
    { "दिल्ली", "1dil.li" },
    { "मुंबई", "1mUm.bI" },
    { "बंबई", "1b\xf8m.bI" }
};

static const char *hi_lex_lookup(const uint32_t *cps, size_t n)
{
    IndicBuf tmp = { NULL, 0, 0 };
    size_t i;
    const char *hit = NULL;

    for (i = 0; i < n; i++)
        indic_utf8_encode(&tmp, cps[i]);
    if (!tmp.data)
        return NULL;
    for (i = 0; i < sizeof hi_lex / sizeof hi_lex[0]; i++) {
        if (strcmp(tmp.data, hi_lex[i].word) == 0) {
            hit = hi_lex[i].spr;
            break;
        }
    }
    ibuf_free(&tmp);
    return hit;
}

/* English loanwords in Devanagari where medial ड/ढ stays retroflex stop [ɖ] */
static int hi_keep_retro_stop(const uint32_t *cps, size_t n)
{
    static const char * const loans[] = {
        "ऑडियो", "ऑर्डर", "इंडिया", "कार्ड", "कनाडा", "कोड", "गार्ड",
        "नोएडा", "पाउडर", "बोर्ड", "मॉडल", "रेडियो", "रोड", "लंदन",
        "वीडियो", "सोडा", "सोडियम", "स्टेडियम", "हॉस्पिटल"
    };
    IndicBuf tmp = { NULL, 0, 0 };
    size_t i;
    int keep = 0;

    for (i = 0; i < n; i++)
        indic_utf8_encode(&tmp, cps[i]);
    if (!tmp.data)
        return 0;
    for (i = 0; i < sizeof loans / sizeof loans[0]; i++) {
        if (strstr(tmp.data, loans[i]) != NULL) {
            keep = 1;
            break;
        }
    }
    ibuf_free(&tmp);
    return keep;
}

static char hi_map_vowel(uint32_t vcp)
{
    switch (vcp) {
    case 0x0905: return PH_SCHWA;   /* अ /ə/ */
    case 0x0906: return PH_AA;      /* आ /ɑː/ */
    case 0x0907: return PH_IH;      /* इ /ɪ/ */
    case 0x0908: return PH_II;      /* ई /iː/ */
    case 0x0909: return PH_UH;      /* उ /ʊ/ */
    case 0x090a: return PH_UU;      /* ऊ /uː/ */
    case 0x090d: return PH_AE;      /* ऍ /æ/ */
    case 0x090f: return PH_EE;      /* ए /eː/ */
    case 0x0910: return PH_EH;      /* ऐ /ɛː/ */
    case 0x0911: return PH_OPEN_O;  /* ऑ /ɒ/ */
    case 0x0913: return PH_OO;      /* ओ /oː/ */
    case 0x0914: return PH_AW;      /* औ /ɔː/ */
    default:     return PH_SCHWA;
    }
}

static void hi_emit_cons(char *phs, int *nph, int max_ph, uint32_t cp,
                         int after_cons, int post_vocalic, int keep_retro)
{
    if (*nph >= max_ph - 3)
        return;
    switch (cp) {
    case 0x0915: phs[(*nph)++] = PH_K; break;
    case 0x0916: phs[(*nph)++] = PH_KH; break;
    case 0x0917: phs[(*nph)++] = PH_G; break;
    case 0x0918: phs[(*nph)++] = PH_GH; break;
    case 0x0919: phs[(*nph)++] = PH_NG; break;

    case 0x091a: phs[(*nph)++] = PH_CH; break;
    case 0x091b: phs[(*nph)++] = PH_CHH; break;
    case 0x091c: phs[(*nph)++] = PH_J; break;
    case 0x091d: phs[(*nph)++] = PH_JH; break;
    case 0x091e: phs[(*nph)++] = PH_NY; break;

    case 0x091f: phs[(*nph)++] = PH_T_RET; break;
    case 0x0920: phs[(*nph)++] = PH_TH_RET; break;
    case 0x0921:
        phs[(*nph)++] = (post_vocalic && !after_cons && !keep_retro)
                      ? PH_FLAP : PH_D_RET;
        break;
    case 0x0922:
        phs[(*nph)++] = (post_vocalic && !after_cons && !keep_retro)
                      ? PH_FLAP_H : PH_DH_RET;
        break;
    case 0x0923: phs[(*nph)++] = PH_N_RET; break;

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
    case 0x0935: phs[(*nph)++] = after_cons ? PH_W : PH_V_APP; break;

    case 0x0936: phs[(*nph)++] = PH_SH; break;
    case 0x0937: phs[(*nph)++] = PH_SH_RET; break;
    case 0x0938: phs[(*nph)++] = PH_S; break;
    case 0x0939: phs[(*nph)++] = PH_H; break;

    /* Nuqta consonants */
    case 0x0958: phs[(*nph)++] = PH_Q; break;
    case 0x0959: phs[(*nph)++] = PH_KH_UV; break;
    case 0x095a: phs[(*nph)++] = PH_GH_UV; break;
    case 0x095b: phs[(*nph)++] = PH_Z; break;
    case 0x095c: phs[(*nph)++] = PH_FLAP; break;
    case 0x095d: phs[(*nph)++] = PH_FLAP_H; break;
    case 0x095e: phs[(*nph)++] = PH_F; break;
    default: break;
    }
}

static char hi_homorganic_nasal(uint32_t next_cp)
{
    switch (indic_cons_place(next_cp)) {
    case PL_VELAR:   return PH_NG;
    case PL_PALATAL: return PH_NY;
    case PL_RETRO:   return PH_N_RET;
    case PL_DENTAL:  return PH_N;
    case PL_LABIAL:  return PH_M;
    default:         return PH_N;
    }
}

/* Check productive Hindi suffixes (-वाला/-वाली/-वाले, -पन, -कर, -दार) whose
   preceding stem-final schwa always deletes as if word-final. */
static int hi_find_suffix_boundary(const IndicAkshara *aks, int nak)
{
    if (nak >= 4) {
        /* -वाला / -वाली / -वाले */
        const IndicAkshara *a1 = &aks[nak - 2];
        const IndicAkshara *a2 = &aks[nak - 1];
        if (a1->ncons == 1 && a1->cons[0] == 0x0935 && a1->vowel_cp == 0x0906
            && a2->ncons == 1 && a2->cons[0] == 0x0932
            && (a2->vowel_cp == 0x0906 || a2->vowel_cp == 0x0908
                || a2->vowel_cp == 0x090f))
            return nak - 2;
        /* -पन / -कर / -दार */
        if (a1->ncons == 1 && a1->cons[0] == 0x092a && a1->inherent
            && a2->ncons == 1 && a2->cons[0] == 0x0928 && a2->inherent)
            return nak - 2;
        if (a1->ncons == 1 && a1->cons[0] == 0x0915 && a1->inherent
            && a2->ncons == 1 && a2->cons[0] == 0x0930 && a2->inherent)
            return nak - 2;
        if (a1->ncons == 1 && a1->cons[0] == 0x0926 && a1->vowel_cp == 0x0906
            && a2->ncons == 1 && a2->cons[0] == 0x0930 && a2->inherent)
            return nak - 2;
    }
    return -1;
}

/* Productive prefixes (अ-, अन-, सु-, कु-, बे-, ना-, उप-) where the first root
   akshara after the prefix resists schwa deletion (e.g., असफल -> [ə'səpʰəl]). */
static int hi_prefix_protected_idx(const IndicAkshara *aks, int nak)
{
    if (nak >= 4 && aks[0].ncons == 0 && aks[0].vowel_cp == 0x0905
        && !aks[0].anusvara) {
        /* अ + CV + CV + C (असफल, अचल, अमर) */
        if (aks[1].ncons == 1 && aks[1].inherent)
            return 1;
    }
    if (nak >= 4 && aks[0].ncons == 1 && !aks[0].anusvara
        && ((aks[0].cons[0] == 0x0938 && aks[0].vowel_cp == 0x0909)   /* सु- */
            || (aks[0].cons[0] == 0x0915 && aks[0].vowel_cp == 0x0909) /* कु- */
            || (aks[0].cons[0] == 0x092c && aks[0].vowel_cp == 0x090f) /* बे- */
            || (aks[0].cons[0] == 0x0928 && aks[0].vowel_cp == 0x0906))) { /* ना- */
        return 1;
    }
    return -1;
}

static void hi_apply_schwa_deletion(IndicAkshara *aks, int nak)
{
    int suf_idx = hi_find_suffix_boundary(aks, nak);
    int prot_idx = hi_prefix_protected_idx(aks, nak);
    int i;

    if (nak <= 1)
        return; /* Monosyllables like न, क, ख keep their inherent vowel */

    /* 1. Stem-final schwa before productive derivational suffix (-वाला, -पन, -कर, -दार) */
    if (suf_idx >= 2) {
        IndicAkshara *st = &aks[suf_idx - 1];
        if (st->inherent && st->ncons == 1 && !st->anusvara
            && !st->chandrabindu && !st->visarga)
            st->drop_schwa = 1;
    }

    /* 2. Word-final apocope: delete final inherent schwa unless the final
          akshara is a heavy Tatsama obstruent+sonorant cluster (-त्र, -क्र, -द्म, -त्न)
          or carries anusvara/chandrabindu/visarga. */
    {
        IndicAkshara *last = &aks[nak - 1];
        if (last->inherent && !last->anusvara && !last->chandrabindu
            && !last->visarga) {
            int keep_cluster_release = 0;
            if (last->ncons >= 2) {
                uint32_t c_last = last->cons[last->ncons - 1];
                uint32_t c_prev = last->cons[last->ncons - 2];
                /* Geminates (न्न, त्त) drop schwa; rising-sonority clusters
                   (त्र, प्र, क्र, द्म, त्न, श्व) keep a short release schwa. */
                if (c_last != c_prev
                    && (c_last == 0x0930 || c_last == 0x092f
                        || c_last == 0x0935 || c_last == 0x092e
                        || c_last == 0x0928))
                    keep_cluster_release = 1;
            }
            if (!keep_cluster_release)
                last->drop_schwa = 1;
        }
    }

    /* 3. Right-to-left medial VCəCV syncope (Ohala 1983):
          Delete aks[i]'s inherent schwa when:
          - i > 0 (never word-initial akshara) and i != prot_idx,
          - aks[i] has a single consonant (ncons == 1), inherent vowel, and no
            anusvara/chandrabindu/visarga,
          - aks[i-1] has a realized vowel (so aks[i]'s consonant is preceded by V,
            not a consonant cluster),
          - aks[i+1] has a single onset consonant (ncons == 1) AND a realized
            vowel (!aks[i+1].drop_schwa && aks[i+1].vowel_cp != 0). */
    for (i = nak - 2; i >= 1; i--) {
        if (i == prot_idx || aks[i].drop_schwa)
            continue;
        if (!aks[i].inherent || aks[i].ncons != 1)
            continue;
        if (aks[i].anusvara || aks[i].chandrabindu || aks[i].visarga)
            continue;
        /* Do not delete schwa on य after इ/ई/ए (e.g. प्रिया, नियम) */
        if (aks[i].cons[0] == 0x092f
            && (aks[i - 1].vowel_cp == 0x0907 || aks[i - 1].vowel_cp == 0x0908))
            continue;
        if (aks[i - 1].vowel_cp == 0 || aks[i - 1].drop_schwa)
            continue;
        if (aks[i + 1].ncons == 1 && aks[i + 1].vowel_cp != 0
            && !aks[i + 1].drop_schwa) {
            aks[i].drop_schwa = 1;
        }
    }
}

static void hi_emit_dev_word(IndicRom *rom, IndicBuf *out,
                             const uint32_t *cps, size_t n)
{
    IndicAkshara aks[MAX_AKSHARAS];
    char phs[256];
    int nph = 0;
    int nak, i, c;
    int keep_retro;
    const char *lex;

    (void)rom;
    lex = hi_lex_lookup(cps, n);
    if (lex) {
        ibuf_puts(out, "`[");
        ibuf_puts(out, lex);
        ibuf_putc(out, ']');
        return;
    }

    keep_retro = hi_keep_retro_stop(cps, n);
    nak = indic_parse_aksharas(cps, n, aks, MAX_AKSHARAS);
    if (nak <= 0)
        return;

    hi_apply_schwa_deletion(aks, nak);

    for (i = 0; i < nak && nph < (int)sizeof phs - 8; i++) {
        IndicAkshara *ak = &aks[i];
        int post_voc = (i > 0 && aks[i - 1].vowel_cp != 0
                        && !aks[i - 1].drop_schwa && !aks[i - 1].anusvara);

        /* Emit onset consonants with special Hindi conjunct rules */
        c = 0;
        while (c < ak->ncons && nph < (int)sizeof phs - 6) {
            uint32_t c0 = ak->cons[c];
            uint32_t c1 = (c + 1 < ak->ncons) ? ak->cons[c + 1] : 0;

            /* ज्ञ (ज् + ञ) -> [ɡj] */
            if (c0 == 0x091c && c1 == 0x091e) {
                phs[nph++] = PH_G;
                phs[nph++] = PH_Y;
                c += 2;
                continue;
            }
            /* क्ष (क् + ष) -> [kʃ] */
            if (c0 == 0x0915 && c1 == 0x0937) {
                phs[nph++] = PH_K;
                phs[nph++] = PH_SH;
                c += 2;
                continue;
            }
            /* ह + sonorant (ह्म ह्न ह्र ह्ल ह्व ह्य) -> murmured [ʱ] + sonorant */
            if (c0 == 0x0939 && (c1 == 0x092e || c1 == 0x0928 || c1 == 0x0930
                                 || c1 == 0x0932 || c1 == 0x0935 || c1 == 0x092f)) {
                phs[nph++] = PH_ASP_VD;
                hi_emit_cons(phs, &nph, (int)sizeof phs, c1, 1, 0, keep_retro);
                c += 2;
                continue;
            }
            /* Geminate ड्ड / ढ्ढ stays retroflex stop, never flaps */
            if ((c0 == 0x0921 || c0 == 0x0922) && c1 == c0) {
                phs[nph++] = (c0 == 0x0921) ? PH_D_RET : PH_DH_RET;
                c += 2;
                continue;
            }
            hi_emit_cons(phs, &nph, (int)sizeof phs, c0,
                         c > 0, (c == 0) ? post_voc : 0, keep_retro);
            c++;
        }

        /* Emit nucleus vowel (if not deleted by schwa syncope/apocope or halant) */
        if (ak->vowel_cp != 0 && !ak->drop_schwa) {
            char vch;

            if (ak->vowel_cp == 0x090b) {
                /* ऋ / ृ -> [ɾɪ] */
                phs[nph++] = PH_R_TAP;
                vch = PH_IH;
            } else if (ak->vowel_cp == 0x090c) {
                phs[nph++] = PH_L;
                vch = PH_IH;
            } else {
                vch = hi_map_vowel(ak->vowel_cp);
            }

            /* Standard Hindi /əɦ/ fronting:
               1. Inherent अ right before ह whose inherent schwa is dropped or
                  word-final (कहना ['kɛɦnɑ], पहला ['pɛɦlɑ], शहर ['ʃɛɦɛɾ],
                  बहन ['bɛɦɛn], जगह ['dʒəɡɛɦ], अहम ['ɛɦɛm]) -> E ([ɛ]).
               2. Inherent अ ON ह itself before final C (शहर, बहन, महल, नहर) -> E. */
            if (vch == PH_SCHWA && ak->inherent) {
                if (i + 1 < nak && aks[i + 1].ncons == 1
                    && aks[i + 1].cons[0] == 0x0939
                    && (aks[i + 1].drop_schwa || aks[i + 1].inherent)) {
                    vch = PH_EH;
                } else if (ak->ncons == 1 && ak->cons[0] == 0x0939
                           && i > 0 && aks[i - 1].inherent
                           && (i + 1 == nak
                               || (i + 1 == nak - 1 && aks[i + 1].drop_schwa))) {
                    vch = PH_EH;
                }
            }

            if (ak->chandrabindu)
                vch = indic_nasalize_vowel(vch);

            /* Anusvara handling */
            if (ak->anusvara) {
                if (i + 1 < nak && aks[i + 1].ncons > 0) {
                    uint32_t nx = aks[i + 1].cons[0];
                    if (nx == 0x091c && aks[i + 1].ncons >= 2
                        && aks[i + 1].cons[1] == 0x091e) {
                        /* संज्ञा -> [səŋɡjɑː] */
                        phs[nph++] = vch;
                        phs[nph++] = PH_NG;
                    } else if (indic_cons_is_stop_or_affricate(nx)) {
                        phs[nph++] = vch;
                        phs[nph++] = hi_homorganic_nasal(nx);
                    } else if (nx == 0x0939) {
                        /* सिंह -> [sɪŋɦ] */
                        phs[nph++] = indic_nasalize_vowel(vch);
                        phs[nph++] = PH_NG;
                    } else if (nx == 0x0935 || nx == 0x092f) {
                        phs[nph++] = indic_nasalize_vowel(vch);
                    } else {
                        /* Before sibilants/liquids (संसार, संस्कृत): nasalized + n */
                        phs[nph++] = vch;
                        phs[nph++] = PH_N;
                    }
                } else {
                    /* Word-final anusvara:
                       - After inherent अ (Sanskrit -अं, e.g. स्वयं, एवं) -> əm
                       - After long/oblique vowels (-ों, -ें, -ैं, -ीं, -ूँ, -ाँ) ->
                         pure vowel nasalization (लोगों, करें, लड़कियों, माँ). */
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
        }
    }

    if (nph > 0) {
        int st = indic_hindi_stress_syl(phs, nph);
        int sec = (st >= 2) ? 0 : -1;
        indic_emit_spr_word(out, phs, nph, st, sec);
    }
}

/* ---- Complete Hindi Number System (0..99 + सौ/हज़ार/लाख/करोड़/अरब/खरब) -- */

static const char * const hi_num_0_99[100] = {
    "शून्य", "एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ",
    "दस", "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस",
    "बीस", "इक्कीस", "बाईस", "तेईस", "चौबीस", "पच्चीस", "छब्बीस", "सत्ताईस", "अट्ठाईस", "उनतीस",
    "तीस", "इकतीस", "बत्तीस", "तैंतीस", "चौंतीस", "पैंतीस", "छत्तीस", "सैंतीस", "अड़तीस", "उनतालीस",
    "चालीस", "इकतालीस", "बयालीस", "तैंतालीस", "चौवालीस", "पैंतालीस", "छियालीस", "सैंतालीस", "अड़तालीस", "उनचास",
    "पचास", "इक्यावन", "बावन", "तिरेपन", "चौवन", "पचपन", "छप्पन", "सत्तावन", "अट्ठावन", "उनसठ",
    "साठ", "इकसठ", "बासठ", "तिरेसठ", "चौंसठ", "पैंसठ", "छियासठ", "सड़सठ", "अड़सठ", "उनहत्तर",
    "सत्तर", "इकहत्तर", "बहत्तर", "तिहत्तर", "चौहत्तर", "पचहत्तर", "छिहत्तर", "सतहत्तर", "अठहत्तर", "उनासी",
    "अस्सी", "इक्यासी", "बयासी", "तिरासी", "चौरासी", "पचासी", "छियासी", "सत्तासी", "अट्ठासी", "नवासी",
    "नब्बे", "इक्यानवे", "बानवे", "तिरानवे", "चौरानवे", "पचानवे", "छियानवे", "सत्तानवे", "अट्ठानवे", "निन्यानवे"
};

static void hi_append_word(IndicRom *rom, IndicBuf *out, const char *w)
{
    indic_space_before(out);
    indic_convert_span(rom, out, w, strlen(w), 1);
}

static void hi_emit_u64(IndicRom *rom, IndicBuf *out, uint64_t v)
{
    static const struct { uint64_t div; const char *name; } units[] = {
        { 100000000000ULL, "खरब" },
        { 1000000000ULL,   "अरब" },
        { 10000000ULL,     "करोड़" },
        { 100000ULL,       "लाख" },
        { 1000ULL,         "हज़ार" },
        { 100ULL,          "सौ" }
    };
    size_t u;

    if (v < 100) {
        hi_append_word(rom, out, hi_num_0_99[v]);
        return;
    }
    for (u = 0; u < sizeof units / sizeof units[0]; u++) {
        if (v >= units[u].div) {
            uint64_t q = v / units[u].div;
            uint64_t r = v % units[u].div;
            hi_emit_u64(rom, out, q);
            hi_append_word(rom, out, units[u].name);
            if (r > 0)
                hi_emit_u64(rom, out, r);
            return;
        }
    }
}

static void hi_emit_integer(IndicRom *rom, IndicBuf *out,
                            const char *digits, size_t ndigits)
{
    size_t i = 0;
    uint64_t val = 0;

    if (ndigits == 0)
        return;

    /* Leading zero sequence (e.g., 042 or OTP) or >13 digits -> digit by digit */
    if ((ndigits > 1 && digits[0] == '0') || ndigits > 13) {
        for (i = 0; i < ndigits; i++)
            hi_append_word(rom, out, hi_num_0_99[digits[i] - '0']);
        return;
    }

    for (i = 0; i < ndigits; i++)
        val = val * 10 + (uint64_t)(digits[i] - '0');
    hi_emit_u64(rom, out, val);
}

static const char *hi_lookup_abbrev(const char *tok, size_t len)
{
    static const struct { const char *k; const char *v; } tab[] = {
        { "डॉ.", "डॉक्टर" }, { "डॉ॰", "डॉक्टर" },
        { "प्रो.", "प्रोफेसर" }, { "प्रो॰", "प्रोफेसर" },
        { "श्री.", "श्रीमान" }, { "श्री॰", "श्रीमान" },
        { "श्रीमती.", "श्रीमती" }, { "सुश्री.", "सुश्री" },
        { "स्व.", "स्वर्गीय" }, { "स्व॰", "स्वर्गीय" },
        { "पं.", "पंडित" }, { "पं॰", "पंडित" },
        { "रु.", "रुपये" }, { "रू.", "रुपये" },
        { "ई.", "ईसवी" }, { "पू.", "पूर्व" },
        { "कि.", "किलो" }, { "मी.", "मीटर" }, { "से.", "सेंटी" },
        /* Titles, address, units and administrative shorthand. Both the
           Latin dot and the Devanagari abbreviation dot ॰ (U+0970) are
           accepted; the values go back through the converter, so only the
           spelling has to be right here, not the phonemes. */
        { "मि.", "मिनट" }, { "मि॰", "मिनट" },
        { "घं.", "घंटा" }, { "घं॰", "घंटा" },
        { "सि.", "सचिव" }, { "सि॰", "सचिव" },
        { "नं.", "नंबर" }, { "नं॰", "नंबर" },
        { "क्र.", "क्रम" }, { "क्र॰", "क्रम" },
        { "पृ.", "पृष्ठ" }, { "पृ॰", "पृष्ठ" },
        { "अ.", "अध्याय" }, { "अ॰", "अध्याय" },
        { "वि.", "विभाग" }, { "वि॰", "विभाग" },
        { "उप.", "उपाध्यक्ष" }, { "उप॰", "उपाध्यक्ष" },
        { "अधि.", "अधिशासी" }, { "अधि॰", "अधिशासी" },
        { "मु.", "मुख्य" }, { "मु॰", "मुख्य" },
        { "सा.", "साहब" }, { "सा॰", "साहब" },
        { "जन.", "जनरल" }, { "जन॰", "जनरल" },
        { "कर्नल.", "कर्नल" },
        { "किमी.", "किलोमीटर" }, { "किमी॰", "किलोमीटर" },
        { "सेमी.", "सेंटीमीटर" }, { "सेमी॰", "सेंटीमीटर" },
        { "मिमी.", "मिलीमीटर" }, { "मिमी॰", "मिलीमीटर" },
        { "किग्रा.", "किलोग्राम" }, { "किग्रा॰", "किलोग्राम" },
        { "मिली.", "मिलीलीटर" }, { "मिली॰", "मिलीलीटर" },
        { "लि.", "लीटर" }, { "लि॰", "लीटर" },
        { "सेक.", "सेकंड" }, { "सेक॰", "सेकंड" },
        { "एम.", "एम" }, { "पी.", "पी" },
        { "यू.", "यू" }, { "एस.", "एस" },
        { "बी.", "बी" }, { "सी.", "सी" }, { "डी.", "डी" }
    };
    size_t i;
    for (i = 0; i < sizeof tab / sizeof tab[0]; i++) {
        if (strlen(tab[i].k) == len && memcmp(tok, tab[i].k, len) == 0)
            return tab[i].v;
    }
    return NULL;
}

static const char *hi_lookup_symbol(uint32_t cp)
{
    switch (cp) {
    case '=': return "बराबर";
    case '@': return "एट";
    case '&': return "और";
    case '*': return "स्टार";
    case '#': return "हैश";
    case '/': return "बटा";
    case '$': return "डॉलर";
    case '+': return "धन";
    case '%': return "प्रतिशत";
    case '^': return "कैरट";
    case '~': return "लगभग";
    case '\\': return "बैकस्लैश";
    case '|': return "पाइप";
    case '<': return "इससे कम";
    case '>': return "इससे अधिक";
    case '_': return "अंडरस्कोर";
    case '`': return "बैकटिक";
    case '{': return "खुला घुंघरू";
    case '}': return "बंद घुंघरू";
    case '[': return "खुला ब्रैकेट";
    case ']': return "बंद ब्रैकेट";
    case 0x20ac: return "यूरो";
    case 0x00a3: return "पाउंड";
    case 0x00a5: return "येन";
    case 0x20b9: return "रुपये";
    case 0x00b0: return "डिग्री";
    case 0x00b1: return "धन या ऋण";
    case 0x00d7: return "गुणा";
    case 0x00f7: return "भाग";
    case 0x2026: return "वगैरह";
    case 0x2122: return "ट्रेडमार्क";
    case 0x00ae: return "पंजीकृत";
    case 0x00a9: return "कॉपीराइट";
    case 0x00b6: return "पैराग्राफ";
    case 0x00a7: return "धारा";
    case 0x2020: return "तारा चिह्न";
    case 0x2021: return "दोहरा तारा चिह्न";
    case 0x2192: return "तीर";
    case 0x2605: return "सितारा";
    case 0x2665: return "दिल";
    case 0x2713: return "सही का निशान";
    case 0x2717: return "गलत का निशान";
    default:  return NULL;
    }
}

EvvRom *hi_rom_new(const char *dir)
{
    IndicRom *rom;

    (void)dir;
    rom = (IndicRom *)calloc(1, sizeof *rom);
    if (!rom)
        return NULL;
    rom->base.ops      = &indic_rom_ops;
    rom->emit_dev_word = hi_emit_dev_word;
    rom->emit_integer  = hi_emit_integer;
    rom->decimal_word  = "दशमलव";
    rom->percent_word  = "प्रतिशत";
    rom->minus_word    = "ऋण";
    rom->plus_word     = "धन";
    rom->rupee_sg      = "रुपया";
    rom->rupee_pl      = "रुपये";
    rom->lookup_abbrev = hi_lookup_abbrev;
    rom->lookup_symbol = hi_lookup_symbol;
    return &rom->base;
}
