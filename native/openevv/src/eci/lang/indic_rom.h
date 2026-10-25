/* Shared Devanagari and Indo-Aryan romanizer foundation for Hindi (hiin)
 * and Nepali (nenp).
 *
 * Each language compiles its own translation unit (rom/hiin/hi_rom.c and
 * rom/nenp/ne_rom.c) with its own distinct phonology, vowel inventory,
 * inherent-vowel deletion/retention rules, affricate/approximant/sibilant
 * realizations, morphological clitic/verb rules, stress assignment, number
 * system, currency/abbreviations, and lexicon.
 */

#ifndef EVV_INDIC_ROM_H
#define EVV_INDIC_ROM_H

#include <ctype.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "eci_rom.h"

/* ---- SPR 1-byte phoneme codes (matching hiin/nenp .statements) ------- */

#define PH_GAP      '_'
#define PH_S1       '1'
#define PH_S2       '2'
#define PH_SYL      '.'

/* Voiceless / voiced unaspirated stops & base consonants */
#define PH_P        'p'
#define PH_B        'b'
#define PH_T_DENT   't'
#define PH_D_DENT   'd'
#define PH_K        'k'
#define PH_G        'g'

/* Aspirated & breathy-voiced (murmured) stops */
#define PH_PH       'P'          /* /pʰ/ */
#define PH_BH       'B'          /* /bʱ/ */
#define PH_TH_DENT  'F'          /* /t̪ʰ/ */
#define PH_DH_DENT  'V'          /* /d̪ʱ/ */
#define PH_KH       'K'          /* /kʰ/ */
#define PH_GH       'Y'          /* /ɡʱ/ */

/* Retroflex stops & flaps */
#define PH_T_RET    'M'          /* /ʈ/ */
#define PH_TH_RET   '\xf1'       /* /ʈʰ/ */
#define PH_D_RET    'W'          /* /ɖ/ */
#define PH_DH_RET   '\xd1'       /* /ɖʱ/ */
#define PH_N_RET    'X'          /* /ɳ/ */
#define PH_FLAP     'j'          /* /ɽ/ */
#define PH_FLAP_H   '\xfd'       /* /ɽʱ/ */

/* Palato-alveolar affricates (Hindi च छ ज झ) */
#define PH_CH       'C'          /* /tʃ/ */
#define PH_CHH      '\xe7'       /* /tʃʰ/ */
#define PH_J        'J'          /* /dʒ/ */
#define PH_JH       '\xc7'       /* /dʒʱ/ */

/* Dental-alveolar affricates (Nepali च छ ज झ) */
#define PH_TS       'T'          /* /ts/ */
#define PH_TSH      '\xf0'       /* /tsʰ/ */
#define PH_DZ       'D'          /* /dz/ */
#define PH_DZH      '\xd0'       /* /dzʱ/ */

/* Nasals */
#define PH_M        'm'          /* /m/ */
#define PH_N        'n'          /* /n/ */
#define PH_NY       'N'          /* /ɲ/ */
#define PH_NG       'G'          /* /ŋ/ */

/* Fricatives, liquids, glides & glottals */
#define PH_F        'f'          /* /f/ */
#define PH_V        'v'          /* /v/ */
#define PH_S        's'          /* /s/ */
#define PH_Z        'z'          /* /z/ */
#define PH_SH       'S'          /* /ʃ/ */
#define PH_ZH       'Z'          /* /ʒ/ */
#define PH_SH_RET   '\xdf'       /* /ʂ/ */
#define PH_Q        'q'          /* /q/ */
#define PH_KH_UV    'x'          /* /x/ */
#define PH_GH_UV    'A'          /* /ɣ/ */
#define PH_H        'h'          /* /ɦ/ */
#define PH_ASP_VL   'H'          /* [ʰ] */
#define PH_ASP_VD   'Q'          /* [ʱ] */
#define PH_R_TAP    'r'          /* /ɾ/ */
#define PH_R_TRILL  'R'          /* /r/ */
#define PH_L        'l'          /* /l/ */
#define PH_V_APP    '\xe5'       /* /ʋ/ */
#define PH_Y        'y'          /* /j/ */
#define PH_W        'w'          /* /w/ */

/* Oral vowels & diphthongs */
#define PH_SCHWA    '\xf8'       /* Hindi /ə/, Nepali /ʌ/ */
#define PH_AA       'a'          /* Hindi /ɑː/, Nepali /a/ */
#define PH_IH       'I'          /* /ɪ/ */
#define PH_II       'i'          /* /i(ː)/ */
#define PH_UH       'U'          /* /ʊ/ */
#define PH_UU       'u'          /* /u(ː)/ */
#define PH_EE       'e'          /* /e(ː)/ */
#define PH_EH       'E'          /* /ɛ(ː)/ */
#define PH_OO       'o'          /* /o(ː)/ */
#define PH_AW       'c'          /* /ɔ(ː)/ */
#define PH_OPEN_O   'O'          /* /ɒ/ */
#define PH_AE       '\xe6'       /* /æ/ */
#define PH_DIP_AI   '\xe4'       /* Nepali /ʌi̯/ */
#define PH_DIP_AU   '\xf6'       /* Nepali /ʌu̯/ */

/* Nasalized vowels & diphthongs */
#define PH_SCHWA_N  '\xe2'       /* /ə̃/ | /ʌ̃/ */
#define PH_AA_N     '\xe3'       /* /ɑ̃/ | /ã/ */
#define PH_IH_N     '\xee'       /* /ɪ̃/ */
#define PH_II_N     '\xef'       /* /ĩ/ */
#define PH_UH_N     '\xfb'       /* /ʊ̃/ */
#define PH_UU_N     '\xfc'       /* /ũ/ */
#define PH_EE_N     '\xea'       /* /ẽ/ */
#define PH_EH_N     '\xeb'       /* /ɛ̃/ */
#define PH_OO_N     '\xf4'       /* /õ/ */
#define PH_AW_N     '\xf5'       /* /ɔ̃/ */
#define PH_DIP_AI_N '\xc4'       /* Nepali /ʌ̃ĩ̯/ */
#define PH_DIP_AU_N '\xd6'       /* Nepali /ʌ̃ũ̯/ */

/* ---- Growable byte buffer -------------------------------------------- */

typedef struct {
    char  *data;
    size_t len;
    size_t cap;
} IndicBuf;

static int ibuf_reserve(IndicBuf *b, size_t extra)
{
    size_t need = b->len + extra + 1;
    size_t ncap;
    char  *p;

    if (need <= b->cap)
        return 1;
    ncap = b->cap ? b->cap * 2 : 256;
    while (ncap < need)
        ncap *= 2;
    p = (char *)realloc(b->data, ncap);
    if (!p)
        return 0;
    b->data = p;
    b->cap = ncap;
    return 1;
}

static void ibuf_clear(IndicBuf *b)
{
    b->len = 0;
    if (b->data)
        b->data[0] = 0;
}

static void ibuf_free(IndicBuf *b)
{
    free(b->data);
    b->data = NULL;
    b->len = 0;
    b->cap = 0;
}

static int ibuf_putc(IndicBuf *b, char c)
{
    if (!ibuf_reserve(b, 1))
        return 0;
    b->data[b->len++] = c;
    b->data[b->len] = 0;
    return 1;
}

static int ibuf_puts(IndicBuf *b, const char *s)
{
    size_t n = strlen(s);

    if (!ibuf_reserve(b, n))
        return 0;
    memcpy(b->data + b->len, s, n);
    b->len += n;
    b->data[b->len] = 0;
    return 1;
}

static int ibuf_putn(IndicBuf *b, const char *s, size_t n)
{
    if (!ibuf_reserve(b, n))
        return 0;
    memcpy(b->data + b->len, s, n);
    b->len += n;
    b->data[b->len] = 0;
    return 1;
}

/* ---- UTF-8 & UCS-2 helpers ------------------------------------------- */

static uint32_t indic_utf8_next(const char *s, size_t len, size_t *pos)
{
    size_t i = *pos;
    unsigned char b0;

    if (i >= len)
        return 0;
    b0 = (unsigned char)s[i];
    if (b0 < 0x80) {
        *pos = i + 1;
        return b0;
    }
    if ((b0 & 0xe0) == 0xc0 && i + 1 < len) {
        unsigned char b1 = (unsigned char)s[i + 1];
        if ((b1 & 0xc0) == 0x80) {
            uint32_t cp = ((uint32_t)(b0 & 0x1f) << 6) | (uint32_t)(b1 & 0x3f);
            if (cp >= 0x80) {
                *pos = i + 2;
                return cp;
            }
        }
    } else if ((b0 & 0xf0) == 0xe0 && i + 2 < len) {
        unsigned char b1 = (unsigned char)s[i + 1];
        unsigned char b2 = (unsigned char)s[i + 2];
        if ((b1 & 0xc0) == 0x80 && (b2 & 0xc0) == 0x80) {
            uint32_t cp = ((uint32_t)(b0 & 0x0f) << 12)
                        | ((uint32_t)(b1 & 0x3f) << 6)
                        | (uint32_t)(b2 & 0x3f);
            if (cp >= 0x800 && !(cp >= 0xd800 && cp <= 0xdfff)) {
                *pos = i + 3;
                return cp;
            }
        }
    } else if ((b0 & 0xf8) == 0xf0 && i + 3 < len) {
        unsigned char b1 = (unsigned char)s[i + 1];
        unsigned char b2 = (unsigned char)s[i + 2];
        unsigned char b3 = (unsigned char)s[i + 3];
        if ((b1 & 0xc0) == 0x80 && (b2 & 0xc0) == 0x80 && (b3 & 0xc0) == 0x80) {
            uint32_t cp = ((uint32_t)(b0 & 0x07) << 18)
                        | ((uint32_t)(b1 & 0x3f) << 12)
                        | ((uint32_t)(b2 & 0x3f) << 6)
                        | (uint32_t)(b3 & 0x3f);
            if (cp >= 0x10000 && cp <= 0x10ffff) {
                *pos = i + 4;
                return cp;
            }
        }
    }
    *pos = i + 1;
    return b0;
}

static void indic_utf8_encode(IndicBuf *b, uint32_t cp)
{
    char tmp[4];

    if (cp < 0x80) {
        ibuf_putc(b, (char)cp);
    } else if (cp < 0x800) {
        tmp[0] = (char)(0xc0 | (cp >> 6));
        tmp[1] = (char)(0x80 | (cp & 0x3f));
        ibuf_putn(b, tmp, 2);
    } else if (cp < 0x10000) {
        tmp[0] = (char)(0xe0 | (cp >> 12));
        tmp[1] = (char)(0x80 | ((cp >> 6) & 0x3f));
        tmp[2] = (char)(0x80 | (cp & 0x3f));
        ibuf_putn(b, tmp, 3);
    } else if (cp <= 0x10ffff) {
        tmp[0] = (char)(0xf0 | (cp >> 18));
        tmp[1] = (char)(0x80 | ((cp >> 12) & 0x3f));
        tmp[2] = (char)(0x80 | ((cp >> 6) & 0x3f));
        tmp[3] = (char)(0x80 | (cp & 0x3f));
        ibuf_putn(b, tmp, 4);
    }
}

/* Nasalize an oral SPR vowel code. */
static char indic_nasalize_vowel(char v)
{
    switch ((unsigned char)v) {
    case (unsigned char)PH_SCHWA:  return PH_SCHWA_N;
    case (unsigned char)PH_AA:     return PH_AA_N;
    case (unsigned char)PH_IH:     return PH_IH_N;
    case (unsigned char)PH_II:     return PH_II_N;
    case (unsigned char)PH_UH:     return PH_UH_N;
    case (unsigned char)PH_UU:     return PH_UU_N;
    case (unsigned char)PH_EE:     return PH_EE_N;
    case (unsigned char)PH_EH:     return PH_EH_N;
    case (unsigned char)PH_AE:     return PH_EH_N;
    case (unsigned char)PH_OO:     return PH_OO_N;
    case (unsigned char)PH_AW:     return PH_AW_N;
    case (unsigned char)PH_OPEN_O: return PH_AW_N;
    case (unsigned char)PH_DIP_AI: return PH_DIP_AI_N;
    case (unsigned char)PH_DIP_AU: return PH_DIP_AU_N;
    default:                       return v;
    }
}

static int indic_is_vowel_ph(char p)
{
    switch ((unsigned char)p) {
    case (unsigned char)PH_SCHWA:
    case (unsigned char)PH_AA:
    case (unsigned char)PH_IH:
    case (unsigned char)PH_II:
    case (unsigned char)PH_UH:
    case (unsigned char)PH_UU:
    case (unsigned char)PH_EE:
    case (unsigned char)PH_EH:
    case (unsigned char)PH_OO:
    case (unsigned char)PH_AW:
    case (unsigned char)PH_OPEN_O:
    case (unsigned char)PH_AE:
    case (unsigned char)PH_DIP_AI:
    case (unsigned char)PH_DIP_AU:
    case (unsigned char)PH_SCHWA_N:
    case (unsigned char)PH_AA_N:
    case (unsigned char)PH_IH_N:
    case (unsigned char)PH_II_N:
    case (unsigned char)PH_UH_N:
    case (unsigned char)PH_UU_N:
    case (unsigned char)PH_EE_N:
    case (unsigned char)PH_EH_N:
    case (unsigned char)PH_OO_N:
    case (unsigned char)PH_AW_N:
    case (unsigned char)PH_DIP_AI_N:
    case (unsigned char)PH_DIP_AU_N:
        return 1;
    default:
        return 0;
    }
}

static int indic_is_short_vowel_ph(char p)
{
    switch ((unsigned char)p) {
    case (unsigned char)PH_SCHWA:
    case (unsigned char)PH_IH:
    case (unsigned char)PH_UH:
    case (unsigned char)PH_SCHWA_N:
    case (unsigned char)PH_IH_N:
    case (unsigned char)PH_UH_N:
        return 1;
    default:
        return 0;
    }
}

/* ---- Devanagari classification & canonicalization -------------------- */

enum {
    PL_NONE = 0,
    PL_VELAR,
    PL_PALATAL,
    PL_RETRO,
    PL_DENTAL,
    PL_LABIAL,
    PL_GLOTTAL
};

static int indic_is_devanagari(uint32_t cp)
{
    return (cp >= 0x0900 && cp <= 0x097f)
        || (cp >= 0xa8e0 && cp <= 0xa8ff)
        || cp == 0x200c || cp == 0x200d;
}

static int indic_dev_digit(uint32_t cp)
{
    if (cp >= 0x0966 && cp <= 0x096f)
        return (int)(cp - 0x0966);
    if (cp >= '0' && cp <= '9')
        return (int)(cp - '0');
    return -1;
}

static int indic_is_dev_cons(uint32_t cp)
{
    return (cp >= 0x0915 && cp <= 0x0939)
        || (cp >= 0x0958 && cp <= 0x095f)
        || cp == 0x0979 || cp == 0x097a;
}

static int indic_is_dev_indep_vowel(uint32_t cp)
{
    return (cp >= 0x0904 && cp <= 0x0914)
        || cp == 0x0960 || cp == 0x0961
        || cp == 0x0972 || (cp >= 0x0973 && cp <= 0x0977);
}

static int indic_is_dev_matra(uint32_t cp)
{
    return (cp >= 0x093e && cp <= 0x094c)
        || cp == 0x093a || cp == 0x093b
        || cp == 0x094e || cp == 0x094f
        || cp == 0x0955 || cp == 0x0956 || cp == 0x0957
        || cp == 0x0962 || cp == 0x0963;
}

/* Fold base + nuqta and decomposed vowel combinations in-place. */
static size_t indic_normalize_cps(uint32_t *cps, size_t n)
{
    size_t r = 0, w = 0;

    while (r < n) {
        uint32_t c = cps[r];
        uint32_t nx = (r + 1 < n) ? cps[r + 1] : 0;

        /* Strip zero-width joiner inside conjuncts while keeping ZWNJ as an
           explicit virama boundary marker when after halant. */
        if (c == 0x200d) {
            r++;
            continue;
        }

        if (nx == 0x093c) { /* Nuqta combining mark */
            uint32_t folded = 0;
            switch (c) {
            case 0x0915: folded = 0x0958; break; /* क़ */
            case 0x0916: folded = 0x0959; break; /* ख़ */
            case 0x0917: folded = 0x095a; break; /* ग़ */
            case 0x091c: folded = 0x095b; break; /* ज़ */
            case 0x0921: folded = 0x095c; break; /* ड़ */
            case 0x0922: folded = 0x095d; break; /* ढ़ */
            case 0x092b: folded = 0x095e; break; /* फ़ */
            case 0x092f: folded = 0x095f; break; /* य़ */
            case 0x0928: folded = 0x0929; break; /* ऩ */
            case 0x0930: folded = 0x0931; break; /* ऱ */
            case 0x0933: folded = 0x0934; break; /* ऴ */
            default: break;
            }
            if (folded) {
                cps[w++] = folded;
                r += 2;
                continue;
            }
            /* Unrecognized base + nuqta: keep base, skip stray nuqta */
            cps[w++] = c;
            r += 2;
            continue;
        }

        /* Common decomposed independent vowels typed on Indic keyboards:
           अ + ा -> आ, अ + ो -> ओ, अ + ौ -> औ, अ + ॅ -> ऍ, अ + ॉ -> ऑ,
           ए + े -> ऐ, ए + ॅ -> ऍ. */
        if (c == 0x0905) {
            uint32_t v = 0;
            switch (nx) {
            case 0x093e: v = 0x0906; break;
            case 0x0945: v = 0x090d; break;
            case 0x0946: v = 0x0904; break;
            case 0x0949: v = 0x0911; break;
            case 0x094a: v = 0x0912; break;
            case 0x094b: v = 0x0913; break;
            case 0x094c: v = 0x0914; break;
            default: break;
            }
            if (v) {
                cps[w++] = v;
                r += 2;
                continue;
            }
        } else if (c == 0x090f) {
            if (nx == 0x0947 || nx == 0x0948) {
                cps[w++] = 0x0910;
                r += 2;
                continue;
            }
            if (nx == 0x0945) {
                cps[w++] = 0x090d;
                r += 2;
                continue;
            }
        }

        cps[w++] = c;
        r++;
    }
    return w;
}

static int indic_cons_place(uint32_t cp)
{
    switch (cp) {
    case 0x0915: case 0x0916: case 0x0917: case 0x0918: case 0x0919:
    case 0x0958: case 0x0959: case 0x095a:
        return PL_VELAR;
    case 0x091a: case 0x091b: case 0x091c: case 0x091d: case 0x091e:
    case 0x092f: case 0x095f: case 0x0936:
        return PL_PALATAL;
    case 0x091f: case 0x0920: case 0x0921: case 0x0922: case 0x0923:
    case 0x095c: case 0x095d: case 0x0937: case 0x0933: case 0x0934:
        return PL_RETRO;
    case 0x0924: case 0x0925: case 0x0926: case 0x0927: case 0x0928:
    case 0x0929: case 0x0930: case 0x0931: case 0x0932: case 0x0938:
    case 0x095b:
        return PL_DENTAL;
    case 0x092a: case 0x092b: case 0x092c: case 0x092d: case 0x092e:
    case 0x0935: case 0x095e:
        return PL_LABIAL;
    case 0x0939:
        return PL_GLOTTAL;
    default:
        return PL_NONE;
    }
}

static int indic_cons_is_stop_or_affricate(uint32_t cp)
{
    return (cp >= 0x0915 && cp <= 0x0928)
        || (cp >= 0x092a && cp <= 0x092e)
        || cp == 0x0958 || cp == 0x095c || cp == 0x095d;
}

/* ---- Structured Akshara / Syllable Unit ------------------------------ */

#define MAX_ONS_CONS  6
#define MAX_AKSHARAS  96

typedef struct {
    uint32_t cons[MAX_ONS_CONS]; /* Devanagari consonant CPs in onset cluster */
    int      ncons;
    uint32_t vowel_cp;           /* 0=none(halant), 0x0905=inherent, else explicit vowel/matra normalized to 0x0905..0x0914/0x090b */
    int      inherent;           /* 1 if vowel came from inherent अ */
    int      anusvara;           /* ं (0x0902) */
    int      chandrabindu;       /* ँ (0x0901) */
    int      visarga;            /* ः (0x0903) */
    int      explicit_halant;    /* trailing ् + ZWNJ or word-final ् */
    int      drop_schwa;         /* set by schwa/ʌ deletion pass */
} IndicAkshara;

static uint32_t indic_matra_to_vowel(uint32_t m)
{
    switch (m) {
    case 0x093e: return 0x0906; /* आ */
    case 0x093f: return 0x0907; /* इ */
    case 0x0940: return 0x0908; /* ई */
    case 0x0941: return 0x0909; /* उ */
    case 0x0942: return 0x090a; /* ऊ */
    case 0x0943:
    case 0x0944: return 0x090b; /* ऋ */
    case 0x0962:
    case 0x0963: return 0x090c; /* ऌ */
    case 0x0945: return 0x090d; /* ऍ */
    case 0x0946:
    case 0x0947: return 0x090f; /* ए */
    case 0x0948: return 0x0910; /* ऐ */
    case 0x0949: return 0x0911; /* ऑ */
    case 0x094a:
    case 0x094b: return 0x0913; /* ओ */
    case 0x094c: return 0x0914; /* औ */
    default:     return 0x0905;
    }
}

/* Parse a normalized Devanagari word into orthographic aksharas. */
static int indic_parse_aksharas(const uint32_t *cps, size_t n,
                                IndicAkshara *out, int max_out)
{
    size_t i = 0;
    int count = 0;

    while (i < n && count < max_out) {
        IndicAkshara *ak;

        /* Skip avagraha (ऽ, lengthens preceding vowel slightly) or stray marks */
        if (cps[i] == 0x093d || cps[i] == 0x200c) {
            i++;
            continue;
        }

        /* Sacred Om ॐ (U+0950) */
        if (cps[i] == 0x0950) {
            ak = &out[count++];
            memset(ak, 0, sizeof *ak);
            ak->vowel_cp = 0x0913; /* ओ */
            ak->cons[0] = 0x092e;  /* wait: ओम् -> vowel ओ then coda म् */
            ak->ncons = 0;
            if (count < max_out) {
                IndicAkshara *m_ak = &out[count++];
                memset(m_ak, 0, sizeof *m_ak);
                m_ak->cons[0] = 0x092e;
                m_ak->ncons = 1;
                m_ak->vowel_cp = 0;
                m_ak->explicit_halant = 1;
            }
            i++;
            continue;
        }

        if (indic_is_dev_indep_vowel(cps[i])) {
            ak = &out[count++];
            memset(ak, 0, sizeof *ak);
            if (cps[i] == 0x0972)
                ak->vowel_cp = 0x090d;
            else if (cps[i] == 0x0904)
                ak->vowel_cp = 0x090f;
            else if (cps[i] == 0x090e)
                ak->vowel_cp = 0x090f;
            else if (cps[i] == 0x0912)
                ak->vowel_cp = 0x0913;
            else if (cps[i] == 0x0960)
                ak->vowel_cp = 0x090b;
            else
                ak->vowel_cp = cps[i];
            ak->inherent = (ak->vowel_cp == 0x0905) ? 1 : 0;
            i++;
            while (i < n) {
                if (cps[i] == 0x0901) { ak->chandrabindu = 1; i++; }
                else if (cps[i] == 0x0902) { ak->anusvara = 1; i++; }
                else if (cps[i] == 0x0903) { ak->visarga = 1; i++; }
                else if (cps[i] == 0x093c) { i++; }
                else break;
            }
            continue;
        }

        if (indic_is_dev_cons(cps[i])) {
            ak = &out[count++];
            memset(ak, 0, sizeof *ak);
            ak->cons[ak->ncons++] = cps[i++];

            /* Gather conjunct consonants linked by virama (0x094d) unless
               broken by ZWNJ (0x200c) or end-of-word. */
            for (;;) {
                while (i < n && cps[i] == 0x093c)
                    i++;
                if (i < n && cps[i] == 0x094d) {
                    i++; /* consume virama */
                    if (i < n && cps[i] == 0x200c) {
                        i++; /* explicit ZWNJ break: consonant stays vowelless */
                        ak->vowel_cp = 0;
                        ak->explicit_halant = 1;
                        goto next_akshara;
                    }
                    while (i < n && cps[i] == 0x200d)
                        i++; /* ZWJ (half-form / Nepali eyelash-ra): keep cluster */
                    if (i < n && indic_is_dev_cons(cps[i])
                        && ak->ncons < MAX_ONS_CONS) {
                        ak->cons[ak->ncons++] = cps[i++];
                        continue;
                    }
                    /* Trailing virama at end of word or before non-consonant */
                    ak->vowel_cp = 0;
                    ak->explicit_halant = 1;
                    goto post_modifiers;
                }
                break;
            }

            if (i < n && indic_is_dev_matra(cps[i])) {
                ak->vowel_cp = indic_matra_to_vowel(cps[i]);
                ak->inherent = 0;
                i++;
            } else {
                ak->vowel_cp = 0x0905;
                ak->inherent = 1;
            }

        post_modifiers:
            while (i < n) {
                if (cps[i] == 0x0901) { ak->chandrabindu = 1; i++; }
                else if (cps[i] == 0x0902) { ak->anusvara = 1; i++; }
                else if (cps[i] == 0x0903) { ak->visarga = 1; i++; }
                else if (cps[i] == 0x093d || cps[i] == 0x093c) { i++; }
                else break;
            }
        next_akshara:
            continue;
        }

        /* Stray matra or modifier without base consonant */
        if (indic_is_dev_matra(cps[i])) {
            ak = &out[count++];
            memset(ak, 0, sizeof *ak);
            ak->vowel_cp = indic_matra_to_vowel(cps[i]);
            i++;
            continue;
        }
        i++;
    }
    return count;
}

/* ---- Syllabification & Stress insertion into SPR `[...]` ------------- */

/* Given a flat phoneme sequence `phs[0..nph-1]` for one word and a chosen
   primary-stress syllable index `stress_syl` (0-based), write `[...]` into
   `out` with `.` syllable breaks and `1` before the stressed syllable. */
static void indic_emit_spr_word(IndicBuf *out, const char *phs, int nph,
                                int stress_syl, int sec_syl)
{
    int v_pos[48];
    int syl_start[48];
    int nsyl = 0;
    int i, s;

    if (nph <= 0)
        return;

    for (i = 0; i < nph && nsyl < 48; i++) {
        if (indic_is_vowel_ph(phs[i]))
            v_pos[nsyl++] = i;
    }

    ibuf_puts(out, "`[");
    if (nsyl == 0) {
        ibuf_putc(out, PH_S1);
        ibuf_putn(out, phs, (size_t)nph);
        ibuf_putc(out, PH_SCHWA);
        ibuf_putc(out, ']');
        return;
    }

    syl_start[0] = 0;
    for (s = 1; s < nsyl; s++) {
        int prev_v = v_pos[s - 1];
        int cur_v = v_pos[s];
        int c_run = cur_v - prev_v - 1;

        if (c_run <= 0) {
            syl_start[s] = cur_v;
        } else if (c_run == 1) {
            syl_start[s] = cur_v - 1; /* V.CV */
        } else {
            /* Split consonant cluster: keep obstruent + liquid/glide onset
               together (C.CrV / C.CwV / C.CyV), otherwise VC.CV. */
            char last_c = phs[cur_v - 1];
            char prev_c = phs[cur_v - 2];
            int onset2 = ((last_c == PH_R_TAP || last_c == PH_W
                           || last_c == PH_Y || last_c == PH_L)
                          && prev_c != last_c
                          && prev_c != PH_N && prev_c != PH_M
                          && prev_c != PH_NG && prev_c != PH_N_RET
                          && prev_c != PH_NY);
            syl_start[s] = onset2 ? (cur_v - 2) : (cur_v - 1);
        }
    }

    if (stress_syl < 0 || stress_syl >= nsyl)
        stress_syl = 0;

    for (s = 0; s < nsyl; s++) {
        int beg = syl_start[s];
        int end = (s + 1 < nsyl) ? syl_start[s + 1] : nph;

        (void)sec_syl;
        if (s > 0) {
            ibuf_putc(out, PH_SYL);
        }
        if (s == stress_syl) {
            ibuf_putc(out, PH_S1);
        }
        ibuf_putn(out, phs + beg, (size_t)(end - beg));
    }
    ibuf_putc(out, ']');
}

/* Compute Hindi weight-sensitive stress index over `phs[0..nph-1]`. */
static int indic_hindi_stress_syl(const char *phs, int nph)
{
    int v_pos[48], syl_start[48], weight[48];
    int nsyl = 0, i, s;

    for (i = 0; i < nph && nsyl < 48; i++)
        if (indic_is_vowel_ph(phs[i]))
            v_pos[nsyl++] = i;
    if (nsyl <= 1)
        return 0;

    syl_start[0] = 0;
    for (s = 1; s < nsyl; s++) {
        int c_run = v_pos[s] - v_pos[s - 1] - 1;
        if (c_run <= 0) syl_start[s] = v_pos[s];
        else if (c_run == 1) syl_start[s] = v_pos[s] - 1;
        else {
            char lc = phs[v_pos[s] - 1];
            char pc = phs[v_pos[s] - 2];
            int onset2 = ((lc == PH_R_TAP || lc == PH_W || lc == PH_Y)
                          && pc != lc && pc != PH_N && pc != PH_M);
            syl_start[s] = onset2 ? (v_pos[s] - 2) : (v_pos[s] - 1);
        }
    }

    for (s = 0; s < nsyl; s++) {
        int end = (s + 1 < nsyl) ? syl_start[s + 1] : nph;
        int coda = end - v_pos[s] - 1;
        int v_long = !indic_is_short_vowel_ph(phs[v_pos[s]]);
        /* Final consonant extrametricality */
        if (s == nsyl - 1 && coda > 0)
            coda -= 1;
        weight[s] = (v_long ? 2 : 1) + coda;
    }

    /* Superheavy (weight >= 3) attracts stress from the right (up to penult/antepenult) */
    for (s = nsyl - 1; s >= 0; s--)
        if (weight[s] >= 3)
            return s;
    /* Otherwise rightmost heavy (weight == 2), preferring penult over final */
    for (s = nsyl - 2; s >= 0; s--)
        if (weight[s] >= 2)
            return s;
    if (weight[nsyl - 1] >= 2)
        return nsyl - 1;
    return (nsyl >= 2) ? (nsyl - 2) : 0;
}

/* ---- South Asian English fallback G2P for Latin words & acronyms ----- */

typedef struct {
    const char *word;
    const char *spr; /* phoneme body with '1'/'2'/'.' stress markers */
} IndicEnLex;

static const IndicEnLex indic_en_lex[] = {
    { "a", "1\xf8" },
    { "about", "\xf8.1baUt" },
    { "account", "\xf8.1kaUnt" },
    { "active", "1\xe6k.MIv" },
    { "add", "1\xe6W" },
    { "after", "1af.M\xf8r" },
    { "all", "1Ol" },
    { "allow", "\xf8.1laU" },
    { "also", "1Ol.so" },
    { "always", "1Ol.wez" },
    { "am", "1\xe6m" },
    { "an", "1\xe6n" },
    { "and", "1\xe6nW" },
    { "android", "1\xe6n.WrcIW" },
    { "app", "1\xe6p" },
    { "apple", "1\xe6p.\xf8l" },
    { "are", "1ar" },
    { "as", "1\xe6z" },
    { "at", "1\xe6M" },
    { "audio", "1O.Wi.o" },
    { "auto", "1O.Mo" },
    { "available", "\xf8.1ve.l\xf8.b\xf8l" },
    { "back", "1b\xe6k" },
    { "bank", "1b\xe6Gk" },
    { "battery", "1b\xe6.M\xf8.ri" },
    { "be", "1bi" },
    { "before", "bI.1for" },
    { "bluetooth", "1blu.MuF" },
    { "button", "1b\xf8.M\xf8n" },
    { "by", "1baI" },
    { "call", "1kOl" },
    { "camera", "1k\xe6m.ra" },
    { "can", "1k\xe6n" },
    { "cancel", "1k\xe6n.s\xf8l" },
    { "card", "1karW" },
    { "change", "1CenJ" },
    { "charging", "1Car.JIG" },
    { "chat", "1C\xe6M" },
    { "check", "1CEk" },
    { "clear", "1klI\xf8r" },
    { "click", "1klIk" },
    { "close", "1kloz" },
    { "code", "1koW" },
    { "com", "1kOm" },
    { "computer", "k\xf8m.1pyu.M\xf8r" },
    { "connect", "k\xf8.1nEkM" },
    { "connected", "k\xf8.1nEk.MIW" },
    { "contact", "1kOn.M\xe6kM" },
    { "copy", "1kO.pi" },
    { "current", "1k\xf8.r\xf8nM" },
    { "custom", "1k\xf8s.M\xf8m" },
    { "data", "1We.Ma" },
    { "date", "1WeM" },
    { "day", "1We" },
    { "default", "WI.1fOlM" },
    { "delete", "WI.1liM" },
    { "device", "WI.1vaIs" },
    { "dictionary", "1WIk.S\xf8.n\xf8.ri" },
    { "do", "1Wu" },
    { "doctor", "1WOk.M\xf8r" },
    { "done", "1W\xf8n" },
    { "dot", "1WOM" },
    { "double", "1W\xf8.b\xf8l" },
    { "down", "1WaUn" },
    { "download", "1WaUn.loW" },
    { "edit", "1E.WIM" },
    { "eight", "1eM" },
    { "eloquence", "1E.l\xf8.kwEns" },
    { "email", "1i.mel" },
    { "enable", "I.1ne.b\xf8l" },
    { "enabled", "I.1ne.b\xf8lW" },
    { "end", "1EnW" },
    { "engine", "1En.JIn" },
    { "english", "1IG.glIS" },
    { "enter", "1En.M\xf8r" },
    { "error", "1E.r\xf8r" },
    { "exit", "1Eg.zIM" },
    { "facebook", "1fes.bUk" },
    { "file", "1faIl" },
    { "find", "1faInW" },
    { "first", "1f\xf8rsM" },
    { "five", "1faIv" },
    { "folder", "1fol.W\xf8r" },
    { "for", "1fOr" },
    { "four", "1for" },
    { "free", "1fri" },
    { "from", "1frOm" },
    { "full", "1fUl" },
    { "get", "1gEM" },
    { "gmail", "1Ji.mel" },
    { "go", "1go" },
    { "good", "1gUW" },
    { "google", "1gu.g\xf8l" },
    { "group", "1grup" },
    { "has", "1h\xe6z" },
    { "have", "1h\xe6v" },
    { "hello", "hE.1lo" },
    { "help", "1hElp" },
    { "here", "1hI\xf8r" },
    { "high", "1haI" },
    { "hindi", "1hIn.di" },
    { "home", "1hom" },
    { "hour", "1aU\xf8r" },
    { "how", "1haU" },
    { "i", "1aI" },
    { "in", "1In" },
    { "india", "1In.Wi.a" },
    { "info", "1In.fo" },
    { "information", "In.f\xf8r.1me.S\xf8n" },
    { "input", "1In.pUM" },
    { "install", "In.1sMOl" },
    { "internet", "1In.M\xf8r.nEM" },
    { "is", "1Iz" },
    { "it", "1IM" },
    { "kathmandu", "1ka\xf1.man.Wu" },
    { "key", "1ki" },
    { "keyboard", "1ki.borW" },
    { "language", "1l\xe6G.gwIJ" },
    { "last", "1lasM" },
    { "left", "1lEfM" },
    { "level", "1lE.v\xf8l" },
    { "link", "1lIGk" },
    { "list", "1lIsM" },
    { "live", "1laIv" },
    { "load", "1loW" },
    { "loading", "1lo.WIG" },
    { "lock", "1lOk" },
    { "login", "1lO.gIn" },
    { "low", "1lo" },
    { "mail", "1mel" },
    { "main", "1men" },
    { "menu", "1mE.nyu" },
    { "message", "1mE.sIJ" },
    { "minute", "1mI.nIM" },
    { "mobile", "1mo.baIl" },
    { "mode", "1moW" },
    { "more", "1mor" },
    { "morning", "1mOr.nIG" },
    { "music", "1myu.zIk" },
    { "my", "1maI" },
    { "name", "1nem" },
    { "nepal", "nE.1pal" },
    { "nepali", "nE.1pa.li" },
    { "network", "1nEM.w\xf8rk" },
    { "new", "1nyu" },
    { "next", "1nEksM" },
    { "night", "1naIM" },
    { "nine", "1naIn" },
    { "no", "1no" },
    { "normal", "1nOr.m\xf8l" },
    { "not", "1nOM" },
    { "notification", "no.MI.fI.1ke.S\xf8n" },
    { "now", "1naU" },
    { "number", "1n\xf8m.b\xf8r" },
    { "of", "1Ov" },
    { "off", "1Of" },
    { "ok", "o.1ke" },
    { "okay", "o.1ke" },
    { "on", "1On" },
    { "one", "1w\xf8n" },
    { "online", "1On.laIn" },
    { "open", "1o.p\xf8n" },
    { "option", "1Op.S\xf8n" },
    { "options", "1Op.S\xf8nz" },
    { "or", "1Or" },
    { "out", "1aUM" },
    { "output", "1aUM.pUM" },
    { "page", "1peJ" },
    { "password", "1pas.w\xf8rW" },
    { "pause", "1pOz" },
    { "pay", "1pe" },
    { "percent", "p\xf8r.1sEnM" },
    { "phone", "1fon" },
    { "photo", "1fo.Mo" },
    { "pitch", "1pIC" },
    { "play", "1ple" },
    { "please", "1pliz" },
    { "plus", "1pl\xf8s" },
    { "point", "1pcInM" },
    { "power", "1paU.\xf8r" },
    { "press", "1prEs" },
    { "profile", "1pro.faIl" },
    { "quick", "1kwIk" },
    { "rate", "1reM" },
    { "read", "1riW" },
    { "ready", "1rE.Wi" },
    { "record", "rI.1kOrW" },
    { "reply", "rI.1plaI" },
    { "reset", "ri.1sEM" },
    { "right", "1raIM" },
    { "save", "1sev" },
    { "screen", "1skrin" },
    { "search", "1s\xf8rC" },
    { "second", "1sE.k\xf8nW" },
    { "select", "sI.1lEkM" },
    { "selected", "sI.1lEk.MIW" },
    { "send", "1sEnW" },
    { "service", "1s\xf8r.vIs" },
    { "set", "1sEM" },
    { "setting", "1sE.MIG" },
    { "settings", "1sE.MIGz" },
    { "seven", "1sE.v\xf8n" },
    { "share", "1SEr" },
    { "show", "1So" },
    { "sign", "1saIn" },
    { "sim", "1sIm" },
    { "six", "1sIks" },
    { "slash", "1sl\xe6S" },
    { "sound", "1saUnW" },
    { "space", "1spes" },
    { "speak", "1spik" },
    { "speech", "1spiC" },
    { "speed", "1spiW" },
    { "start", "1sMarM" },
    { "status", "1sMe.M\xf8s" },
    { "stop", "1sMOp" },
    { "switch", "1swIC" },
    { "system", "1sIs.M\xf8m" },
    { "tab", "1M\xe6" "b" },
    { "talkback", "1MOk.b\xe6k" },
    { "tap", "1M\xe6p" },
    { "ten", "1MEn" },
    { "test", "1MEsM" },
    { "text", "1MEksM" },
    { "thank", "1F\xe6Gk" },
    { "thanks", "1F\xe6Gks" },
    { "that", "1d\xe6M" },
    { "the", "1d\xf8" },
    { "there", "1dEr" },
    { "this", "1dIs" },
    { "three", "1Fri" },
    { "time", "1MaIm" },
    { "to", "1Mu" },
    { "today", "MU.1We" },
    { "top", "1MOp" },
    { "two", "1Mu" },
    { "type", "1MaIp" },
    { "up", "1\xf8p" },
    { "update", "\xf8p.1WeM" },
    { "use", "1yuz" },
    { "user", "1yu.z\xf8r" },
    { "version", "1v\xf8r.S\xf8n" },
    { "video", "1vI.Wi.o" },
    { "view", "1vyu" },
    { "voice", "1vcIs" },
    { "volume", "1vO.lyum" },
    { "wait", "1weM" },
    { "warning", "1wOr.nIG" },
    { "was", "1wOz" },
    { "water", "1wO.M\xf8r" },
    { "we", "1wi" },
    { "web", "1wEb" },
    { "welcome", "1wEl.k\xf8m" },
    { "what", "1wOM" },
    { "whatsapp", "1wOMs.\xe6p" },
    { "when", "1wEn" },
    { "where", "1wEr" },
    { "wifi", "1waI.faI" },
    { "will", "1wIl" },
    { "window", "1wIn.Wo" },
    { "with", "1wId" },
    { "word", "1w\xf8rW" },
    { "world", "1w\xf8rlW" },
    { "yes", "1yEs" },
    { "you", "1yu" },
    { "your", "1yor" },
    { "youtube", "1yu.Myub" },
    { "zero", "1zi.ro" }
};

static const char * const indic_en_letter_spr[26] = {
    "1e",        /* A */
    "1bi",       /* B */
    "1si",       /* C */
    "1Wi",       /* D */
    "1i",        /* E */
    "1Ef",       /* F */
    "1Ji",       /* G */
    "1eC",       /* H */
    "1aI",       /* I */
    "1Je",       /* J */
    "1ke",       /* K */
    "1El",       /* L */
    "1Em",       /* M */
    "1En",       /* N */
    "1o",        /* O */
    "1pi",       /* P */
    "1kyu",      /* Q */
    "1ar",       /* R */
    "1Es",       /* S */
    "1Mi",       /* T */
    "1yu",       /* U */
    "1vi",       /* V */
    "1W\xf8.blyu", /* W */
    "1Eks",      /* X */
    "1waI",      /* Y */
    "1zEW"       /* Z */
};

static const char *indic_en_lex_lookup(const char *low)
{
    size_t lo = 0;
    size_t hi = sizeof indic_en_lex / sizeof indic_en_lex[0];

    while (lo < hi) {
        size_t mid = lo + (hi - lo) / 2;
        int cmp = strcmp(low, indic_en_lex[mid].word);
        if (cmp == 0)
            return indic_en_lex[mid].spr;
        if (cmp < 0)
            hi = mid;
        else
            lo = mid + 1;
    }
    return NULL;
}

static int indic_is_known_initialism(const char *low)
{
    static const char * const inits[] = {
        "ai", "am", "api", "apk", "atm", "bbc", "cd", "ceo", "cnn", "cpu",
        "cv", "db", "dc", "dj", "dna", "dns", "doj", "dos", "dvd", "eu",
        "faq", "fbi", "fm", "fps", "ftp", "gb", "gdp", "gif", "gmt", "gps",
        "gpu", "gst", "gui", "hd", "hr", "html", "http", "https", "id",
        "imf", "inr", "io", "ios", "ip", "iq", "ir", "isp", "it", "kb",
        "kg", "km", "kyc", "lcd", "led", "lte", "mb", "md", "ml", "mm",
        "mp", "mr", "mrs", "ms", "ngo", "npr", "nri", "ok", "os", "otp",
        "pc", "pdf", "phd", "pin", "pm", "pr", "qr", "ram", "rbi", "rom",
        "rs", "sd", "sdk", "sim", "sms", "sql", "ssd", "ssh", "ssl", "tb",
        "tcp", "tts", "tv", "ui", "uk", "un", "upi", "url", "us", "usa",
        "usb", "utc", "uv", "vat", "vip", "vpn", "vr", "who", "www", "xml"
    };
    size_t lo = 0, hi = sizeof inits / sizeof inits[0];
    while (lo < hi) {
        size_t mid = lo + (hi - lo) / 2;
        int cmp = strcmp(low, inits[mid]);
        if (cmp == 0)
            return 1;
        if (cmp < 0)
            hi = mid;
        else
            lo = mid + 1;
    }
    return 0;
}

static int indic_en_is_vowel_ch(char c)
{
    return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y';
}

/* Rule-based South Asian English G2P for unseen Latin words. */
static void indic_emit_latin_word(IndicBuf *out, const char *w, size_t n)
{
    char low[96];
    char phs[192];
    int nph = 0;
    size_t i;
    int all_upper = 1, has_vowel = 0;
    const char *lex;

    if (n == 0)
        return;
    if (n >= sizeof low)
        n = sizeof low - 1;

    for (i = 0; i < n; i++) {
        unsigned char c = (unsigned char)w[i];
        if (c >= 'a' && c <= 'z')
            all_upper = 0;
        low[i] = (char)tolower(c);
        if (indic_en_is_vowel_ch(low[i]))
            has_vowel = 1;
    }
    low[n] = 0;

    /* Single letter or uppercase initialism / vowelless acronym -> spell out */
    if (n == 1 || !has_vowel
        || (all_upper && (n <= 3 || indic_is_known_initialism(low))
            && strcmp(low, "ok") != 0)) {
        for (i = 0; i < n; i++) {
            if (low[i] >= 'a' && low[i] <= 'z') {
                if (i > 0)
                    ibuf_putc(out, ' ');
                ibuf_puts(out, "`[");
                ibuf_puts(out, indic_en_letter_spr[low[i] - 'a']);
                ibuf_putc(out, ']');
            }
        }
        return;
    }

    lex = indic_en_lex_lookup(low);
    if (lex) {
        ibuf_puts(out, "`[");
        ibuf_puts(out, lex);
        ibuf_putc(out, ']');
        return;
    }

    /* Productive grapheme-to-phoneme rules (South Asian English phonology) */
    for (i = 0; i < n && nph < (int)sizeof phs - 6; ) {
        char c = low[i];
        char c1 = (i + 1 < n) ? low[i + 1] : 0;
        char c2 = (i + 2 < n) ? low[i + 2] : 0;
        char c3 = (i + 3 < n) ? low[i + 3] : 0;

        if (i + 4 <= n && memcmp(low + i, "tion", 4) == 0) {
            phs[nph++] = PH_SH; phs[nph++] = PH_SCHWA; phs[nph++] = PH_N;
            i += 4; continue;
        }
        if (i + 4 <= n && memcmp(low + i, "sion", 4) == 0) {
            phs[nph++] = PH_ZH; phs[nph++] = PH_SCHWA; phs[nph++] = PH_N;
            i += 4; continue;
        }
        if (i + 4 <= n && memcmp(low + i, "ture", 4) == 0) {
            phs[nph++] = PH_CH; phs[nph++] = PH_SCHWA; phs[nph++] = PH_R_TAP;
            i += 4; continue;
        }
        if (c == 'i' && c1 == 'g' && c2 == 'h') {
            phs[nph++] = PH_AA; phs[nph++] = PH_IH;
            i += 3; continue;
        }
        if (c == 'i' && c1 == 'n' && c2 == 'g' && i + 3 == n) {
            phs[nph++] = PH_IH; phs[nph++] = PH_NG;
            i += 3; continue;
        }
        if (c == 's' && c1 == 'h') { phs[nph++] = PH_SH; i += 2; continue; }
        if (c == 'c' && c1 == 'h') { phs[nph++] = PH_CH; i += 2; continue; }
        if (c == 'p' && c1 == 'h') { phs[nph++] = PH_F; i += 2; continue; }
        if (c == 't' && c1 == 'h') { phs[nph++] = PH_TH_DENT; i += 2; continue; }
        if (c == 'w' && c1 == 'h') { phs[nph++] = PH_W; i += 2; continue; }
        if (c == 'c' && c1 == 'k') { phs[nph++] = PH_K; i += 2; continue; }
        if (c == 'n' && c1 == 'g') { phs[nph++] = PH_NG; i += 2; continue; }
        if (c == 'q' && c1 == 'u') {
            phs[nph++] = PH_K; phs[nph++] = PH_W; i += 2; continue;
        }
        if (c == 'k' && c1 == 'n' && i == 0) {
            phs[nph++] = PH_N; i += 2; continue;
        }
        if (c == 'w' && c1 == 'r' && i == 0) {
            phs[nph++] = PH_R_TAP; i += 2; continue;
        }
        if (c == 'e' && (c1 == 'e' || c1 == 'a')) {
            phs[nph++] = PH_II; i += 2; continue;
        }
        if (c == 'o' && c1 == 'o') {
            phs[nph++] = PH_UU; i += 2; continue;
        }
        if (c == 'a' && (c1 == 'i' || c1 == 'y')) {
            phs[nph++] = PH_EE; i += 2; continue;
        }
        if (c == 'o' && (c1 == 'a' || c1 == 'w')) {
            phs[nph++] = PH_OO; i += 2; continue;
        }
        if (c == 'o' && c1 == 'u') {
            phs[nph++] = PH_AA; phs[nph++] = PH_UH; i += 2; continue;
        }
        if (c == 'o' && (c1 == 'i' || c1 == 'y')) {
            phs[nph++] = PH_AW; phs[nph++] = PH_IH; i += 2; continue;
        }
        if (c == 'a' && (c1 == 'u' || c1 == 'w')) {
            phs[nph++] = PH_OPEN_O; i += 2; continue;
        }
        if (c == 'e' && c1 == 'r' && !indic_en_is_vowel_ch(c2)) {
            phs[nph++] = PH_SCHWA; phs[nph++] = PH_R_TAP; i += 2; continue;
        }
        if ((c == 'i' || c == 'u') && c1 == 'r' && !indic_en_is_vowel_ch(c2)) {
            phs[nph++] = PH_SCHWA; phs[nph++] = PH_R_TAP; i += 2; continue;
        }
        if (c == 'a' && c1 == 'r' && !indic_en_is_vowel_ch(c2)) {
            phs[nph++] = PH_AA; phs[nph++] = PH_R_TAP; i += 2; continue;
        }
        if (c == 'o' && c1 == 'r' && !indic_en_is_vowel_ch(c2)) {
            phs[nph++] = PH_OPEN_O; phs[nph++] = PH_R_TAP; i += 2; continue;
        }

        /* Double consonant collapse */
        if (!indic_en_is_vowel_ch(c) && c == c1) {
            i++;
            continue;
        }

        /* Silent final 'e' + magic-e tense vowel */
        if (c == 'e' && i + 1 == n && nph > 0) {
            i++;
            continue;
        }
        if (indic_en_is_vowel_ch(c) && c1 && !indic_en_is_vowel_ch(c1)
            && c2 == 'e' && i + 3 == n && c3 == 0) {
            switch (c) {
            case 'a': phs[nph++] = PH_EE; break;
            case 'i': phs[nph++] = PH_AA; phs[nph++] = PH_IH; break;
            case 'o': phs[nph++] = PH_OO; break;
            case 'u': phs[nph++] = PH_Y; phs[nph++] = PH_UU; break;
            case 'e': phs[nph++] = PH_II; break;
            default:  phs[nph++] = PH_IH; break;
            }
            i++;
            continue;
        }

        switch (c) {
        case 'a': phs[nph++] = (i + 1 == n) ? PH_AA : PH_AE; break;
        case 'b': phs[nph++] = PH_B; break;
        case 'c':
            phs[nph++] = (c1 == 'e' || c1 == 'i' || c1 == 'y') ? PH_S : PH_K;
            break;
        case 'd': phs[nph++] = PH_D_RET; break;
        case 'e': phs[nph++] = PH_EH; break;
        case 'f': phs[nph++] = PH_F; break;
        case 'g':
            phs[nph++] = (c1 == 'e' || c1 == 'i' || c1 == 'y') ? PH_J : PH_G;
            break;
        case 'h': phs[nph++] = PH_H; break;
        case 'i': phs[nph++] = PH_IH; break;
        case 'j': phs[nph++] = PH_J; break;
        case 'k': phs[nph++] = PH_K; break;
        case 'l': phs[nph++] = PH_L; break;
        case 'm': phs[nph++] = PH_M; break;
        case 'n': phs[nph++] = PH_N; break;
        case 'o': phs[nph++] = (i + 1 == n) ? PH_OO : PH_OPEN_O; break;
        case 'p': phs[nph++] = PH_P; break;
        case 'q': phs[nph++] = PH_K; break;
        case 'r': phs[nph++] = PH_R_TAP; break;
        case 's': phs[nph++] = PH_S; break;
        case 't': phs[nph++] = PH_T_RET; break;
        case 'u': phs[nph++] = PH_SCHWA; break;
        case 'v': phs[nph++] = PH_V; break;
        case 'w': phs[nph++] = PH_W; break;
        case 'x': phs[nph++] = PH_K; phs[nph++] = PH_S; break;
        case 'y': phs[nph++] = (i == 0) ? PH_Y : PH_II; break;
        case 'z': phs[nph++] = PH_Z; break;
        default: break;
        }
        i++;
    }

    indic_emit_spr_word(out, phs, nph, 0, -1);
}

/* ---- Romanizer User Dictionary (UTF-8 key -> UTF-8 or SPR value) ----- */

typedef struct {
    char   *key;
    char   *val;
    int32_t pos;
} IndicDictEntry;

typedef struct {
    IndicDictEntry *items;
    size_t          count;
    size_t          cap;
    size_t          cursor;
} IndicUserDict;

static void indic_dict_free(IndicUserDict *d)
{
    size_t i;

    if (!d)
        return;
    for (i = 0; i < d->count; i++) {
        free(d->items[i].key);
        free(d->items[i].val);
    }
    free(d->items);
    free(d);
}

static int indic_key_eq(const char *a, const char *b, size_t len)
{
    size_t k;
    for (k = 0; k < len; k++) {
        unsigned char ca = (unsigned char)a[k];
        unsigned char cb = (unsigned char)b[k];
        if (ca >= 'A' && ca <= 'Z')
            ca = (unsigned char)(ca - 'A' + 'a');
        if (cb >= 'A' && cb <= 'Z')
            cb = (unsigned char)(cb - 'A' + 'a');
        if (ca != cb)
            return 0;
    }
    return 1;
}

static const IndicDictEntry *indic_dict_find(const IndicUserDict *d,
                                             const char *key, size_t klen)
{
    size_t i;

    if (!d || !key || klen == 0)
        return NULL;
    for (i = 0; i < d->count; i++) {
        if (strlen(d->items[i].key) == klen
            && indic_key_eq(d->items[i].key, key, klen))
            return &d->items[i];
    }
    return NULL;
}

static int indic_dict_put(IndicUserDict *d, const char *key, size_t klen,
                          const char *val, size_t vlen, int32_t pos)
{
    size_t i;
    char *kc, *vc;

    if (!d || !key || klen == 0)
        return -1;

    /* Deleting an entry when value is empty or NULL */
    if (!val || vlen == 0) {
        for (i = 0; i < d->count; i++) {
            if (strlen(d->items[i].key) == klen
                && indic_key_eq(d->items[i].key, key, klen)) {
                free(d->items[i].key);
                free(d->items[i].val);
                memmove(&d->items[i], &d->items[i + 1],
                        (d->count - i - 1) * sizeof d->items[0]);
                d->count--;
                return 0;
            }
        }
        return 0;
    }

    vc = (char *)malloc(vlen + 1);
    if (!vc)
        return -1;
    memcpy(vc, val, vlen);
    vc[vlen] = 0;

    for (i = 0; i < d->count; i++) {
        if (strlen(d->items[i].key) == klen
            && indic_key_eq(d->items[i].key, key, klen)) {
            free(d->items[i].val);
            d->items[i].val = vc;
            d->items[i].pos = pos;
            return 0;
        }
    }

    if (d->count == d->cap) {
        size_t ncap = d->cap ? d->cap * 2 : 32;
        IndicDictEntry *ni = (IndicDictEntry *)realloc(
            d->items, ncap * sizeof *ni);
        if (!ni) {
            free(vc);
            return -1;
        }
        d->items = ni;
        d->cap = ncap;
    }

    kc = (char *)malloc(klen + 1);
    if (!kc) {
        free(vc);
        return -1;
    }
    memcpy(kc, key, klen);
    kc[klen] = 0;

    d->items[d->count].key = kc;
    d->items[d->count].val = vc;
    d->items[d->count].pos = pos;
    d->count++;
    return 0;
}

/* ---- Romanizer Instance & Shared Sentence/Annotation Driver ---------- */

typedef struct IndicRom IndicRom;

typedef void (*IndicWordFn)(IndicRom *rom, IndicBuf *out,
                            const uint32_t *cps, size_t n);
typedef void (*IndicNumFn)(IndicRom *rom, IndicBuf *out,
                           const char *digits, size_t ndigits);
typedef const char *(*IndicAbbrevFn)(const char *tok, size_t len);
typedef const char *(*IndicSymFn)(uint32_t cp);

struct IndicRom {
    EvvRom          base;
    IndicBuf        pending;     /* queued UTF-8 text + inline ECI commands */
    IndicBuf        sentence;    /* last emitted SPR sentence */
    IndicBuf        conv;        /* owned conversion scratch (UCS2/MBCS) */
    IndicUserDict  *active_dict;
    int32_t         params[32];
    int             annotated;
    int             stopped;
    uint32_t        err_status;
    IndicWordFn     emit_dev_word;
    IndicNumFn      emit_integer;
    const char     *decimal_word;   /* "दशमलव" */
    const char     *percent_word;   /* "प्रतिशत" */
    const char     *minus_word;     /* "ऋण" / "माइनस" */
    const char     *plus_word;      /* "धन" / "प्लस" */
    const char     *rupee_sg;       /* "रुपया" / "रुपैयाँ" */
    const char     *rupee_pl;       /* "रुपये" / "रुपैयाँ" */
    IndicAbbrevFn   lookup_abbrev;
    IndicSymFn      lookup_symbol;
};

/* Forward declaration of recursive text converter (used on dict expansions
   and number/symbol word expansions, with `depth` guard against cycles). */
static void indic_convert_span(IndicRom *rom, IndicBuf *out,
                               const char *text, size_t len, int depth);

static void indic_space_before(IndicBuf *out)
{
    if (out->len > 0 && out->data[out->len - 1] != ' '
        && out->data[out->len - 1] != '\n')
        ibuf_putc(out, ' ');
}

static int indic_is_ascii_alpha(uint32_t cp)
{
    return (cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z');
}

/* Scan an inline ECI annotation starting at `s[at] == '`'` and return the
   index immediately past it. */
static size_t indic_scan_annotation(const char *s, size_t len, size_t at)
{
    size_t j = at + 1;

    if (j >= len)
        return j;
    if (s[j] == '[') {
        j++;
        while (j < len && s[j] != ']')
            j++;
        if (j < len && s[j] == ']')
            j++;
        return j;
    }
    while (j < len && (unsigned char)s[j] > ' ' && s[j] != '`') {
        if (s[j] == '[') {
            j++;
            while (j < len && s[j] != ']')
                j++;
            if (j < len && s[j] == ']')
                j++;
            break;
        }
        j++;
    }
    return j;
}

/* Copy an inline ECI annotation `s[at..end)` to `out`, decoding any 2-byte
   UTF-8 U+0080..U+00FF (`0xC2/0xC3 0x80..0xBF`) inside `[...]` SPR brackets
   back to its single SPR phoneme byte so callers encoding Strings as UTF-8
   (Android/Kotlin) and callers passing raw 1-byte SPR (C) both work. */
static void indic_emit_annotation(IndicBuf *out, const char *s,
                                  size_t at, size_t end)
{
    if (at + 1 < end && s[at] == '`' && s[at + 1] == '[') {
        size_t k = at;
        while (k < end) {
            unsigned char b0 = (unsigned char)s[k];
            if ((b0 == 0xc2 || b0 == 0xc3) && k + 1 < end
                && ((unsigned char)s[k + 1] & 0xc0) == 0x80) {
                unsigned char ph = (unsigned char)(((b0 & 0x03) << 6)
                                 | ((unsigned char)s[k + 1] & 0x3f));
                ibuf_putc(out, (char)ph);
                k += 2;
            } else {
                ibuf_putc(out, s[k]);
                k++;
            }
        }
    } else {
        ibuf_putn(out, s + at, end - at);
    }
}

/* Convert a span of UTF-8 text (possibly containing inline ECI annotations,
   Devanagari words, numbers, currencies, abbreviations, punctuation, and
   Latin words) into SPR + ECI commands in `out`. */
static void indic_convert_span(IndicRom *rom, IndicBuf *out,
                               const char *text, size_t len, int depth)
{
    size_t i = 0;

    if (depth > 4 || !text || len == 0)
        return;

    while (i < len) {
        size_t start = i;
        uint32_t cp;

        /* Internal sentinel \x01 + `... injected by addParam/insertIndex */
        if (text[i] == '\x01' && i + 1 < len && text[i + 1] == '`') {
            size_t end = indic_scan_annotation(text, len, i + 1);
            indic_space_before(out);
            indic_emit_annotation(out, text, i + 1, end);
            ibuf_putc(out, ' ');
            i = end;
            while (i < len && text[i] == ' ')
                i++;
            continue;
        }

        /* Escaped backtick \` -> literal */
        if (text[i] == '\\' && i + 1 < len && text[i + 1] == '`') {
            i += 2;
            continue;
        }

        /* Pass-through ECI annotation (`[...], `ui, `vbst+6, `p100, `pp1, etc.)
           when eciInputType != 0 (`rom->annotated`) or inside internal dict
           expansion (`depth > 0`). */
        if ((rom->annotated || depth > 0) && text[i] == '`' && i + 1 < len
            && (text[i + 1] == '[' || isalpha((unsigned char)text[i + 1])
                || text[i + 1] == '0' || text[i + 1] == '1')) {
            size_t end = indic_scan_annotation(text, len, i);
            indic_space_before(out);
            indic_emit_annotation(out, text, i, end);
            if (text[i + 1] != '[')
                ibuf_putc(out, ' ');
            i = end;
            while (i < len && text[i] == ' ')
                i++;
            continue;
        }

        cp = indic_utf8_next(text, len, &i);

        /* Whitespace */
        if (cp <= 0x20 || cp == 0x00a0 || cp == 0x200b || cp == 0xfeff) {
            if (out->len > 0 && out->data[out->len - 1] != ' ')
                ibuf_putc(out, ' ');
            continue;
        }

        /* Sentence & clause punctuation (including Devanagari danda । and ॥) */
        if (cp == 0x0964 || cp == 0x0965 || cp == '.') {
            ibuf_puts(out, ". ");
            continue;
        }
        if (cp == '?' || cp == '!' || cp == ',' || cp == ';' || cp == ':') {
            ibuf_putc(out, (char)cp);
            ibuf_putc(out, ' ');
            continue;
        }
        if (cp == 0x2013 || cp == 0x2014 || cp == '-' || cp == '(' || cp == ')'
            || cp == '"' || cp == '\'' || cp == 0x201c || cp == 0x201d
            || cp == 0x2018 || cp == 0x2019) {
            /* Minus sign directly before a digit -> spoken minus word */
            if ((cp == '-' || cp == 0x2212) && i < len) {
                size_t peek = i;
                uint32_t nx = indic_utf8_next(text, len, &peek);
                if (indic_dev_digit(nx) >= 0 && rom->minus_word) {
                    indic_space_before(out);
                    indic_convert_span(rom, out, rom->minus_word,
                                       strlen(rom->minus_word), depth + 1);
                    continue;
                }
            }
            if (cp == 0x2013 || cp == 0x2014)
                ibuf_puts(out, ", ");
            else if (out->len > 0 && out->data[out->len - 1] != ' ')
                ibuf_putc(out, ' ');
            continue;
        }

        /* Currency prefix ₹ (U+20B9) before a number -> "<number> रुपये/रुपैयाँ" */
        if (cp == 0x20b9) {
            size_t p2 = i;
            while (p2 < len && text[p2] == ' ')
                p2++;
            if (p2 < len) {
                size_t p3 = p2;
                uint32_t d0 = indic_utf8_next(text, len, &p3);
                if (indic_dev_digit(d0) >= 0) {
                    size_t nstart = p2, nend = p2;
                    while (nend < len) {
                        size_t tpos = nend;
                        uint32_t dc = indic_utf8_next(text, len, &tpos);
                        if (indic_dev_digit(dc) >= 0 || dc == ',' || dc == '.')
                            nend = tpos;
                        else
                            break;
                    }
                    indic_space_before(out);
                    indic_convert_span(rom, out, text + nstart,
                                       nend - nstart, depth + 1);
                    indic_space_before(out);
                    {
                        const char *rw = (nend - nstart == 1
                                          && text[nstart] == '1')
                                       ? rom->rupee_sg : rom->rupee_pl;
                        indic_convert_span(rom, out, rw, strlen(rw), depth + 1);
                    }
                    i = nend;
                    continue;
                }
            }
            indic_space_before(out);
            indic_convert_span(rom, out, rom->rupee_pl,
                               strlen(rom->rupee_pl), depth + 1);
            continue;
        }

        /* Numbers (Devanagari ०-९ and ASCII 0-9, with commas and decimals) */
        if (indic_dev_digit(cp) >= 0) {
            char int_digits[64];
            char frac_digits[64];
            size_t ni = 0, nf = 0;
            int has_dot = 0;
            size_t cur = start;

            while (cur < len) {
                size_t next_cur = cur;
                uint32_t dc = indic_utf8_next(text, len, &next_cur);
                int dv = indic_dev_digit(dc);

                if (dv >= 0) {
                    if (!has_dot) {
                        if (ni < sizeof int_digits - 1)
                            int_digits[ni++] = (char)('0' + dv);
                    } else {
                        if (nf < sizeof frac_digits - 1)
                            frac_digits[nf++] = (char)('0' + dv);
                    }
                    cur = next_cur;
                } else if (dc == ',' && !has_dot && next_cur < len) {
                    size_t p2 = next_cur;
                    uint32_t d2 = indic_utf8_next(text, len, &p2);
                    if (indic_dev_digit(d2) >= 0) {
                        cur = next_cur;
                        continue;
                    }
                    break;
                } else if (dc == '.' && !has_dot && next_cur < len) {
                    size_t p2 = next_cur;
                    uint32_t d2 = indic_utf8_next(text, len, &p2);
                    if (indic_dev_digit(d2) >= 0) {
                        has_dot = 1;
                        cur = next_cur;
                        continue;
                    }
                    break;
                } else {
                    break;
                }
            }
            int_digits[ni] = 0;
            frac_digits[nf] = 0;

            indic_space_before(out);
            rom->emit_integer(rom, out, int_digits, ni);
            if (has_dot && nf > 0) {
                size_t k;
                indic_space_before(out);
                indic_convert_span(rom, out, rom->decimal_word,
                                   strlen(rom->decimal_word), depth + 1);
                for (k = 0; k < nf; k++) {
                    char one[2];
                    one[0] = frac_digits[k];
                    one[1] = 0;
                    indic_space_before(out);
                    rom->emit_integer(rom, out, one, 1);
                }
            }
            /* Trailing % right after number */
            if (cur < len && text[cur] == '%' && rom->percent_word) {
                indic_space_before(out);
                indic_convert_span(rom, out, rom->percent_word,
                                   strlen(rom->percent_word), depth + 1);
                cur++;
            }
            i = cur;
            continue;
        }

        /* Devanagari word (including digits/abbreviation dots attached) */
        if (indic_is_devanagari(cp) && cp != 0x0964 && cp != 0x0965) {
            uint32_t wcps[128];
            size_t nw = 0;
            size_t end_byte = i;

            wcps[nw++] = cp;
            while (end_byte < len) {
                size_t nx_pos = end_byte;
                uint32_t ncp = indic_utf8_next(text, len, &nx_pos);
                if ((indic_is_devanagari(ncp) && ncp != 0x0964 && ncp != 0x0965
                     && indic_dev_digit(ncp) < 0 && ncp != 0x0970)
                    || ncp == 0x200c || ncp == 0x200d) {
                    if (nw < 126)
                        wcps[nw++] = ncp;
                    end_byte = nx_pos;
                } else {
                    break;
                }
            }

            /* Check abbreviation with trailing '.' or '॰' (U+0970), e.g., डॉ., रु., वि.सं. */
            if (rom->lookup_abbrev && end_byte < len) {
                size_t p2 = end_byte;
                uint32_t dcp = indic_utf8_next(text, len, &p2);
                if (dcp == '.' || dcp == 0x0970) {
                    const char *exp = rom->lookup_abbrev(text + start,
                                                         p2 - start);
                    if (exp) {
                        indic_space_before(out);
                        indic_convert_span(rom, out, exp, strlen(exp),
                                           depth + 1);
                        i = p2;
                        continue;
                    }
                }
            }

            /* User dictionary lookup on the exact UTF-8 token */
            if (depth == 0 && rom->active_dict) {
                const IndicDictEntry *de = indic_dict_find(
                    rom->active_dict, text + start, end_byte - start);
                if (de && de->val) {
                    indic_space_before(out);
                    if (de->val[0] == '`' && de->val[1] == '[') {
                        indic_emit_annotation(out, de->val, 0, strlen(de->val));
                    } else {
                        indic_convert_span(rom, out, de->val,
                                           strlen(de->val), depth + 1);
                    }
                    i = end_byte;
                    continue;
                }
            }

            nw = indic_normalize_cps(wcps, nw);
            if (nw > 0) {
                indic_space_before(out);
                rom->emit_dev_word(rom, out, wcps, nw);
            }
            i = end_byte;
            continue;
        }

        /* Latin word */
        if (indic_is_ascii_alpha(cp)) {
            size_t end_byte = i;
            while (end_byte < len && indic_is_ascii_alpha((unsigned char)text[end_byte]))
                end_byte++;

            if (depth == 0 && rom->active_dict) {
                const IndicDictEntry *de = indic_dict_find(
                    rom->active_dict, text + start, end_byte - start);
                if (de && de->val) {
                    indic_space_before(out);
                    if (de->val[0] == '`' && de->val[1] == '[') {
                        indic_emit_annotation(out, de->val, 0, strlen(de->val));
                    } else {
                        indic_convert_span(rom, out, de->val,
                                           strlen(de->val), depth + 1);
                    }
                    i = end_byte;
                    continue;
                }
            }

            indic_space_before(out);
            indic_emit_latin_word(out, text + start, end_byte - start);
            i = end_byte;
            continue;
        }

        /* Spoken symbols (+, %, =, /, @, &, *, #) */
        if (cp == '%' && rom->percent_word) {
            indic_space_before(out);
            indic_convert_span(rom, out, rom->percent_word,
                               strlen(rom->percent_word), depth + 1);
            continue;
        }
        if (cp == '+' && rom->plus_word) {
            indic_space_before(out);
            indic_convert_span(rom, out, rom->plus_word,
                               strlen(rom->plus_word), depth + 1);
            continue;
        }
        if (rom->lookup_symbol) {
            const char *sw = rom->lookup_symbol(cp);
            if (sw) {
                indic_space_before(out);
                indic_convert_span(rom, out, sw, strlen(sw), depth + 1);
                continue;
            }
        }
    }
}

/* Find the end of the first complete sentence in `s[0..len)`, or 0 if none. */
static size_t indic_find_sentence_end(IndicRom *rom, const char *s, size_t len)
{
    size_t i = 0;

    while (i < len) {
        size_t cur = i;
        uint32_t cp;

        if (s[i] == '\x01' && i + 1 < len && s[i + 1] == '`') {
            i = indic_scan_annotation(s, len, i + 1);
            continue;
        }
        if (rom->annotated && s[i] == '`') {
            i = indic_scan_annotation(s, len, i);
            continue;
        }
        cp = indic_utf8_next(s, len, &i);
        if (cp == '\n' || cp == 0x0964 || cp == 0x0965
            || cp == '?' || cp == '!') {
            return i;
        }
        if (cp == '.') {
            /* Do not split inside decimal numbers (3.14) or known abbreviations */
            if (cur > 0 && i < len) {
                size_t p2 = i;
                uint32_t nx = indic_utf8_next(s, len, &p2);
                if (indic_dev_digit((unsigned char)s[cur - 1]) >= 0
                    && indic_dev_digit(nx) >= 0)
                    continue;
            }
            if (rom->lookup_abbrev && cur > 0) {
                size_t ws = cur;
                while (ws > 0 && (unsigned char)s[ws - 1] > ' ')
                    ws--;
                if (rom->lookup_abbrev(s + ws, i - ws))
                    continue;
            }
            return i;
        }
    }
    return 0;
}

/* ---- EvvRomOps callbacks --------------------------------------------- */

static void indic_rom_release(EvvRom *r)
{
    IndicRom *rom = (IndicRom *)r;

    if (!rom)
        return;
    ibuf_free(&rom->pending);
    ibuf_free(&rom->sentence);
    ibuf_free(&rom->conv);
    free(rom);
}

static int32_t indic_rom_addText(EvvRom *r, const char *text, int32_t len,
                                 int32_t flag)
{
    IndicRom *rom = (IndicRom *)r;
    size_t n;

    if (!rom || rom->stopped)
        return 1;
    if (flag)
        rom->annotated = 1;
    else if (rom->pending.len == 0)
        rom->annotated = 0;
    if (!text)
        return 1;
    n = (len < 0) ? strlen(text) : (size_t)len;
    if (n == 0)
        return 1;
    if (!ibuf_putn(&rom->pending, text, n)) {
        rom->err_status = 1;
        return -1;
    }
    return 1;
}

static int32_t indic_rom_addParam(EvvRom *r, const char *text, int32_t len)
{
    IndicRom *rom = (IndicRom *)r;
    size_t n;

    if (!rom || !text)
        return 1;
    n = (len < 0) ? strlen(text) : (size_t)len;
    if (n == 0)
        return 1;
    indic_space_before(&rom->pending);
    if (text[0] == '`' && !ibuf_putc(&rom->pending, '\x01'))
        return -1;
    if (!ibuf_putn(&rom->pending, text, n))
        return -1;
    ibuf_putc(&rom->pending, ' ');
    return 1;
}

static int32_t indic_rom_insertIndex(EvvRom *r)
{
    IndicRom *rom = (IndicRom *)r;

    if (!rom)
        return 1;
    indic_space_before(&rom->pending);
    if (!ibuf_puts(&rom->pending, "\x01`ui "))
        return -1;
    return 1;
}

static int32_t indic_rom_processSentence(EvvRom *r, char **out,
                                         int32_t flush)
{
    IndicRom *rom = (IndicRom *)r;
    size_t cut;

    if (!out)
        return 0;
    *out = NULL;
    if (!rom || rom->stopped || rom->pending.len == 0)
        return 0;

    for (;;) {
        if (rom->pending.len == 0)
            return 0;
        cut = indic_find_sentence_end(rom, rom->pending.data, rom->pending.len);
        if (cut == 0) {
            if (!flush)
                return 0;
            cut = rom->pending.len;
        }

        ibuf_clear(&rom->sentence);
        indic_convert_span(rom, &rom->sentence, rom->pending.data, cut, 0);

        /* Consume the processed prefix from `pending` */
        if (cut < rom->pending.len) {
            memmove(rom->pending.data, rom->pending.data + cut,
                    rom->pending.len - cut + 1);
            rom->pending.len -= cut;
        } else {
            ibuf_clear(&rom->pending);
        }

        /* Skip empty/whitespace-only conversions and try next chunk */
        if (rom->sentence.len > 0) {
            size_t k = 0;
            while (k < rom->sentence.len && rom->sentence.data[k] == ' ')
                k++;
            if (k < rom->sentence.len) {
                /* Ensure sentence ends with a clause boundary or space so the
                   engine synthesizes cleanly across pieces. */
                char tail = rom->sentence.data[rom->sentence.len - 1];
                if (flush && rom->pending.len == 0
                    && tail != '.' && tail != '?' && tail != '!' && tail != ' ')
                    ibuf_puts(&rom->sentence, ". ");
                else if (tail != ' ')
                    ibuf_putc(&rom->sentence, ' ');
                *out = rom->sentence.data;
                return 2;
            }
        }
    }
}

static int32_t indic_rom_stop(EvvRom *r)
{
    IndicRom *rom = (IndicRom *)r;

    if (!rom)
        return 1;
    rom->stopped = 1;
    ibuf_clear(&rom->pending);
    return 1;
}

static int32_t indic_rom_resume(EvvRom *r)
{
    IndicRom *rom = (IndicRom *)r;

    if (!rom)
        return 1;
    rom->stopped = 0;
    return 1;
}

static int32_t indic_rom_UCS2ToMBCS(EvvRom *r, const uint16_t *in,
                                    char **out, int32_t n)
{
    IndicRom *rom = (IndicRom *)r;
    int32_t i;

    if (!rom || !in || !out)
        return 0;
    ibuf_clear(&rom->conv);
    if (n < 0) {
        for (n = 0; in[n] != 0; n++)
            ;
    }
    for (i = 0; i < n; i++) {
        uint32_t u = in[i];
        if (u >= 0xd800 && u <= 0xdbff && i + 1 < n
            && in[i + 1] >= 0xdc00 && in[i + 1] <= 0xdfff) {
            u = 0x10000 + (((u - 0xd800) << 10) | (in[i + 1] - 0xdc00));
            i++;
        }
        indic_utf8_encode(&rom->conv, u);
    }
    if (!ibuf_reserve(&rom->conv, 1))
        return 0;
    *out = rom->conv.data;
    return 1;
}

static int32_t indic_rom_setParam(EvvRom *r, int32_t which, int32_t value)
{
    IndicRom *rom = (IndicRom *)r;
    int32_t prev = 0;

    if (!rom)
        return 0;
    if (which >= 0 && which < 32) {
        prev = rom->params[which];
        rom->params[which] = value;
    }
    return prev;
}

static int32_t indic_rom_getParam(EvvRom *r, int32_t which)
{
    IndicRom *rom = (IndicRom *)r;

    if (!rom || which < 0 || which >= 32)
        return 0;
    return rom->params[which];
}

static void indic_rom_clearErrors(EvvRom *r)
{
    IndicRom *rom = (IndicRom *)r;
    if (rom)
        rom->err_status = 0;
}

static uint32_t indic_rom_progStatus(EvvRom *r)
{
    IndicRom *rom = (IndicRom *)r;
    return rom ? rom->err_status : 0;
}

static void indic_rom_errorMessage(EvvRom *r, char *out)
{
    IndicRom *rom = (IndicRom *)r;
    if (!out)
        return;
    if (rom && rom->err_status)
        strcpy(out, "Romanizer out of memory");
    else
        strcpy(out, "No Romanizer Error");
}

static void *indic_rom_newDict(EvvRom *r)
{
    (void)r;
    return calloc(1, sizeof(IndicUserDict));
}

static void indic_rom_deleteDict(EvvRom *r, void *dict)
{
    IndicRom *rom = (IndicRom *)r;
    IndicUserDict *d = (IndicUserDict *)dict;

    if (rom && rom->active_dict == d)
        rom->active_dict = NULL;
    indic_dict_free(d);
}

static void indic_rom_setDict(EvvRom *r, void *dict)
{
    IndicRom *rom = (IndicRom *)r;
    if (rom)
        rom->active_dict = (IndicUserDict *)dict;
}

static int32_t indic_rom_loadDict(EvvRom *r, void *dict, int32_t which,
                                  const char *name)
{
    IndicUserDict *d = (IndicUserDict *)dict;
    FILE *f;
    char line[1024];

    (void)r;
    (void)which;
    if (!d || !name)
        return 1;
    f = fopen(name, "rb");
    if (!f)
        return 1;
    while (fgets(line, (int)sizeof line, f)) {
        char *tab;
        size_t len = strlen(line);
        while (len > 0 && (line[len - 1] == '\n' || line[len - 1] == '\r'))
            line[--len] = 0;
        tab = strchr(line, '\t');
        if (!tab || tab == line)
            continue;
        *tab = 0;
        indic_dict_put(d, line, (size_t)(tab - line),
                       tab + 1, strlen(tab + 1), 0);
    }
    fclose(f);
    return 0;
}

static int32_t indic_rom_saveDict(EvvRom *r, void *dict, int32_t which,
                                  const char *name)
{
    IndicUserDict *d = (IndicUserDict *)dict;
    FILE *f;
    size_t i;

    (void)r;
    (void)which;
    if (!d || !name)
        return 1;
    f = fopen(name, "wb");
    if (!f)
        return 1;
    for (i = 0; i < d->count; i++)
        fprintf(f, "%s\t%s\n", d->items[i].key, d->items[i].val);
    fclose(f);
    return 0;
}

static int32_t indic_rom_lookupDictExt(EvvRom *r, void *dict, int32_t which,
                                       void *word, int32_t wordLen,
                                       void **value, int32_t *valueLen,
                                       int32_t *pos, int32_t codeset)
{
    const IndicDictEntry *e;
    size_t klen;

    (void)r;
    (void)which;
    (void)codeset;
    if (!dict || !word || !value || !valueLen)
        return 1;
    klen = (wordLen > 0) ? strnlen((const char *)word, (size_t)wordLen)
                         : strlen((const char *)word);
    e = indic_dict_find((const IndicUserDict *)dict, (const char *)word, klen);
    if (!e)
        return 1;
    *value = e->val;
    *valueLen = (int32_t)strlen(e->val) + 1;
    if (pos)
        *pos = e->pos;
    return 0;
}

static int32_t indic_rom_updateDictExt(EvvRom *r, void *dict, int32_t which,
                                       void *word, int32_t wordLen,
                                       void *value, int32_t valueLen,
                                       int32_t pos, int32_t codeset)
{
    size_t klen, vlen = 0;

    (void)r;
    (void)which;
    (void)codeset;
    if (!dict || !word)
        return 1;
    klen = (wordLen > 0) ? strnlen((const char *)word, (size_t)wordLen)
                         : strlen((const char *)word);
    if (value)
        vlen = (valueLen > 0) ? strnlen((const char *)value, (size_t)valueLen)
                              : strlen((const char *)value);
    return indic_dict_put((IndicUserDict *)dict, (const char *)word, klen,
                          (const char *)value, vlen, pos) == 0 ? 0 : 1;
}

static int32_t indic_rom_findFirstDictExt(EvvRom *r, void *dict, int32_t which,
                                          void **key, int32_t *keyLen,
                                          void **value, int32_t *valueLen,
                                          int32_t *pos, int32_t codeset)
{
    IndicUserDict *d = (IndicUserDict *)dict;

    (void)r;
    (void)which;
    (void)codeset;
    if (!d || !key || !keyLen || !value || !valueLen || d->count == 0)
        return 1;
    d->cursor = 1;
    *key = d->items[0].key;
    *keyLen = (int32_t)strlen(d->items[0].key) + 1;
    *value = d->items[0].val;
    *valueLen = (int32_t)strlen(d->items[0].val) + 1;
    if (pos)
        *pos = d->items[0].pos;
    return 0;
}

static int32_t indic_rom_findNextDictExt(EvvRom *r, void *dict, int32_t which,
                                         void **key, int32_t *keyLen,
                                         void **value, int32_t *valueLen,
                                         int32_t *pos, int32_t codeset)
{
    IndicUserDict *d = (IndicUserDict *)dict;
    size_t idx;

    (void)r;
    (void)which;
    (void)codeset;
    if (!d || !key || !keyLen || !value || !valueLen || d->cursor >= d->count)
        return 1;
    idx = d->cursor++;
    *key = d->items[idx].key;
    *keyLen = (int32_t)strlen(d->items[idx].key) + 1;
    *value = d->items[idx].val;
    *valueLen = (int32_t)strlen(d->items[idx].val) + 1;
    if (pos)
        *pos = d->items[idx].pos;
    return 0;
}

static int32_t indic_rom_identityConv(EvvRom *r, const char *in, char **out)
{
    IndicRom *rom = (IndicRom *)r;
    if (!rom || !in || !out)
        return 0;
    ibuf_clear(&rom->conv);
    if (!ibuf_puts(&rom->conv, in))
        return 0;
    *out = rom->conv.data;
    return 1;
}

static const EvvRomOps indic_rom_ops = {
    indic_rom_release,
    indic_rom_addText,
    indic_rom_insertIndex,
    indic_rom_processSentence,
    indic_rom_stop,
    indic_rom_resume,
    indic_rom_UCS2ToMBCS,
    indic_rom_setParam,
    indic_rom_getParam,
    indic_rom_clearErrors,
    indic_rom_progStatus,
    indic_rom_errorMessage,
    indic_rom_addParam,
    indic_rom_newDict,
    indic_rom_deleteDict,
    indic_rom_setDict,
    indic_rom_loadDict,
    indic_rom_saveDict,
    indic_rom_lookupDictExt,
    indic_rom_updateDictExt,
    indic_rom_findFirstDictExt,
    indic_rom_findNextDictExt,
    indic_rom_identityConv,
    indic_rom_identityConv
};

#endif /* EVV_INDIC_ROM_H */
