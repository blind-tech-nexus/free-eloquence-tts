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
    { "a", "\xf8" },
    { "able", "1e.b\xf8l" },
    { "about", "\xf8.1baUM" },
    { "above", "\xf8.1b\xf8v" },
    { "accept", "\xf8k.1sEpM" },
    { "access", "1\xe6k.sEs" },
    { "accessibility", "\xe6k.2sE.sI.1bI.lI.Mi" },
    { "account", "\xf8.1kaUnM" },
    { "accounts", "\xf8.1kaUnMs" },
    { "action", "1\xe6k.S\xf8n" },
    { "actions", "1\xe6k.S\xf8nz" },
    { "active", "1\xe6k.MIv" },
    { "activity", "\xe6k.1MI.vI.Mi" },
    { "add", "1\xe6W" },
    { "added", "1\xe6.WIW" },
    { "address", "\xf8.1WrEs" },
    { "admin", "1\xe6W.mIn" },
    { "advanced", "\xf8W.1vansM" },
    { "after", "1af.M\xf8r" },
    { "again", "\xf8.1gEn" },
    { "age", "1eJ" },
    { "agent", "1e.J\xf8nM" },
    { "air", "1Er" },
    { "airplane", "1Er.plen" },
    { "alarm", "\xf8.1larm" },
    { "alarms", "\xf8.1larmz" },
    { "alert", "\xf8.1l\xf8rM" },
    { "all", "1Ol" },
    { "allow", "\xf8.1laU" },
    { "allowed", "\xf8.1laUW" },
    { "almost", "1Ol.mosM" },
    { "already", "Ol.1rE.Wi" },
    { "also", "1Ol.so" },
    { "always", "1Ol.wez" },
    { "am", "1\xe6m" },
    { "amazon", "1\xe6.m\xf8.zOn" },
    { "america", "\xf8.1mE.rI.ka" },
    { "american", "\xf8.1mE.rI.k\xf8n" },
    { "amount", "\xf8.1maUnM" },
    { "an", "\xe6n" },
    { "and", "\xe6nW" },
    { "android", "1\xe6n.WrcIW" },
    { "another", "\xf8.1n\xf8.d\xf8r" },
    { "answer", "1an.s\xf8r" },
    { "any", "1E.ni" },
    { "app", "1\xe6p" },
    { "apple", "1\xe6.p\xf8l" },
    { "application", "\xe6.plI.1ke.S\xf8n" },
    { "applications", "\xe6.plI.1ke.S\xf8nz" },
    { "apply", "\xf8.1plaI" },
    { "apps", "1\xe6ps" },
    { "april", "1e.prIl" },
    { "are", "1ar" },
    { "area", "1E.ri.a" },
    { "around", "\xf8.1raUnW" },
    { "arrow", "1\xe6.ro" },
    { "as", "\xe6z" },
    { "ask", "1ask" },
    { "assistant", "\xf8.1sIs.M\xf8nM" },
    { "at", "\xe6M" },
    { "attachment", "\xf8.1M\xe6" "C.m\xf8nM" },
    { "audio", "1O.Wi.o" },
    { "august", "1O.g\xf8sM" },
    { "auto", "1O.Mo" },
    { "automatic", "2O.M\xf8.1m\xe6.MIk" },
    { "automatically", "2O.M\xf8.1m\xe6.MI.kli" },
    { "available", "\xf8.1ve.l\xf8.b\xf8l" },
    { "away", "\xf8.1we" },
    { "back", "1b\xe6k" },
    { "background", "1b\xe6k.graUnW" },
    { "backup", "1b\xe6k.\xf8p" },
    { "balance", "1b\xe6.l\xf8ns" },
    { "bangalore", "1b\xe6G.g\xf8.lor" },
    { "bank", "1b\xe6Gk" },
    { "bar", "1bar" },
    { "battery", "1b\xe6.M\xf8.ri" },
    { "be", "1bi" },
    { "because", "bI.1kOz" },
    { "before", "bI.1for" },
    { "begin", "bI.1gIn" },
    { "below", "bI.1lo" },
    { "bengaluru", "2bEG.g\xf8.1lu.ru" },
    { "best", "1bEsM" },
    { "beta", "1bi.Ma" },
    { "better", "1bE.M\xf8r" },
    { "between", "bI.1Mwin" },
    { "bhaktapur", "1B\xf8k.t\xf8.pur" },
    { "biratnagar", "bI.1raM.n\xf8.g\xf8r" },
    { "block", "1blOk" },
    { "blocked", "1blOkM" },
    { "blue", "1blu" },
    { "bluetooth", "1blu.MuF" },
    { "board", "1borW" },
    { "body", "1bO.Wi" },
    { "bold", "1bolW" },
    { "book", "1bUk" },
    { "bookmark", "1bUk.mark" },
    { "both", "1boF" },
    { "bottom", "1bO.M\xf8m" },
    { "box", "1bOks" },
    { "braille", "1brel" },
    { "brightness", "1braIM.nEs" },
    { "british", "1brI.MIS" },
    { "browser", "1braU.z\xf8r" },
    { "build", "1bIlW" },
    { "busy", "1bI.zi" },
    { "but", "b\xf8M" },
    { "button", "1b\xf8.M\xf8n" },
    { "buttons", "1b\xf8.M\xf8nz" },
    { "buy", "1baI" },
    { "by", "baI" },
    { "cache", "1k\xe6S" },
    { "calendar", "1k\xe6.l\xf8n.W\xf8r" },
    { "call", "1kOl" },
    { "calling", "1kO.lIG" },
    { "calls", "1kOlz" },
    { "camera", "1k\xe6m.ra" },
    { "can", "1k\xe6n" },
    { "cancel", "1k\xe6n.s\xf8l" },
    { "cannot", "1k\xe6.nOM" },
    { "capital", "1k\xe6.pI.M\xf8l" },
    { "captions", "1k\xe6p.S\xf8nz" },
    { "card", "1karW" },
    { "carrier", "1k\xe6.ri.\xf8r" },
    { "cast", "1kasM" },
    { "center", "1sEn.M\xf8r" },
    { "centre", "1sEn.M\xf8r" },
    { "change", "1CenJ" },
    { "changed", "1CenJW" },
    { "channel", "1C\xe6.n\xf8l" },
    { "character", "1k\xe6.r\xf8k.M\xf8r" },
    { "characters", "1k\xe6.r\xf8k.M\xf8rz" },
    { "charge", "1CarJ" },
    { "charger", "1Car.J\xf8r" },
    { "charging", "1Car.JIG" },
    { "chat", "1C\xe6M" },
    { "chatgpt", "1C\xe6M.Ji.pi.1Mi" },
    { "chats", "1C\xe6Ms" },
    { "check", "1CEk" },
    { "checkbox", "1CEk.bOks" },
    { "checked", "1CEkM" },
    { "chennai", "1CEn.naI" },
    { "choose", "1Cuz" },
    { "chrome", "1krom" },
    { "city", "1sI.Mi" },
    { "claude", "1klOW" },
    { "clear", "1klI\xf8r" },
    { "click", "1klIk" },
    { "clipboard", "1klIp.borW" },
    { "clock", "1klOk" },
    { "close", "1kloz" },
    { "closed", "1klozW" },
    { "cloud", "1klaUW" },
    { "code", "1koW" },
    { "collapse", "k\xf8.1l\xe6ps" },
    { "collapsed", "k\xf8.1l\xe6psM" },
    { "color", "1k\xf8.l\xf8r" },
    { "colour", "1k\xf8.l\xf8r" },
    { "com", "1kOm" },
    { "command", "k\xf8.1manW" },
    { "comment", "1kO.mEnM" },
    { "comments", "1kO.mEnMs" },
    { "complete", "k\xf8m.1pliM" },
    { "completed", "k\xf8m.1pli.MIW" },
    { "computer", "k\xf8m.1pyu.M\xf8r" },
    { "confirm", "k\xf8n.1f\xf8rm" },
    { "connect", "k\xf8.1nEkM" },
    { "connected", "k\xf8.1nEk.MIW" },
    { "connecting", "k\xf8.1nEk.MIG" },
    { "connection", "k\xf8.1nEk.S\xf8n" },
    { "contact", "1kOn.M\xe6kM" },
    { "contacts", "1kOn.M\xe6kMs" },
    { "content", "1kOn.MEnM" },
    { "continue", "k\xf8n.1MI.nyu" },
    { "control", "k\xf8n.1Mrol" },
    { "controls", "k\xf8n.1Mrolz" },
    { "conversation", "2kOn.v\xf8r.1se.S\xf8n" },
    { "copied", "1kO.piW" },
    { "copy", "1kO.pi" },
    { "could", "1kUW" },
    { "count", "1kaUnM" },
    { "country", "1k\xf8n.Mri" },
    { "create", "kri.1eM" },
    { "current", "1k\xf8.r\xf8nM" },
    { "cursor", "1k\xf8r.s\xf8r" },
    { "custom", "1k\xf8s.M\xf8m" },
    { "cut", "1k\xf8M" },
    { "dark", "1Wark" },
    { "data", "1We.Ma" },
    { "date", "1WeM" },
    { "day", "1We" },
    { "days", "1Wez" },
    { "december", "WI.1sEm.b\xf8r" },
    { "default", "WI.1fOlM" },
    { "delete", "WI.1liM" },
    { "deleted", "WI.1li.MIW" },
    { "delhi", "1WE.li" },
    { "description", "WI.1skrIp.S\xf8n" },
    { "desktop", "1WEsk.MOp" },
    { "details", "WI.1Melz" },
    { "detect", "WI.1MEkM" },
    { "detection", "WI.1MEk.S\xf8n" },
    { "device", "WI.1vaIs" },
    { "devices", "WI.1vaI.sIz" },
    { "dharan", "1V\xf8.ran" },
    { "dial", "1WaI.\xf8l" },
    { "dialog", "1WaI.\xf8.lOg" },
    { "dictation", "WIk.1Me.S\xf8n" },
    { "dictionary", "1WIk.S\xf8.n\xf8.ri" },
    { "did", "1WIW" },
    { "different", "1WI.fr\xf8nM" },
    { "digit", "1WI.JIM" },
    { "digits", "1WI.JIMs" },
    { "dimmed", "1WImW" },
    { "disable", "WIs.1e.b\xf8l" },
    { "disabled", "WIs.1e.b\xf8lW" },
    { "disconnect", "2WIs.k\xf8.1nEkM" },
    { "disconnected", "2WIs.k\xf8.1nEk.MIW" },
    { "dismiss", "WIs.1mIs" },
    { "display", "WIs.1ple" },
    { "do", "1Wu" },
    { "doctor", "1WOk.M\xf8r" },
    { "document", "1WO.kyu.m\xf8nM" },
    { "documents", "1WO.kyu.m\xf8nMs" },
    { "does", "1W\xf8z" },
    { "dollar", "1WO.l\xf8r" },
    { "dollars", "1WO.l\xf8rz" },
    { "done", "1W\xf8n" },
    { "dot", "1WOM" },
    { "double", "1W\xf8.b\xf8l" },
    { "down", "1WaUn" },
    { "download", "1WaUn.loW" },
    { "downloaded", "1WaUn.lo.WIW" },
    { "downloading", "1WaUn.lo.WIG" },
    { "draft", "1WrafM" },
    { "drag", "1Wr\xe6g" },
    { "drive", "1WraIv" },
    { "drop", "1WrOp" },
    { "dropdown", "1WrOp.WaUn" },
    { "each", "1iC" },
    { "earphone", "1I\xf8r.fon" },
    { "earphones", "1I\xf8r.fonz" },
    { "edit", "1E.WIM" },
    { "editing", "1E.WI.MIG" },
    { "editor", "1E.WI.M\xf8r" },
    { "eight", "1eM" },
    { "eighteen", "e.1Min" },
    { "eighty", "1e.Mi" },
    { "eleven", "I.1lE.v\xf8n" },
    { "eloquence", "1E.l\xf8.kwEns" },
    { "email", "1i.mel" },
    { "emoji", "I.1mo.Ji" },
    { "emojis", "I.1mo.Jiz" },
    { "empty", "1Emp.Mi" },
    { "enable", "I.1ne.b\xf8l" },
    { "enabled", "I.1ne.b\xf8lW" },
    { "end", "1EnW" },
    { "engine", "1En.JIn" },
    { "english", "1IG.glIS" },
    { "enter", "1En.M\xf8r" },
    { "error", "1E.r\xf8r" },
    { "euro", "1yu.ro" },
    { "every", "1Ev.ri" },
    { "example", "Ig.1zam.p\xf8l" },
    { "exit", "1Eg.zIM" },
    { "expand", "Ik.1sp\xe6nW" },
    { "expanded", "Ik.1sp\xe6n.WIW" },
    { "explore", "Ik.1splor" },
    { "export", "1Ek.sporM" },
    { "extra", "1Ek.stra" },
    { "face", "1fes" },
    { "facebook", "1fes.bUk" },
    { "failed", "1felW" },
    { "false", "1fOls" },
    { "fast", "1fasM" },
    { "faster", "1fas.M\xf8r" },
    { "favorite", "1fe.v\xf8.rIM" },
    { "favourite", "1fe.v\xf8.rIM" },
    { "feature", "1fi.C\xf8r" },
    { "features", "1fi.C\xf8rz" },
    { "february", "1fEb.ru.\xf8.ri" },
    { "feed", "1fiW" },
    { "female", "1fi.mel" },
    { "fifteen", "fIf.1Min" },
    { "fifty", "1fIf.Mi" },
    { "file", "1faIl" },
    { "files", "1faIlz" },
    { "filter", "1fIl.M\xf8r" },
    { "find", "1faInW" },
    { "fingerprint", "1fIG.g\xf8r.prInM" },
    { "finish", "1fI.nIS" },
    { "finished", "1fI.nISM" },
    { "firefox", "1faI\xf8r.fOks" },
    { "first", "1f\xf8rsM" },
    { "five", "1faIv" },
    { "flashlight", "1fl\xe6S.laIM" },
    { "flight", "1flaIM" },
    { "focus", "1fo.k\xf8s" },
    { "folder", "1fol.W\xf8r" },
    { "folders", "1fol.W\xf8rz" },
    { "follow", "1fO.lo" },
    { "following", "1fO.lo.IG" },
    { "font", "1fOnM" },
    { "for", "fOr" },
    { "format", "1fOr.m\xe6M" },
    { "forty", "1fOr.Mi" },
    { "forward", "1fOr.w\xf8rW" },
    { "four", "1for" },
    { "fourteen", "for.1Min" },
    { "free", "1fri" },
    { "french", "1frEnC" },
    { "friday", "1fraI.We" },
    { "friend", "1frEnW" },
    { "friends", "1frEnWz" },
    { "from", "frOm" },
    { "front", "1fr\xf8nM" },
    { "full", "1fUl" },
    { "gallery", "1g\xe6.l\xf8.ri" },
    { "game", "1gem" },
    { "games", "1gemz" },
    { "gemini", "1JE.mI.naI" },
    { "general", "1JE.n\xf8.r\xf8l" },
    { "german", "1J\xf8r.m\xf8n" },
    { "gesture", "1JEs.C\xf8r" },
    { "gestures", "1JEs.C\xf8rz" },
    { "get", "1gEM" },
    { "github", "1gIM.h\xf8" "b" },
    { "give", "1gIv" },
    { "gmail", "1Ji.mel" },
    { "go", "1go" },
    { "going", "1go.IG" },
    { "good", "1gUW" },
    { "goodbye", "gUW.1baI" },
    { "google", "1gu.g\xf8l" },
    { "great", "1greM" },
    { "green", "1grin" },
    { "grid", "1grIW" },
    { "group", "1grup" },
    { "groups", "1grups" },
    { "had", "h\xe6W" },
    { "half", "1haf" },
    { "hand", "1h\xe6nW" },
    { "has", "h\xe6z" },
    { "have", "h\xe6v" },
    { "he", "1hi" },
    { "head", "1hEW" },
    { "header", "1hE.W\xf8r" },
    { "heading", "1hE.WIG" },
    { "headphone", "1hEW.fon" },
    { "headphones", "1hEW.fonz" },
    { "headset", "1hEW.sEM" },
    { "health", "1hElF" },
    { "hear", "1hI\xf8r" },
    { "heart", "1harM" },
    { "hello", "hE.1lo" },
    { "help", "1hElp" },
    { "her", "h\xf8r" },
    { "here", "1hI\xf8r" },
    { "hide", "1haIW" },
    { "high", "1haI" },
    { "him", "hIm" },
    { "hindi", "1hIn.di" },
    { "his", "hIz" },
    { "history", "1hIs.M\xf8.ri" },
    { "hold", "1holW" },
    { "home", "1hom" },
    { "hotspot", "1hOM.spOM" },
    { "hour", "1aU.\xf8r" },
    { "hours", "1aU.\xf8rz" },
    { "house", "1haUs" },
    { "how", "1haU" },
    { "hundred", "1h\xf8n.WrEW" },
    { "hyderabad", "1haI.d\xf8.ra.baW" },
    { "i", "1aI" },
    { "icon", "1aI.kOn" },
    { "if", "If" },
    { "image", "1I.mIJ" },
    { "images", "1I.mI.JIz" },
    { "import", "Im.1porM" },
    { "important", "Im.1pOr.M\xf8nM" },
    { "in", "In" },
    { "inbox", "1In.bOks" },
    { "include", "In.1kluW" },
    { "incoming", "1In.k\xf8.mIG" },
    { "india", "1In.Wi.a" },
    { "indian", "1In.Wi.\xf8n" },
    { "info", "1In.fo" },
    { "information", "2In.f\xf8r.1me.S\xf8n" },
    { "input", "1In.pUM" },
    { "insert", "In.1s\xf8rM" },
    { "inside", "In.1saIW" },
    { "instagram", "1In.sMa.gr\xe6m" },
    { "install", "In.1sMOl" },
    { "installed", "In.1sMOlW" },
    { "installing", "In.1sMO.lIG" },
    { "internet", "1In.M\xf8r.nEM" },
    { "into", "1In.Mu" },
    { "invalid", "In.1v\xe6.lIW" },
    { "iphone", "1aI.fon" },
    { "is", "Iz" },
    { "it", "IM" },
    { "itahari", "I.1M\xf8.h\xf8.ri" },
    { "italian", "I.1M\xe6.ly\xf8n" },
    { "item", "1aI.M\xf8m" },
    { "items", "1aI.M\xf8mz" },
    { "its", "IMs" },
    { "january", "1J\xe6.nyu.\xf8.ri" },
    { "join", "1JcIn" },
    { "july", "JU.1laI" },
    { "jump", "1J\xf8mp" },
    { "june", "1Jun" },
    { "just", "1J\xf8sM" },
    { "kathmandu", "1ka\xf1.man.Wu" },
    { "keep", "1kip" },
    { "key", "1ki" },
    { "keyboard", "1ki.borW" },
    { "keys", "1kiz" },
    { "know", "1no" },
    { "kolkata", "kol.1ka.ta" },
    { "koshi", "1ko.Si" },
    { "label", "1le.b\xf8l" },
    { "lalitpur", "1l\xf8.lIt.pur" },
    { "language", "1l\xe6G.gwIJ" },
    { "languages", "1l\xe6G.gwI.JIz" },
    { "large", "1larJ" },
    { "last", "1lasM" },
    { "later", "1le.M\xf8r" },
    { "launch", "1lOnC" },
    { "launcher", "1lOn.C\xf8r" },
    { "learn", "1l\xf8rn" },
    { "leave", "1liv" },
    { "left", "1lEfM" },
    { "less", "1lEs" },
    { "let", "1lEM" },
    { "letter", "1lE.M\xf8r" },
    { "letters", "1lE.M\xf8rz" },
    { "level", "1lE.v\xf8l" },
    { "library", "1laI.brE.ri" },
    { "light", "1laIM" },
    { "like", "1laIk" },
    { "limit", "1lI.mIM" },
    { "line", "1laIn" },
    { "lines", "1laInz" },
    { "link", "1lIGk" },
    { "links", "1lIGks" },
    { "linux", "1lI.n\xf8ks" },
    { "list", "1lIsM" },
    { "listen", "1lI.s\xf8n" },
    { "live", "1laIv" },
    { "load", "1loW" },
    { "loading", "1lo.WIG" },
    { "local", "1lo.k\xf8l" },
    { "location", "lo.1ke.S\xf8n" },
    { "lock", "1lOk" },
    { "locked", "1lOkM" },
    { "log", "1lOg" },
    { "login", "1lO.gIn" },
    { "logout", "1lO.gaUM" },
    { "long", "1lOG" },
    { "look", "1lUk" },
    { "loud", "1laUW" },
    { "love", "1l\xf8v" },
    { "low", "1lo" },
    { "lower", "1lo.\xf8r" },
    { "mac", "1m\xe6k" },
    { "made", "1meW" },
    { "mail", "1mel" },
    { "main", "1men" },
    { "make", "1mek" },
    { "male", "1mel" },
    { "manage", "1m\xe6.nIJ" },
    { "manager", "1m\xe6.nI.J\xf8r" },
    { "many", "1mE.ni" },
    { "map", "1m\xe6p" },
    { "maps", "1m\xe6ps" },
    { "march", "1marC" },
    { "mark", "1mark" },
    { "max", "1m\xe6ks" },
    { "maximum", "1m\xe6k.sI.m\xf8m" },
    { "may", "1me" },
    { "me", "1mi" },
    { "media", "1mi.Wi.a" },
    { "medium", "1mi.Wi.\xf8m" },
    { "meet", "1miM" },
    { "meeting", "1mi.MIG" },
    { "member", "1mEm.b\xf8r" },
    { "memory", "1mE.m\xf8.ri" },
    { "menu", "1mE.nyu" },
    { "merge", "1m\xf8rJ" },
    { "message", "1mE.sIJ" },
    { "messages", "1mE.sI.JIz" },
    { "messenger", "1mE.sIn.J\xf8r" },
    { "mic", "1maIk" },
    { "microphone", "1maI.kr\xf8.fon" },
    { "microsoft", "1maI.kr\xf8.sOfM" },
    { "middle", "1mI.W\xf8l" },
    { "million", "1mI.ly\xf8n" },
    { "minimum", "1mI.nI.m\xf8m" },
    { "minus", "1maI.n\xf8s" },
    { "minute", "1mI.nIM" },
    { "minutes", "1mI.nIMs" },
    { "missed", "1mIsM" },
    { "mobile", "1mo.baIl" },
    { "mode", "1moW" },
    { "model", "1mO.W\xf8l" },
    { "monday", "1m\xf8n.We" },
    { "money", "1m\xf8.ni" },
    { "month", "1m\xf8nF" },
    { "months", "1m\xf8nFs" },
    { "more", "1mor" },
    { "morning", "1mOr.nIG" },
    { "most", "1mosM" },
    { "move", "1muv" },
    { "movie", "1mu.vi" },
    { "much", "1m\xf8" "C" },
    { "mumbai", "mUm.1baI" },
    { "music", "1myu.zIk" },
    { "must", "1m\xf8sM" },
    { "mute", "1myuM" },
    { "muted", "1myu.MIW" },
    { "my", "maI" },
    { "name", "1nem" },
    { "native", "1ne.MIv" },
    { "navigate", "1n\xe6.vI.geM" },
    { "navigation", "2n\xe6.vI.1ge.S\xf8n" },
    { "near", "1nI\xf8r" },
    { "need", "1niW" },
    { "nepal", "nE.1pal" },
    { "nepali", "nE.1pa.li" },
    { "netflix", "1nEM.flIks" },
    { "network", "1nEM.w\xf8rk" },
    { "never", "1nE.v\xf8r" },
    { "new", "1nyu" },
    { "news", "1nyuz" },
    { "next", "1nEksM" },
    { "night", "1naIM" },
    { "nine", "1naIn" },
    { "nineteen", "naIn.1Min" },
    { "ninety", "1naIn.Mi" },
    { "no", "1no" },
    { "none", "1n\xf8n" },
    { "normal", "1nOr.m\xf8l" },
    { "north", "1nOrF" },
    { "not", "1nOM" },
    { "note", "1noM" },
    { "notes", "1noMs" },
    { "nothing", "1n\xf8.FIG" },
    { "notice", "1no.MIs" },
    { "notification", "2no.MI.fI.1ke.S\xf8n" },
    { "notifications", "2no.MI.fI.1ke.S\xf8nz" },
    { "november", "no.1vEm.b\xf8r" },
    { "now", "1naU" },
    { "number", "1n\xf8m.b\xf8r" },
    { "numbers", "1n\xf8m.b\xf8rz" },
    { "october", "Ok.1Mo.b\xf8r" },
    { "of", "Ov" },
    { "off", "1Of" },
    { "office", "1O.fIs" },
    { "offline", "1Of.laIn" },
    { "ok", "o.1ke" },
    { "okay", "o.1ke" },
    { "old", "1olW" },
    { "on", "1On" },
    { "once", "1w\xf8ns" },
    { "one", "1w\xf8n" },
    { "oneplus", "1w\xf8n.pl\xf8s" },
    { "online", "1On.laIn" },
    { "only", "1on.li" },
    { "open", "1o.p\xf8n" },
    { "opened", "1o.p\xf8nW" },
    { "opening", "1o.p\xf8.nIG" },
    { "option", "1Op.S\xf8n" },
    { "options", "1Op.S\xf8nz" },
    { "or", "Or" },
    { "order", "1Or.W\xf8r" },
    { "other", "1\xf8.d\xf8r" },
    { "our", "1aU.\xf8r" },
    { "out", "1aUM" },
    { "output", "1aUM.pUM" },
    { "outside", "aUM.1saIW" },
    { "over", "1o.v\xf8r" },
    { "own", "1on" },
    { "package", "1p\xe6.kIJ" },
    { "page", "1peJ" },
    { "pages", "1pe.JIz" },
    { "pair", "1pEr" },
    { "paired", "1pErW" },
    { "panel", "1p\xe6.n\xf8l" },
    { "paragraph", "1p\xe6.r\xf8.graf" },
    { "part", "1parM" },
    { "passcode", "1pas.koW" },
    { "password", "1pas.w\xf8rW" },
    { "paste", "1pesM" },
    { "path", "1paF" },
    { "pause", "1pOz" },
    { "paused", "1pOzW" },
    { "pay", "1pe" },
    { "payment", "1pe.m\xf8nM" },
    { "people", "1pi.p\xf8l" },
    { "percent", "p\xf8r.1sEnM" },
    { "percentage", "p\xf8r.1sEn.MIJ" },
    { "permission", "p\xf8r.1mI.S\xf8n" },
    { "permissions", "p\xf8r.1mI.S\xf8nz" },
    { "person", "1p\xf8r.s\xf8n" },
    { "personal", "1p\xf8r.s\xf8.n\xf8l" },
    { "phone", "1fon" },
    { "photo", "1fo.Mo" },
    { "photos", "1fo.Moz" },
    { "pick", "1pIk" },
    { "picture", "1pIk.C\xf8r" },
    { "pictures", "1pIk.C\xf8rz" },
    { "pin", "1pIn" },
    { "pitch", "1pIC" },
    { "pixel", "1pIk.s\xf8l" },
    { "place", "1ples" },
    { "play", "1ple" },
    { "player", "1ple.\xf8r" },
    { "playing", "1ple.IG" },
    { "please", "1pliz" },
    { "plus", "1pl\xf8s" },
    { "podcast", "1pOW.kasM" },
    { "point", "1pcInM" },
    { "pokhara", "1po.K\xf8.ra" },
    { "pop", "1pOp" },
    { "popup", "1pO.p\xf8p" },
    { "port", "1porM" },
    { "position", "p\xf8.1zI.S\xf8n" },
    { "post", "1posM" },
    { "power", "1paU.\xf8r" },
    { "preference", "1prE.fr\xf8ns" },
    { "preferences", "1prE.fr\xf8n.sIz" },
    { "press", "1prEs" },
    { "preview", "1pri.vyu" },
    { "previous", "1pri.vi.\xf8s" },
    { "price", "1praIs" },
    { "print", "1prInM" },
    { "privacy", "1praI.v\xf8.si" },
    { "private", "1praI.vIM" },
    { "problem", "1prOb.l\xf8m" },
    { "process", "1prO.sEs" },
    { "profile", "1pro.faIl" },
    { "program", "1pro.gr\xe6m" },
    { "progress", "1prO.grEs" },
    { "project", "1prO.JEkM" },
    { "public", "1p\xf8" "b.lIk" },
    { "punctuation", "2p\xf8Gk.Cu.1e.S\xf8n" },
    { "put", "1pUM" },
    { "quality", "1kwO.lI.Mi" },
    { "question", "1kwEs.C\xf8n" },
    { "queue", "1kyu" },
    { "quick", "1kwIk" },
    { "quiet", "1kwaI.\xf8M" },
    { "quit", "1kwIM" },
    { "quote", "1kwoM" },
    { "radio", "1re.Wi.o" },
    { "rate", "1reM" },
    { "read", "1riW" },
    { "reader", "1ri.W\xf8r" },
    { "reading", "1ri.WIG" },
    { "ready", "1rE.Wi" },
    { "real", "1ri.\xf8l" },
    { "receive", "rI.1siv" },
    { "received", "rI.1sivW" },
    { "recent", "1ri.s\xf8nM" },
    { "recently", "1ri.s\xf8nM.li" },
    { "record", "rI.1kOrW" },
    { "recording", "rI.1kOr.WIG" },
    { "red", "1rEW" },
    { "refresh", "rI.1frES" },
    { "remove", "rI.1muv" },
    { "removed", "rI.1muvW" },
    { "rename", "ri.1nem" },
    { "repeat", "rI.1piM" },
    { "replace", "rI.1ples" },
    { "reply", "rI.1plaI" },
    { "report", "rI.1porM" },
    { "request", "rI.1kwEsM" },
    { "required", "rI.1kwaI\xf8rW" },
    { "reset", "ri.1sEM" },
    { "restart", "ri.1sMarM" },
    { "restore", "rI.1sMor" },
    { "result", "rI.1z\xf8lM" },
    { "results", "rI.1z\xf8lMs" },
    { "resume", "rI.1zum" },
    { "return", "rI.1M\xf8rn" },
    { "review", "rI.1vyu" },
    { "right", "1raIM" },
    { "ring", "1rIG" },
    { "ringtone", "1rIG.Mon" },
    { "room", "1rum" },
    { "row", "1ro" },
    { "run", "1r\xf8n" },
    { "running", "1r\xf8.nIG" },
    { "rupee", "rU.1pi" },
    { "rupees", "rU.1piz" },
    { "safe", "1sef" },
    { "same", "1sem" },
    { "samsung", "1s\xe6m.s\xf8G" },
    { "saturday", "1s\xe6.M\xf8r.We" },
    { "save", "1sev" },
    { "saved", "1sevW" },
    { "say", "1se" },
    { "scan", "1sk\xe6n" },
    { "schedule", "1skE.Jul" },
    { "school", "1skul" },
    { "screen", "1skrin" },
    { "screenshot", "1skrin.SOM" },
    { "scroll", "1skrol" },
    { "search", "1s\xf8rC" },
    { "searching", "1s\xf8r.CIG" },
    { "second", "1sE.k\xf8nW" },
    { "seconds", "1sE.k\xf8nWz" },
    { "section", "1sEk.S\xf8n" },
    { "secure", "sI.1kyU\xf8r" },
    { "security", "sI.1kyU.rI.Mi" },
    { "see", "1si" },
    { "seek", "1sik" },
    { "select", "sI.1lEkM" },
    { "selected", "sI.1lEk.MIW" },
    { "selection", "sI.1lEk.S\xf8n" },
    { "send", "1sEnW" },
    { "sending", "1sEn.WIG" },
    { "sent", "1sEnM" },
    { "sentence", "1sEn.M\xf8ns" },
    { "september", "sEp.1MEm.b\xf8r" },
    { "server", "1s\xf8r.v\xf8r" },
    { "service", "1s\xf8r.vIs" },
    { "services", "1s\xf8r.vI.sIz" },
    { "session", "1sE.S\xf8n" },
    { "set", "1sEM" },
    { "setting", "1sE.MIG" },
    { "settings", "1sE.MIGz" },
    { "setup", "1sE.M\xf8p" },
    { "seven", "1sE.v\xf8n" },
    { "seventeen", "sE.v\xf8n.1Min" },
    { "seventy", "1sE.v\xf8n.Mi" },
    { "share", "1SEr" },
    { "shared", "1SErW" },
    { "she", "1Si" },
    { "sheet", "1SiM" },
    { "shift", "1SIfM" },
    { "short", "1SOrM" },
    { "shortcut", "1SOrM.k\xf8M" },
    { "shortcuts", "1SOrM.k\xf8Ms" },
    { "should", "1SUW" },
    { "show", "1So" },
    { "showing", "1So.IG" },
    { "shut", "1S\xf8M" },
    { "side", "1saIW" },
    { "sign", "1saIn" },
    { "signal", "1sIg.n\xf8l" },
    { "silent", "1saI.l\xf8nM" },
    { "sim", "1sIm" },
    { "simple", "1sIm.p\xf8l" },
    { "since", "1sIns" },
    { "single", "1sIG.g\xf8l" },
    { "site", "1saIM" },
    { "six", "1sIks" },
    { "sixteen", "sIks.1Min" },
    { "sixty", "1sIks.Mi" },
    { "size", "1saIz" },
    { "skip", "1skIp" },
    { "slash", "1sl\xe6S" },
    { "sleep", "1slip" },
    { "slide", "1slaIW" },
    { "slider", "1slaI.W\xf8r" },
    { "slow", "1slo" },
    { "slower", "1slo.\xf8r" },
    { "small", "1smOl" },
    { "smart", "1smarM" },
    { "smile", "1smaIl" },
    { "so", "so" },
    { "software", "1sOfM.wEr" },
    { "some", "1s\xf8m" },
    { "something", "1s\xf8m.FIG" },
    { "song", "1sOG" },
    { "soon", "1sun" },
    { "sorry", "1sO.ri" },
    { "sort", "1sOrM" },
    { "sound", "1saUnW" },
    { "sounds", "1saUnWz" },
    { "source", "1sors" },
    { "south", "1saUF" },
    { "space", "1spes" },
    { "spanish", "1sp\xe6.nIS" },
    { "speak", "1spik" },
    { "speaker", "1spi.k\xf8r" },
    { "speaking", "1spi.kIG" },
    { "special", "1spE.S\xf8l" },
    { "speech", "1spiC" },
    { "speed", "1spiW" },
    { "spell", "1spEl" },
    { "spelling", "1spE.lIG" },
    { "spoken", "1spo.k\xf8n" },
    { "spotify", "1spO.MI.faI" },
    { "standard", "1sM\xe6n.W\xf8rW" },
    { "star", "1sMar" },
    { "start", "1sMarM" },
    { "started", "1sMar.MIW" },
    { "starting", "1sMar.MIG" },
    { "state", "1sMeM" },
    { "status", "1sMe.M\xf8s" },
    { "step", "1sMEp" },
    { "still", "1sMIl" },
    { "stop", "1sMOp" },
    { "stopped", "1sMOpM" },
    { "storage", "1sMo.rIJ" },
    { "store", "1sMor" },
    { "story", "1sMo.ri" },
    { "stream", "1sMrim" },
    { "streaming", "1sMri.mIG" },
    { "street", "1sMriM" },
    { "style", "1sMaIl" },
    { "subject", "1s\xf8" "b.JEkM" },
    { "submit", "s\xf8" "b.1mIM" },
    { "success", "s\xf8k.1sEs" },
    { "summary", "1s\xf8.m\xf8.ri" },
    { "sunday", "1s\xf8n.We" },
    { "support", "s\xf8.1porM" },
    { "supported", "s\xf8.1por.MIW" },
    { "sure", "1SU\xf8r" },
    { "swipe", "1swaIp" },
    { "switch", "1swIC" },
    { "switched", "1swICM" },
    { "switching", "1swI.CIG" },
    { "symbol", "1sIm.b\xf8l" },
    { "symbols", "1sIm.b\xf8lz" },
    { "sync", "1sIGk" },
    { "system", "1sIs.M\xf8m" },
    { "tab", "1M\xe6" "b" },
    { "table", "1Me.b\xf8l" },
    { "tabs", "1M\xe6" "bz" },
    { "tag", "1M\xe6g" },
    { "take", "1Mek" },
    { "talk", "1MOk" },
    { "talkback", "1MOk.b\xe6k" },
    { "tap", "1M\xe6p" },
    { "task", "1Mask" },
    { "team", "1Mim" },
    { "telegram", "1ME.lI.gr\xe6m" },
    { "telephone", "1ME.lI.fon" },
    { "television", "1ME.lI.vI.Z\xf8n" },
    { "tell", "1MEl" },
    { "temperature", "1MEm.pr\xf8.C\xf8r" },
    { "ten", "1MEn" },
    { "terminal", "1M\xf8r.mI.n\xf8l" },
    { "test", "1MEsM" },
    { "testing", "1MEs.MIG" },
    { "text", "1MEksM" },
    { "than", "d\xe6n" },
    { "thank", "1F\xe6Gk" },
    { "thanks", "1F\xe6Gks" },
    { "that", "d\xe6M" },
    { "the", "d\xf8" },
    { "their", "1dEr" },
    { "them", "dEm" },
    { "theme", "1Fim" },
    { "then", "1dEn" },
    { "there", "1dEr" },
    { "these", "1diz" },
    { "they", "1de" },
    { "thing", "1FIG" },
    { "think", "1FIGk" },
    { "third", "1F\xf8rW" },
    { "thirteen", "F\xf8r.1Min" },
    { "thirty", "1F\xf8r.Mi" },
    { "this", "1dIs" },
    { "those", "1doz" },
    { "thousand", "1FaU.z\xf8nW" },
    { "three", "1Fri" },
    { "through", "1Fru" },
    { "thursday", "1F\xf8rz.We" },
    { "tick", "1MIk" },
    { "ticked", "1MIkM" },
    { "time", "1MaIm" },
    { "timer", "1MaI.m\xf8r" },
    { "times", "1MaImz" },
    { "title", "1MaI.M\xf8l" },
    { "to", "Mu" },
    { "today", "MU.1We" },
    { "together", "MU.1gE.d\xf8r" },
    { "toggle", "1MO.g\xf8l" },
    { "tomorrow", "MU.1mO.ro" },
    { "tone", "1Mon" },
    { "too", "1Mu" },
    { "tool", "1Mul" },
    { "toolbar", "1Mul.bar" },
    { "tools", "1Mulz" },
    { "top", "1MOp" },
    { "total", "1Mo.M\xf8l" },
    { "touch", "1M\xf8" "C" },
    { "track", "1Mr\xe6k" },
    { "translate", "Mr\xe6nz.1leM" },
    { "translation", "Mr\xe6nz.1le.S\xf8n" },
    { "trash", "1Mr\xe6S" },
    { "true", "1Mru" },
    { "try", "1MraI" },
    { "tuesday", "1Myuz.We" },
    { "turn", "1M\xf8rn" },
    { "turned", "1M\xf8rnW" },
    { "twelve", "1MwElv" },
    { "twenty", "1MwEn.Mi" },
    { "two", "1Mu" },
    { "type", "1MaIp" },
    { "typing", "1MaI.pIG" },
    { "unable", "\xf8n.1e.b\xf8l" },
    { "unchecked", "\xf8n.1CEkM" },
    { "under", "1\xf8n.W\xf8r" },
    { "undo", "\xf8n.1Wu" },
    { "unicorn", "1yu.nI.kOrn" },
    { "unknown", "\xf8n.1non" },
    { "unless", "\xf8n.1lEs" },
    { "unlock", "\xf8n.1lOk" },
    { "unlocked", "\xf8n.1lOkM" },
    { "unmute", "\xf8n.1myuM" },
    { "unread", "\xf8n.1rEW" },
    { "until", "\xf8n.1MIl" },
    { "up", "1\xf8p" },
    { "update", "\xf8p.1WeM" },
    { "updated", "\xf8p.1We.MIW" },
    { "updates", "\xf8p.1WeMs" },
    { "upload", "1\xf8p.loW" },
    { "upper", "1\xf8.p\xf8r" },
    { "usage", "1yu.sIJ" },
    { "use", "1yuz" },
    { "used", "1yuzW" },
    { "user", "1yu.z\xf8r" },
    { "username", "1yu.z\xf8r.nem" },
    { "users", "1yu.z\xf8rz" },
    { "using", "1yu.zIG" },
    { "value", "1v\xe6.lyu" },
    { "verified", "1vE.rI.faIW" },
    { "verify", "1vE.rI.faI" },
    { "version", "1v\xf8r.S\xf8n" },
    { "very", "1vE.ri" },
    { "via", "1vaI.a" },
    { "vibrate", "vaI.1breM" },
    { "vibration", "vaI.1bre.S\xf8n" },
    { "video", "1vI.Wi.o" },
    { "videos", "1vI.Wi.oz" },
    { "view", "1vyu" },
    { "voice", "1vcIs" },
    { "voicemail", "1vcIs.mel" },
    { "voices", "1vcI.sIz" },
    { "volume", "1vO.lyum" },
    { "wait", "1weM" },
    { "waiting", "1we.MIG" },
    { "wake", "1wek" },
    { "walk", "1wOk" },
    { "wallpaper", "1wOl.pe.p\xf8r" },
    { "want", "1wOnM" },
    { "warning", "1wOr.nIG" },
    { "was", "wOz" },
    { "watch", "1wOC" },
    { "water", "1wO.M\xf8r" },
    { "way", "1we" },
    { "we", "1wi" },
    { "weather", "1wE.d\xf8r" },
    { "web", "1wEb" },
    { "website", "1wEb.saIM" },
    { "wednesday", "1wEnz.We" },
    { "week", "1wik" },
    { "weeks", "1wiks" },
    { "welcome", "1wEl.k\xf8m" },
    { "well", "1wEl" },
    { "were", "w\xf8r" },
    { "west", "1wEsM" },
    { "what", "1wOM" },
    { "whatsapp", "1wOMs.\xe6p" },
    { "when", "1wEn" },
    { "where", "1wEr" },
    { "which", "1wIC" },
    { "while", "1waIl" },
    { "white", "1waIM" },
    { "who", "1hu" },
    { "why", "1waI" },
    { "wide", "1waIW" },
    { "widget", "1wI.JIM" },
    { "wifi", "1waI.faI" },
    { "will", "wIl" },
    { "window", "1wIn.Wo" },
    { "windows", "1wIn.Woz" },
    { "wireless", "1waI\xf8r.lEs" },
    { "with", "wId" },
    { "without", "wI.1daUM" },
    { "word", "1w\xf8rW" },
    { "words", "1w\xf8rWz" },
    { "work", "1w\xf8rk" },
    { "working", "1w\xf8r.kIG" },
    { "world", "1w\xf8rlW" },
    { "would", "1wUW" },
    { "write", "1raIM" },
    { "writing", "1raI.MIG" },
    { "wrong", "1rOG" },
    { "xiaomi", "1SaU.mi" },
    { "year", "1yI\xf8r" },
    { "years", "1yI\xf8rz" },
    { "yellow", "1yE.lo" },
    { "yes", "1yEs" },
    { "yesterday", "1yEs.M\xf8r.We" },
    { "yet", "1yEM" },
    { "you", "1yu" },
    { "your", "1yor" },
    { "youtube", "1yu.Myub" },
    { "zero", "1zi.ro" },
    { "zoom", "1zum" }
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

        /* Sentence & clause punctuation (including Devanagari danda । and ॥,
           the horizontal ellipsis … and the bullet •, all of which end a
           clause the way a full stop does). */
        if (cp == 0x0964 || cp == 0x0965 || cp == '.'
            || cp == 0x2026 || cp == 0x2022) {
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

        /* Anything still unhandled: an emoji, an unfamiliar symbol or a
           mark the pipeline did not name. Dropping it outright used to join
           the words on either side of it into one; a separating space keeps
           them two words, which is the lesser wrong by a wide margin.
           Letters and digits never reach here: each has its own branch
           above, so this fires only on non-word codepoints. */
        if (out->len > 0 && out->data[out->len - 1] != ' ')
            ibuf_putc(out, ' ');
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
