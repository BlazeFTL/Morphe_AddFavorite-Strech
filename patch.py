#!/usr/bin/env python3
"""
patch.py -- apply fork changes to a MorpheApp/morphe-manager checkout (dev branch):
  1. favorite universal patches (star + long-press, favorites float to top)
  2. expert-gated "Skip APK signing" (morphe-manager PR #1008)
  3. fork identity (applicationId, app name)

Anchors are structural (function bodies, call arguments, parameter lists, single
lines), never pasted multi-line context. Files are found by declaration; the path
is only a hint. Feature code lives in new files. Idempotent and all-or-nothing:
nothing is written unless every required edit resolves and the result passes
bracket and reference checks.

Usage:
    python3 patch.py [path-to-morphe-manager-checkout]
    python3 patch.py [path] --check     dry run, exit 1 if anything is pending
"""
import argparse
import pathlib
import re
import sys
from collections import namedtuple

FORK_APP_ID = "app.morphe.manages"
FORK_APP_NAME = "MorpheFork"
DEFAULT_UPSTREAM_APP_ID = "app.morphe.manager"

BOM = "\ufeff"
NOTES = []

Fun = namedtuple("Fun", "start name lp rp bo bc eq")


def note(msg):
    NOTES.append(msg)


def mask(src):
    out = list(src)
    n = len(src)

    def blank(a, b):
        for k in range(a, min(b, n)):
            if out[k] != "\n":
                out[k] = " "

    i = 0
    while i < n:
        c = src[i]
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j < 0 else j
            blank(i, j)
            i = j
        elif src.startswith("/*", i):
            depth, j = 1, i + 2
            while j < n and depth:
                if src.startswith("/*", j):
                    depth += 1
                    j += 2
                elif src.startswith("*/", j):
                    depth -= 1
                    j += 2
                else:
                    j += 1
            blank(i, j)
            i = j
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            blank(i, j)
            i = j
        elif c == '"':
            j = i + 1
            while j < n and src[j] != '"' and src[j] != "\n":
                j += 2 if src[j] == "\\" else 1
            blank(i, j + 1)
            i = j + 1
        elif c == "'":
            mm = re.match(r"'(?:\\u[0-9a-fA-F]{4}|\\.|[^\\'\n])'", src[i:i + 8])
            if mm:
                blank(i, i + mm.end())
                i += mm.end()
            else:
                i += 1
        else:
            i += 1
    return "".join(out)


PAIRS = {"(": ")", "{": "}", "[": "]"}


def match_close(m, i):
    open_c = m[i]
    close_c = PAIRS[open_c]
    depth = 0
    for j in range(i, len(m)):
        if m[j] == open_c:
            depth += 1
        elif m[j] == close_c:
            depth -= 1
            if depth == 0:
                return j
    return -1


def balanced(m):
    stack = []
    for c in m:
        if c in PAIRS:
            stack.append(PAIRS[c])
        elif c in ")}]":
            if not stack or stack.pop() != c:
                return False
    return not stack


def line_start(s, i):
    return s.rfind("\n", 0, i) + 1


def line_end(s, i):
    j = s.find("\n", i)
    return len(s) if j < 0 else j


def indent_of(s, i):
    return re.match(r"[ \t]*", s[line_start(s, i):]).group(0)


def reindent(text, ind):
    return "\n".join(ind + l if l.strip() else l for l in text.split("\n"))


def split_top(text):
    parts, depth, last = [], 0, 0
    for i, c in enumerate(text):
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        elif c == "," and depth == 0:
            parts.append(text[last:i])
            last = i + 1
    parts.append(text[last:])
    return parts


def annotation_start(src, pos):
    ls = line_start(src, pos)
    while ls > 0:
        pls = line_start(src, ls - 1)
        if src[pls:ls].strip().startswith("@"):
            ls = pls
        else:
            break
    return ls


def doc_start(src, pos):
    ls = annotation_start(src, pos)
    if ls > 0:
        pls = line_start(src, ls - 1)
        if src[pls:ls].strip().endswith("*/"):
            k = src.rfind("/*", 0, ls)
            if k >= 0:
                return line_start(src, k)
    return ls


_FUN_RX = re.compile(r"\bfun\s+(?:<[^>\n]*>\s*)?(?:[\w.]+\.)?(\w+)\s*\(")
_AFTER_PARAMS = re.compile(r"\s*(?::[^{=]*)?(\{|=)")


def all_funs(m):
    out = []
    for mm in _FUN_RX.finditer(m):
        lp = mm.end() - 1
        rp = match_close(m, lp)
        if rp < 0:
            continue
        bo = bc = eq = None
        t = _AFTER_PARAMS.match(m, rp + 1)
        if t:
            if t.group(1) == "{":
                bo = t.end() - 1
                bc = match_close(m, bo)
            else:
                eq = t.end() - 1
        out.append(Fun(mm.start(), mm.group(1), lp, rp, bo, bc, eq))
    return out


def find_funs(m, name):
    return [f for f in all_funs(m) if f.name == name]


def one(items):
    return items[0] if len(items) == 1 else None


def fun_end(m, f):
    if f.bo is not None:
        return f.bc
    k = m.find("{", f.eq)
    nl = m.find("\n", f.eq)
    if k != -1 and (nl == -1 or k < nl):
        return match_close(m, k)
    return nl if nl != -1 else len(m) - 1


def enclosing_fun(m, pos):
    best = None
    for f in all_funs(m):
        if f.bo is not None and f.bo < pos < f.bc:
            if best is None or f.bo > best.bo:
                best = f
    return best


def find_calls(m, name, lo=0, hi=None):
    hi = len(m) if hi is None else hi
    out = []
    for mm in re.finditer(r"(?<![\w.])" + re.escape(name) + r"\s*\(", m[lo:hi]):
        s = lo + mm.start()
        if re.search(r"(?:^|\W)fun\s+$", m[max(0, s - 8):s]):
            continue
        out.append(lo + mm.end() - 1)
    return out


def anchor(m, rx, which=None):
    hits = list(re.finditer(rx, m, re.M))
    if which is None:
        return hits[0] if len(hits) == 1 else None
    return hits[which] if len(hits) > which else None


def insert_after_line(src, m, rx, text, which=None, extra=""):
    h = anchor(m, rx, which)
    if not h:
        return None
    ind = indent_of(src, h.start()) + extra
    e = line_end(src, h.end())
    return src[:e] + "\n" + reindent(text, ind) + src[e:]


def insert_before_line(src, m, rx, text, which=None):
    h = anchor(m, rx, which)
    if not h:
        return None
    s = line_start(src, h.start())
    ind = indent_of(src, h.start())
    return src[:s] + reindent(text, ind) + "\n" + src[s:]


def add_first_arg(src, m, lp, text, marker=None):
    rp = match_close(m, lp)
    if rp < 0:
        return None
    if marker and re.search(marker, m[lp:rp]):
        return src
    mm = re.match(r"(?:[ \t]*\n)+", src[lp + 1:rp])
    if not mm:
        return None
    s = lp + 1 + mm.end()
    ind = indent_of(src, s)
    return src[:s] + reindent(text, ind) + "\n" + src[s:]


def add_last_param(src, m, f, text, marker):
    if re.search(marker, m[f.lp:f.rp]):
        return src
    region = m[f.lp + 1:f.rp]
    end = len(region.rstrip())
    ls = region.rfind("\n", 0, end) + 1
    if ls == 0 and "\n" not in region[:end]:
        return None
    last = region[ls:end]
    if not re.match(r"\s*(?:vararg\s+|noinline\s+|crossinline\s+)?\w+\s*:", last):
        return None
    s = f.lp + 1 + ls
    ind = indent_of(src, s)
    return src[:s] + reindent(text, ind) + "\n" + src[s:]


def package_of(text):
    mm = re.search(r"^package\s+([\w.]+)", text, re.M)
    return mm.group(1) if mm else None


def ensure_import(src, fq):
    if re.search(r"^import\s+" + re.escape(fq) + r"\s*$", src, re.M):
        return src
    pkg, _, _ = fq.rpartition(".")
    if package_of(src) == pkg:
        return src
    if re.search(r"^import\s+" + re.escape(pkg) + r"\.\*\s*$", src, re.M):
        return src
    lines = src.split("\n")
    imports = [i for i, l in enumerate(lines) if l.startswith("import ")]
    line = "import " + fq
    if not imports:
        pk = next((i for i, l in enumerate(lines) if l.startswith("package ")), None)
        if pk is None:
            return None
        lines[pk + 1:pk + 1] = ["", line]
        return "\n".join(lines)
    for i in imports:
        if lines[i][7:].strip() > fq:
            lines.insert(i, line)
            return "\n".join(lines)
    lines.insert(imports[-1] + 1, line)
    return "\n".join(lines)


def imp(fq):
    return lambda src: ensure_import(src, fq)


def sub_once(src, rx, repl, flags=0):
    hits = list(re.finditer(rx, src, flags))
    if len(hits) != 1:
        return None
    h = hits[0]
    return src[:h.start()] + h.expand(repl) + src[h.end():]


# ---------------------------------------------------------------- resources / gradle

def strings_edit(entries, anchor_name):
    def fn(src):
        missing = [x for n, x in entries if 'name="%s"' % n not in src]
        if not missing:
            return src
        h = re.search(r'^([ \t]*)<string\s+name="%s"[^\n]*</string>[ \t]*$' % re.escape(anchor_name), src, re.M)
        if h:
            block = "\n".join(h.group(1) + x for x in missing)
            return src[:h.end()] + "\n" + block + src[h.end():]
        k = src.rfind("</resources>")
        if k < 0:
            return None
        return src[:k] + "\n".join("    " + x for x in missing) + "\n" + src[k:]
    return fn


FAVORITE_STRINGS = [
    ("expert_mode_favorite_added", '<string name="expert_mode_favorite_added">%s added to favorites</string>'),
    ("expert_mode_favorite_removed", '<string name="expert_mode_favorite_removed">%s removed from favorites</string>'),
    ("add_to_favorites", '<string name="add_to_favorites">Add to favorites</string>'),
    ("remove_from_favorites", '<string name="remove_from_favorites">Remove from favorites</string>'),
]

SIGNING_STRINGS = [
    ("settings_advanced_skip_signing", '<string name="settings_advanced_skip_signing">Skip APK signing</string>'),
    ("settings_advanced_skip_signing_description",
     '<string name="settings_advanced_skip_signing_description">Leave patched APKs unsigned. You can still save or send them to the selected installer, but Android normally rejects unsigned APKs</string>'),
]

OUR_STRINGS = [n for n, _ in FAVORITE_STRINGS + SIGNING_STRINGS]


def app_name_edit(src):
    h = re.search(r'(<string\s+name="app_name"[^>]*>)([^<]*)(</string>)', src)
    if not h:
        return None
    if h.group(2) == FORK_APP_NAME:
        return src
    return src[:h.start(2)] + FORK_APP_NAME + src[h.end(2):]


def app_id_edit(src):
    hits = list(re.finditer(r'(\bapplicationId\s*=\s*")([^"]+)(")', src))
    if len(hits) != 1:
        return None
    h = hits[0]
    if h.group(2) == FORK_APP_ID:
        return src
    return src[:h.start(2)] + FORK_APP_ID + src[h.end(2):]


def gservices_edit(upstream):
    def fn(src):
        found = [False]

        def repl(h):
            value = h.group(2)
            if value == FORK_APP_ID or value.startswith(FORK_APP_ID + "."):
                found[0] = True
                return h.group(0)
            if value == upstream or value.startswith(upstream + "."):
                found[0] = True
                return h.group(1) + FORK_APP_ID + value[len(upstream):] + h.group(3)
            return h.group(0)

        out = re.sub(r'("package_name"\s*:\s*")([^"]+)(")', repl, src)
        return out if found[0] else None
    return fn


# ---------------------------------------------------------------- PreferencesManager

def prefs_decl(name, text, anchors):
    def fn(src):
        m = mask(src)
        if re.search(r"\bval\s+%s\s*=" % name, m):
            return src
        for a in anchors:
            r = insert_after_line(src, m, a, text)
            if r is not None:
                return r
        return None
    return fn


def prefs_snapshot_field(src):
    m = mask(src)
    if re.search(r"\bval\s+favoriteUniversalPatches\s*:", m):
        return src
    text = "val favoriteUniversalPatches: Set<String>? = null,"
    r = insert_before_line(src, m, r"^[ \t]*val\s+useExpertMode\s*:\s*Boolean\?\s*=\s*null\b[^\n]*$", text)
    if r is not None:
        return r
    h = re.search(r"\bdata\s+class\s+SettingsSnapshot\s*\(\s*\n", m)
    if not h:
        return None
    return src[:h.end()] + indent_of(src, h.end()) + text + "\n" + src[h.end():]


def prefs_export(src):
    m = mask(src)
    if re.search(r"\bfavoriteUniversalPatches\s*=\s*favoriteUniversalPatches\.get\(", m):
        return src
    text = "favoriteUniversalPatches = favoriteUniversalPatches.get().takeIf { it.isNotEmpty() },"
    r = insert_before_line(src, m, r"^[ \t]*useExpertMode\s*=\s*useExpertMode\.get\(\)\s*,?[ \t]*$", text)
    if r is not None:
        return r
    h = re.search(r"\bfun\s+exportSettings\s*\([^)]*\)[^=\n{]*=\s*SettingsSnapshot\s*\(", m)
    if not h:
        return None
    return add_first_arg(src, m, h.end() - 1, text)


def prefs_restricted(src):
    m = mask(src)
    if re.search(r"\bfavoriteUniversalPatches\s*=\s*favoriteUniversalPatches\.takeIf", m):
        return src
    text = "favoriteUniversalPatches = favoriteUniversalPatches.takeIf { patching },"
    r = insert_before_line(src, m, r"^[ \t]*useExpertMode\s*=\s*useExpertMode\.takeIf\s*\{[^\n]*$", text)
    return r


def prefs_restore(src):
    m = mask(src)
    if re.search(r"\bsnapshot\.favoriteUniversalPatches\b", m):
        return src
    text = "snapshot.favoriteUniversalPatches?.let { favoriteUniversalPatches.value = it }"
    r = insert_after_line(src, m, r"^[ \t]*snapshot\.useExpertMode\?\.let\s*\{[^\n]*\}[ \t]*$", text)
    if r is not None:
        return r
    return insert_after_line(
        src, m, r"^[ \t]*(?:suspend\s+)?fun\s+importSettings\s*\([^)]*\)[^=\n{]*=\s*edit\s*\{[ \t]*$", text, extra="    ")


PREFS_EDITS = [
    ("favorites pref declaration", prefs_decl(
        "favoriteUniversalPatches",
        '\n/** Names of universal patches favorited by the user, displayed at the top of universal patch sections. */\nval favoriteUniversalPatches = stringSetPreference("favorite_universal_patches", emptySet())',
        [r"^[ \t]*val\s+useExpertMode\s*=[^\n]*$"]), False),
    ("skip signing pref declaration", prefs_decl(
        "skipApkSigning",
        '\n/** Leave patched APKs unsigned; the original META-INF signature files stay in the output. */\nval skipApkSigning = booleanPreference("skip_apk_signing", false)',
        [r"^[ \t]*val\s+stripUnusedNativeLibs\s*=[^\n]*$", r"^[ \t]*val\s+useExpertMode\s*=[^\n]*$"]), False),
    ("SettingsSnapshot field", prefs_snapshot_field, False),
    ("export", prefs_export, False),
    ("restrictedTo", prefs_restricted, True),
    ("restore", prefs_restore, False),
]


# ---------------------------------------------------------------- SettingsViewModel

def vm_edit(src):
    m = mask(src)
    if re.search(r"\bfun\s+setSkipApkSigning\b", m):
        return src
    text = ("\n/** Leaves patched APKs unsigned, keeping the original META-INF signature files. */\n"
            "fun setSkipApkSigning(enabled: Boolean) = viewModelScope.launch {\n"
            "    prefs.skipApkSigning.update(enabled)\n}")
    f = one(find_funs(m, "setStripUnusedNativeLibs"))
    if f:
        ind = indent_of(src, f.start)
        e = line_end(src, fun_end(m, f))
        return src[:e] + "\n" + reindent(text, ind) + src[e:]
    f = one(find_funs(m, "setGitHubPat"))
    if f:
        s = doc_start(src, f.start)
        ind = indent_of(src, f.start)
        return src[:s] + reindent(text.lstrip("\n"), ind) + "\n\n" + src[s:]
    return None


# ---------------------------------------------------------------- AdvancedTabContent

SWITCH = """SettingsSwitchItem(
    checked = skipApkSigning,
    onToggle = { settingsViewModel.setSkipApkSigning(!skipApkSigning) },
    icon = Icons.Outlined.LockOpen,
    title = stringResource(R.string.settings_advanced_skip_signing),
    subtitle = stringResource(R.string.settings_advanced_skip_signing_description)
)

SettingsDivider()
"""


def adv_state(src):
    m = mask(src)
    if re.search(r"\bval\s+skipApkSigning\s+by\b", m):
        return src
    text = "val skipApkSigning by prefs.skipApkSigning.getAsState()"
    r = insert_after_line(src, m, r"^[ \t]*val\s+useExpertMode\s+by\s+prefs\.useExpertMode\.getAsState\(\)[ \t]*$", text)
    if r is not None:
        return r
    return insert_after_line(src, m, r"^[ \t]*val\s+\w+\s+by\s+prefs\.\w+\.getAsState\(\)[ \t]*$", text, which=0)


def adv_switch(src):
    m = mask(src)
    if "R.string.settings_advanced_skip_signing" in m:
        return src
    hits = list(re.finditer(r"^[ \t]*GitHubPatSettingsItem\s*\(", m, re.M))
    if len(hits) == 1:
        s = line_start(src, hits[0].start())
        ind = indent_of(src, s)
        if s > 0:
            pls = line_start(src, s - 1)
            if src[pls:s].strip().startswith("//"):
                s = pls
        return src[:s] + reindent(SWITCH, ind) + "\n" + src[s:]
    h = re.search(
        r"if\s*\(\s*expertMode\s*\)\s*\{[ \t]*\n[ \t]*Column\([^\n]*\{[ \t]*\n([ \t]*)SettingsGroup\s*\{[ \t]*\n", m)
    if not h:
        return None
    ind = h.group(1) + "    "
    return src[:h.end()] + reindent(SWITCH, ind) + "\n" + src[h.end():]


def fix_duplicate_strip_toggle(ws, adv_path):
    rx = re.compile(r"R\.string\.settings_advanced_strip_unused_libs\b")
    others = 0
    for p in ws.kt_paths():
        if p != adv_path and "/ui/" in p.as_posix():
            others += len(rx.findall(mask(ws.get(p))))
    src = ws.get(adv_path)
    m = mask(src)
    in_adv = len(rx.findall(m))
    keep = 0 if others else 1
    if in_adv <= keep:
        return
    removed = 0
    while len(rx.findall(m)) > keep:
        hits = list(rx.finditer(m))
        h = hits[-1]
        calls = find_calls(m, "SettingsSwitchItem", 0, h.start())
        if not calls:
            break
        lp = calls[-1]
        rp = match_close(m, lp)
        if not (lp < h.start() < rp):
            break
        a = line_start(src, m.rfind("SettingsSwitchItem", 0, lp + 1))
        b = line_end(src, rp)
        while a > 0:
            pls = line_start(src, a - 1)
            if src[pls:a].strip().startswith("//"):
                a = pls
            else:
                break
        src = src[:a] + src[min(b + 1, len(src)):]
        m = mask(src)
        removed += 1
    if not removed:
        return
    src = re.sub(r"([ \t]*SettingsDivider\(\)[ \t]*\n)(?:[ \t]*\n)*[ \t]*SettingsDivider\(\)[ \t]*\n", r"\1", src)
    src = re.sub(r"\n(?:[ \t]*\n)*[ \t]*SettingsDivider\(\)[ \t]*\n(?:[ \t]*\n)*([ \t]*\})", r"\n\1", src)
    src = re.sub(r"(SettingsGroup\s*\{[ \t]*\n)(?:[ \t]*\n)*[ \t]*SettingsDivider\(\)[ \t]*\n(?:[ \t]*\n)?", r"\1", src)
    unused = re.sub(r"^[ \t]*val\s+stripUnusedNativeLibs\s+by[^\n]*$", "", mask(src), flags=re.M)
    if not re.search(r"\bstripUnusedNativeLibs\b", unused):
        src = re.sub(r"^[ \t]*val\s+stripUnusedNativeLibs\s+by\s+[^\n]*\n", "", src, flags=re.M)
    ws.set(adv_path, src)
    note("removed duplicate 'Optimize for device architecture' toggle from Expert settings (kept in Patcher options)")


# ---------------------------------------------------------------- PatcherWorker

def worker_sign(src):
    m = mask(src)
    if "SignatureRestorer.restore(" in m:
        return src
    hits = list(re.finditer(r"^([ \t]*)keystoreManager\.sign\(([^\n]*)\)[ \t]*$", m, re.M))
    if len(hits) != 1 or not re.search(r"\bval\s+inputFile\b", m) or "SplitApkPreparer" not in m:
        return None
    h = hits[0]
    ind = h.group(1)
    parts = split_top(src[h.start(2):h.end(2)])
    if len(parts) < 2:
        return None
    a_in, a_out = parts[0].strip(), parts[1].strip()
    region_start = h.start()
    notif = None
    if region_start > 0:
        pls = line_start(src, region_start - 1)
        prev = m[pls:region_start]
        if re.match(r"\s*updatePatcherNotification\(.*signingApkLabel.*\)\s*$", prev):
            notif = src[pls:region_start].strip()
            region_start = pls
    lines = [
        "val skipSigning = prefs.skipApkSigning.get()",
        "if (skipSigning) {",
        "    if (SplitApkPreparer.isSplitArchive(inputFile)) {",
        '        args.logger.warn("Signing skipped, but the input was a split bundle: merging already cleared the original META-INF, so there is nothing to restore")',
        "        %s.copyTo(%s, overwrite = true)" % (a_in, a_out),
        "    } else {",
        '        args.logger.info("Signing skipped by user preference, restoring original META-INF")',
        "        SignatureRestorer.restore(inputFile, %s, %s)" % (a_in, a_out),
        "    }",
        "} else {",
    ]
    if notif:
        lines.append("    " + notif)
    lines += ["    keystoreManager.sign(%s)" % src[h.start(2):h.end(2)], "}"]
    block = reindent("\n".join(lines), ind)
    return src[:region_start] + block + src[h.end():]


def worker_autoinstall(src):
    m = mask(src)
    if re.search(r"\bautoInstallPending\s*=\s*!skipSigning\s*&&", m):
        return src
    decl = re.search(r"^([ \t]*)val\s+skipSigning\b", m, re.M)
    hits = list(re.finditer(r"^([ \t]*)autoInstallPending\s*=\s*installerManager\.autoInstallAllowed\(", m, re.M))
    if not decl or len(hits) != 1:
        return None
    h = hits[0]
    if len(h.group(1)) < len(decl.group(1)):
        return None
    pos = m.index("installerManager.autoInstallAllowed(", h.start())
    return src[:pos] + "!skipSigning && " + src[pos:]


# ---------------------------------------------------------------- AppDialog

def appdialog_edit(src):
    m = mask(src)
    hits = list(re.finditer(r"\.widthIn\(max\s*=[^\n]*\)[ \t]*\n[ \t]*\.fillMaxHeight\(\)", m))
    if not hits:
        ok = re.search(r"\.fillMaxSize\(\)\s*,\s*horizontalAlignment\s*=\s*Alignment\.CenterHorizontally", m)
        return src if ok else None
    if len(hits) != 1:
        return None
    h = hits[0]
    return src[:h.start()] + ".fillMaxSize()" + src[h.end():]


# ---------------------------------------------------------------- SettingComponents

def _single_fun(src, name):
    m = mask(src)
    f = one(find_funs(m, name))
    return m, f


def comp_param(name):
    def fn(src):
        m, f = _single_fun(src, name)
        if not f:
            return None
        return add_last_param(src, m, f, "onLongClick: (() -> Unit)? = null,", r"\bonLongClick\b")
    return fn


def comp_optin(src):
    m, f = _single_fun(src, "SurfaceCard")
    if not f:
        return None
    st = annotation_start(src, f.start)
    header = src[st:f.start]
    if "ExperimentalFoundationApi" in header:
        return src
    mo = re.search(r"@OptIn\(([^)]*)\)", header)
    if mo:
        a = st + mo.start(1)
        b = st + mo.end(1)
        return src[:a] + src[a:b].rstrip() + ", ExperimentalFoundationApi::class" + src[b:]
    return src[:st] + indent_of(src, st) + "@OptIn(ExperimentalFoundationApi::class)\n" + src[st:]


def comp_click(src):
    m, f = _single_fun(src, "SurfaceCard")
    if not f or f.bo is None:
        return None
    body_m = m[f.bo:f.bc + 1]
    if "combinedClickable" in body_m:
        return src
    rx1 = re.compile(
        r"if\s*\(\s*onClick\s*!=\s*null\s*\)\s*\{(\s*)Modifier\.clickable\(\s*enabled\s*=\s*enabled\s*,\s*onClick\s*=\s*onClick\s*\)(\s*)\}\s*else\s*Modifier")
    h = rx1.search(body_m)
    if h:
        new = ("if (onClick != null || onLongClick != null) {" + h.group(1)
               + "Modifier.combinedClickable(enabled = enabled, onClick = onClick ?: {}, onLongClick = onLongClick)"
               + h.group(2) + "} else Modifier")
        a, b = f.bo + h.start(), f.bo + h.end()
        return src[:a] + new + src[b:]
    rx2 = re.compile(r"Modifier\.clickable\(\s*enabled\s*=\s*enabled\s*,\s*onClick\s*=\s*onClick\s*\)")
    h = rx2.search(body_m)
    if not h:
        return None
    note("SurfaceCard: click modifier shape changed, long-press works only when onClick is set")
    a, b = f.bo + h.start(), f.bo + h.end()
    return (src[:a] + "Modifier.combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)"
            + src[b:])


def comp_pass(src):
    m, f = _single_fun(src, "SettingsItemCard")
    if not f or f.bo is None:
        return None
    calls = find_calls(m, "SurfaceCard", f.bo, f.bc)
    if len(calls) != 1:
        return None
    return add_first_arg(src, m, calls[0], "onLongClick = onLongClick,", r"\bonLongClick\s*=")


COMPONENT_EDITS = [
    ("imports ExperimentalFoundationApi", imp("androidx.compose.foundation.ExperimentalFoundationApi"), False),
    ("imports combinedClickable", imp("androidx.compose.foundation.combinedClickable"), False),
    ("SurfaceCard onLongClick param", comp_param("SurfaceCard"), False),
    ("SurfaceCard OptIn", comp_optin, False),
    ("SurfaceCard click modifier", comp_click, False),
    ("SettingsItemCard onLongClick param", comp_param("SettingsItemCard"), False),
    ("SettingsItemCard passes onLongClick", comp_pass, False),
]


# ---------------------------------------------------------------- PatchCard

def card_params(src):
    m, f = _single_fun(src, "PatchCard")
    if not f:
        return None
    return add_last_param(
        src, m, f, "isFavorite: Boolean = false,\nonToggleFavorite: (() -> Unit)? = null,", r"\bisFavorite\b")


def card_longclick(src):
    m, f = _single_fun(src, "PatchCard")
    if not f or f.bo is None:
        return None
    calls = find_calls(m, "SettingsItemCard", f.bo, f.bc)
    if len(calls) != 1:
        return None
    return add_first_arg(
        src, m, calls[0], "onLongClick = favoriteLongClick(patch.isUniversal, onToggleFavorite),", r"\bfavoriteLongClick\b")


STAR = """if (patch.isUniversal && onToggleFavorite != null) {
    FavoriteStarButton(
        patchName = patch.displayName,
        isFavorite = isFavorite,
        onClick = onToggleFavorite
    )
}"""


def card_star(src):
    m, f = _single_fun(src, "PatchCard")
    if not f or f.bo is None:
        return None
    body = m[f.bo:f.bc + 1]
    if "FavoriteStarButton(" in body:
        return src
    h = re.search(r"\bif\s*\(\s*buildsClone\s*\)\s*\{", body)
    if h:
        ob = f.bo + h.end() - 1
        cb = match_close(m, ob)
        ind = indent_of(src, f.bo + h.start())
        return src[:cb + 1] + "\n" + reindent(STAR, ind) + src[cb + 1:]
    for lp in find_calls(m, "Text", f.bo, f.bc):
        rp = match_close(m, lp)
        if "patch.displayName" in src[lp:rp]:
            ind = indent_of(src, lp)
            note("PatchCard: badge block not found, star placed after the name text")
            return src[:rp + 1] + "\n" + reindent(STAR, ind) + src[rp + 1:]
    return None


# ---------------------------------------------------------------- ExpertModeDialog

def _dialog_step(cur):
    m = mask(cur)
    calls = [lp for lp in find_calls(m, "PatchCard") if not re.search(r"\bisFavorite\b", m[lp:match_close(m, lp)])]
    if not calls:
        return cur, False
    lp = calls[0]
    f = enclosing_fun(m, lp)
    if not f:
        return None, False
    body = m[f.bo:f.bc + 1]
    if not re.search(r"\bval\s+patchFavorites\s*=\s*rememberFavoritePatches\(\)", body):
        if re.search(r"\bpatchFavorites\b", body):
            return None, False
        mm = re.match(r"(?:[ \t]*\n)+", cur[f.bo + 1:f.bc])
        if not mm:
            return None, False
        s = f.bo + 1 + mm.end()
        ind = indent_of(cur, s)
        cur = cur[:s] + ind + "val patchFavorites = rememberFavoritePatches()\n" + cur[s:]
        return cur, True
    groups = find_calls(m, "rememberPatchGroups", f.bo, f.bc)
    if groups and "favoritesFirst" not in cur[groups[0]:match_close(m, groups[0])]:
        g = groups[0]
        gr = match_close(m, g)
        am = m[g + 1:gr]
        mm = re.search(r"(\bpatches\s*=\s*)([^,\n]+?)(\s*,)", am)
        if mm:
            expr = cur[g + 1 + mm.start(2):g + 1 + mm.end(2)]
            lam = re.search(r"\binfoOf\s*=\s*(\{[^\n]*\})", am)
            lam_txt = cur[g + 1 + lam.start(1):g + 1 + lam.end(1)] if lam else "{ (patch, _) -> patch }"
            new = ("patches = remember(" + expr + ", patchFavorites) { patchFavorites.favoritesFirst(" + expr + ") "
                   + lam_txt + " }")
            a, b = g + 1 + mm.start(1), g + 1 + mm.end(2)
            return cur[:a] + new + cur[b:], True
        note("ExpertModeDialog: rememberPatchGroups arguments changed, favorites will not float to the top")
    elif not groups:
        note("ExpertModeDialog: rememberPatchGroups call not found, favorites will not float to the top")
    rp = match_close(m, lp)
    pm = re.search(r"\bpatch\s*=\s*(\w+)", m[lp + 1:rp])
    if not pm:
        return None, False
    pv = pm.group(1)
    text = "isFavorite = patchFavorites.isFavorite(%s),\nonToggleFavorite = { patchFavorites.toggle(%s) }," % (pv, pv)
    r = add_first_arg(cur, m, lp, text, r"\bisFavorite\b")
    return r, r is not None


def make_dialog_edit(helper_pkg):
    def fn(src):
        cur = src
        if not find_calls(mask(cur), "PatchCard"):
            return None
        for _ in range(60):
            nxt, progressed = _dialog_step(cur)
            if nxt is None:
                return None
            cur = nxt
            if not progressed:
                break
        else:
            return None
        cur = ensure_import(cur, helper_pkg + ".rememberFavoritePatches")
        cur = ensure_import(cur, "androidx.compose.runtime.remember") if cur is not None else None
        return cur
    return fn


# ---------------------------------------------------------------- new files

SIGNATURE_RESTORER = r'''package @@PKG@@

import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object SignatureRestorer {
    private const val BLOCK_MAGIC = "APK Sig Block 42"

    fun restore(original: File, patched: File, output: File) {
        val merged = File.createTempFile("metainf-restored-", ".apk", patched.parentFile)
        try {
            mergeOriginalMetaInf(original, patched, merged)
            val block = readSigningBlock(original)
            if (block == null) {
                merged.copyTo(output, overwrite = true)
            } else {
                graftSigningBlock(block, merged, output)
            }
        } finally {
            merged.delete()
        }
    }

    private fun mergeOriginalMetaInf(original: File, patched: File, output: File) {
        val metaInf = LinkedHashMap<String, ByteArray>()
        ZipFile(original).use { src ->
            src.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("META-INF/") }
                .forEach { metaInf[it.name] = src.getInputStream(it).readBytes() }
        }
        ZipFile(patched).use { src ->
            ZipOutputStream(BufferedOutputStream(output.outputStream())).use { zos ->
                src.entries().asSequence()
                    .filter { !it.isDirectory && it.name !in metaInf }
                    .forEach { entry ->
                        val outEntry = ZipEntry(entry.name)
                        if (entry.method == ZipEntry.STORED) {
                            outEntry.method = ZipEntry.STORED
                            outEntry.size = entry.size
                            outEntry.crc = entry.crc
                        }
                        zos.putNextEntry(outEntry)
                        src.getInputStream(entry).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                metaInf.forEach { (name, bytes) ->
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }
    }

    private fun readSigningBlock(apk: File): ByteArray? {
        RandomAccessFile(apk, "r").use { f ->
            val eocd = findEocd(f) ?: return null
            val cdOffset = readU32(f, eocd + 16)
            if (cdOffset < 32 || cdOffset > f.length()) return null
            f.seek(cdOffset - 16)
            val magic = ByteArray(16)
            f.readFully(magic)
            if (String(magic, Charsets.US_ASCII) != BLOCK_MAGIC) return null
            val size = readU64(f, cdOffset - 24)
            val blockStart = cdOffset - 8 - size
            if (blockStart < 0) return null
            if (readU64(f, blockStart) != size) return null
            f.seek(blockStart)
            val block = ByteArray((size + 8).toInt())
            f.readFully(block)
            return block
        }
    }

    private fun graftSigningBlock(block: ByteArray, zip: File, out: File) {
        RandomAccessFile(zip, "r").use { f ->
            val eocd = findEocd(f) ?: run {
                zip.copyTo(out, overwrite = true)
                return
            }
            val cdOffset = readU32(f, eocd + 16)
            val total = f.length()
            out.outputStream().buffered().use { o ->
                copyRange(f, o, 0, cdOffset)
                o.write(block)
                copyRange(f, o, cdOffset, eocd)
                val tail = ByteArray((total - eocd).toInt())
                f.seek(eocd)
                f.readFully(tail)
                ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(16, (cdOffset + block.size).toInt())
                o.write(tail)
            }
        }
    }

    private fun findEocd(f: RandomAccessFile): Long? {
        val len = f.length()
        val start = maxOf(0L, len - 22 - 65535)
        val buf = ByteArray((len - start).toInt())
        f.seek(start)
        f.readFully(buf)
        for (i in buf.size - 22 downTo 0) {
            if (buf[i].toInt() == 0x50 &&
                buf[i + 1].toInt() == 0x4b &&
                buf[i + 2].toInt() == 0x05 &&
                buf[i + 3].toInt() == 0x06
            ) {
                return start + i
            }
        }
        return null
    }

    private fun readU32(f: RandomAccessFile, pos: Long): Long {
        val b = ByteArray(4)
        f.seek(pos)
        f.readFully(b)
        var v = 0L
        for (i in 3 downTo 0) {
            v = (v shl 8) or (b[i].toInt() and 0xff).toLong()
        }
        return v
    }

    private fun readU64(f: RandomAccessFile, pos: Long): Long {
        val b = ByteArray(8)
        f.seek(pos)
        f.readFully(b)
        var v = 0L
        for (i in 7 downTo 0) {
            v = (v shl 8) or (b[i].toInt() and 0xff).toLong()
        }
        return v
    }

    private fun copyRange(f: RandomAccessFile, out: OutputStream, from: Long, to: Long) {
        f.seek(from)
        var remaining = to - from
        val buf = ByteArray(64 * 1024)
        while (remaining > 0) {
            val read = f.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (read < 0) break
            out.write(buf, 0, read)
            remaining -= read
        }
    }
}
'''

FAVORITE_PATCHES = r'''package @@PKG@@

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import @@NS@@.R
import @@PREFS_PKG@@.PreferencesManager
import @@PATCHINFO_PKG@@.PatchInfo
import @@TOAST_PKG@@.toast
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Stable
internal class FavoritePatches(
    val names: Set<String>,
    private val onToggle: (PatchInfo) -> Unit
) {
    fun isFavorite(patch: PatchInfo): Boolean =
        patch.isUniversal && (patch.name in names || patch.displayName in names)

    fun toggle(patch: PatchInfo) = onToggle(patch)

    fun <T> favoritesFirst(items: List<T>, infoOf: (T) -> PatchInfo): List<T> =
        if (names.isEmpty()) items else items.sortedByDescending { isFavorite(infoOf(it)) }
}

@Composable
internal fun rememberFavoritePatches(): FavoritePatches {
    val prefs: PreferencesManager = koinInject()
    val names by prefs.favoriteUniversalPatches.getAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(names) {
        FavoritePatches(names) { patch ->
            val wasFavorite = patch.name in names || patch.displayName in names
            val updated = if (wasFavorite) names - patch.name - patch.displayName else names + patch.name
            scope.launch { prefs.favoriteUniversalPatches.update(updated) }
            context.toast(
                context.getString(
                    if (wasFavorite) R.string.expert_mode_favorite_removed else R.string.expert_mode_favorite_added,
                    patch.displayName
                )
            )
        }
    }
}

@Composable
internal fun favoriteLongClick(enabled: Boolean, onToggle: (() -> Unit)?): (() -> Unit)? {
    val haptic = LocalHapticFeedback.current
    if (!enabled || onToggle == null) return null
    return {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onToggle()
    }
}

@Composable
internal fun FavoriteStarButton(
    patchName: String,
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionLabel = stringResource(
        if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites
    )
    val description = "$patchName, $actionLabel"
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(28.dp)
            .semantics { contentDescription = description }
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
'''


# ---------------------------------------------------------------- workspace

class Workspace:
    def __init__(self, root):
        self.root = root
        self.main = root / "app" / "src" / "main"
        self.orig = {}
        self.cur = {}
        self.fmt = {}
        for p in sorted(self.main.rglob("*.kt")):
            self.load(p)

    def load(self, p):
        raw = p.read_bytes().decode("utf-8")
        bom = raw.startswith(BOM)
        if bom:
            raw = raw[1:]
        crlf = "\r\n" in raw
        text = raw.replace("\r\n", "\n")
        self.orig[p] = text
        self.cur[p] = text
        self.fmt[p] = (bom, crlf)

    def ensure(self, p):
        if p not in self.cur and p.is_file():
            self.load(p)
        return p in self.cur

    def get(self, p):
        return self.cur[p]

    def set(self, p, text):
        self.cur[p] = text

    def kt_paths(self):
        return [p for p in self.cur if p.suffix == ".kt"]

    def add_new(self, p, text):
        self.orig.setdefault(p, None)
        self.cur[p] = text
        self.fmt.setdefault(p, (False, False))

    def find(self, rx, hint=None, exclude=(), multi=False):
        flags = re.M
        if hint is not None and not multi:
            h = self.root / hint
            if self.ensure(h) and re.search(rx, mask(self.cur[h]), flags):
                return [h]
        hits = [p for p in self.kt_paths()
                if p not in exclude and self.orig.get(p) is not None and re.search(rx, mask(self.orig[p]), flags)]
        return hits

    def write(self, p):
        text = self.cur[p]
        bom, crlf = self.fmt[p]
        if crlf:
            text = text.replace("\n", "\r\n")
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(((BOM if bom else "") + text).encode("utf-8"))


P = "app/src/main/java/app/morphe/manager/"


def build_plan(ws):
    problems = []
    plan = []

    def locate(label, rx, hint, multi=False, exclude=()):
        hits = ws.find(rx, hint, exclude, multi)
        if not hits:
            problems.append("cannot locate %s (%s)" % (label, rx))
            return [] if multi else None
        if multi:
            return hits
        if len(hits) != 1:
            problems.append("ambiguous %s: %s" % (label, ", ".join(str(h.relative_to(ws.root)) for h in hits)))
            return None
        return hits[0]

    prefs = locate("PreferencesManager", r"^class\s+PreferencesManager\b", P + "domain/manager/PreferencesManager.kt")
    vm = locate("SettingsViewModel", r"^class\s+SettingsViewModel\b", P + "ui/viewmodel/SettingsViewModel.kt")
    adv = locate("AdvancedTabContent", r"\bfun\s+AdvancedTabContent\s*\(", P + "ui/screen/settings/AdvancedTabContent.kt")
    worker = locate("PatcherWorker", r"^class\s+PatcherWorker\b", P + "patcher/worker/PatcherWorker.kt")
    card = locate("PatchCard", r"\bfun\s+PatchCard\s*\(", P + "ui/screen/home/ExpertPatchCard.kt")
    appdialog = locate("AppDialog", r"\bfun\s+AppDialog\s*\(", P + "ui/screen/shared/AppDialog.kt")
    comps = locate("SurfaceCard", r"\bfun\s+SurfaceCard\s*\(", P + "ui/screen/shared/SettingComponents.kt")
    dialogs = locate("PatchCard call sites", r"(?<![\w.])PatchCard\s*\(", P + "ui/screen/home/ExpertModeDialog.kt",
                     multi=True, exclude=(card,) if card else ())
    stripper = ws.find(r"\bobject\s+NativeLibStripper\b", P + "patcher/util/NativeLibStripper.kt")
    patchinfo = ws.find(r"\bclass\s+PatchInfo\b", P + "patcher/patch/PatchInfo.kt")
    toast = ws.find(r"\bfun\s+Context\.toast\s*\(", P + "util/Util.kt")

    strings = ws.main / "res" / "values" / "strings.xml"
    gradle = ws.root / "app" / "build.gradle.kts"
    gservices = ws.root / "app" / "google-services.json"
    for p in (strings, gradle):
        if not p.is_file():
            problems.append("missing " + str(p.relative_to(ws.root)))
    if problems:
        return None, problems
    ws.load(strings)
    ws.load(gradle)
    has_gservices = gservices.is_file()
    if has_gservices:
        ws.load(gservices)

    ns = re.search(r'\bnamespace\s*=\s*"([^"]+)"', ws.get(gradle))
    idm = re.search(r'\bapplicationId\s*=\s*"([^"]+)"', ws.get(gradle))
    upstream = idm.group(1) if idm and idm.group(1) != FORK_APP_ID else DEFAULT_UPSTREAM_APP_ID
    if not ns:
        return None, ["namespace not found in app/build.gradle.kts"]
    if not (stripper and patchinfo and toast):
        missing = [n for n, v in (("NativeLibStripper", stripper), ("PatchInfo", patchinfo), ("Context.toast", toast)) if not v]
        return None, ["cannot locate " + ", ".join(missing)]

    util_dir = stripper[0].parent
    util_pkg = package_of(ws.get(stripper[0]))
    helper_dir = card.parent
    helper_pkg = package_of(ws.get(card))
    fmt = {
        "PKG": None,
        "NS": ns.group(1),
        "PREFS_PKG": package_of(ws.get(prefs)),
        "PATCHINFO_PKG": package_of(ws.get(patchinfo[0])),
        "TOAST_PKG": package_of(ws.get(toast[0])),
    }

    def render(tpl, pkg):
        out = tpl.replace("@@PKG@@", pkg)
        for k, v in fmt.items():
            if v:
                out = out.replace("@@%s@@" % k, v)
        return out

    plan.append(("res/values/strings.xml", strings, [
        ("favorite strings", strings_edit(FAVORITE_STRINGS, "expert_mode_universal_patches"), False),
        ("skip signing strings", strings_edit(SIGNING_STRINGS, "settings_advanced_strip_unused_libs_description"), False),
        ("app name", app_name_edit, False),
    ]))
    plan.append(("app/build.gradle.kts", gradle, [("applicationId", app_id_edit, False)]))
    if has_gservices:
        plan.append(("app/google-services.json", gservices, [("package_name", gservices_edit(upstream), False)]))
    plan.append((str(prefs.relative_to(ws.root)), prefs, PREFS_EDITS))
    plan.append((str(vm.relative_to(ws.root)), vm, [("setSkipApkSigning", vm_edit, False)]))
    plan.append((str(adv.relative_to(ws.root)), adv, [
        ("skip signing state", adv_state, False),
        ("skip signing switch", adv_switch, False),
        ("import LockOpen", imp("androidx.compose.material.icons.outlined.LockOpen"), False),
    ]))
    plan.append((str(worker.relative_to(ws.root)), worker, [
        ("import SignatureRestorer", imp(util_pkg + ".SignatureRestorer"), False),
        ("signing branch", worker_sign, False),
        ("auto-install guard", worker_autoinstall, False),
    ]))
    plan.append((str(appdialog.relative_to(ws.root)), appdialog, [("dialog fills full size", appdialog_edit, False)]))
    plan.append((str(comps.relative_to(ws.root)), comps, COMPONENT_EDITS))
    plan.append((str(card.relative_to(ws.root)), card, [
        ("PatchCard params", card_params, False),
        ("PatchCard long-press", card_longclick, False),
        ("PatchCard star", card_star, False),
    ]))
    for d in dialogs:
        plan.append((str(d.relative_to(ws.root)), d, [("favorites wiring", make_dialog_edit(helper_pkg), False)]))

    new_files = [
        (util_dir / "SignatureRestorer.kt", render(SIGNATURE_RESTORER, util_pkg)),
        (helper_dir / "FavoritePatches.kt", render(FAVORITE_PATCHES, helper_pkg)),
    ]
    needles = [
        (r"\bval\s+skipApkSigning\s*=", prefs, "skipApkSigning preference"),
        (r"\bval\s+favoriteUniversalPatches\s*=", prefs, "favoriteUniversalPatches preference"),
        (r"\bfun\s+setSkipApkSigning\b", vm, "setSkipApkSigning"),
    ]
    return (plan, new_files, adv, needles), []


def verify(ws, changed, strings_path, needles):
    errors = []
    for p in changed:
        if p.suffix == ".kt" and not balanced(mask(ws.get(p))):
            errors.append("%s: unbalanced brackets after edit" % p.relative_to(ws.root))
    strings_text = ws.get(strings_path)
    for p in changed:
        if p.suffix != ".kt":
            continue
        for name in set(re.findall(r"R\.string\.(\w+)", mask(ws.get(p)))):
            if name in OUR_STRINGS and 'name="%s"' % name not in strings_text:
                errors.append("%s: R.string.%s not defined" % (p.relative_to(ws.root), name))
    for rx, path, what in needles:
        if not re.search(rx, mask(ws.get(path))):
            errors.append("missing " + what)
    return errors


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("source", nargs="?", default=".", help="morphe-manager repo root (default: cwd)")
    ap.add_argument("--check", action="store_true", help="dry run, no changes written")
    args = ap.parse_args()

    root = pathlib.Path(args.source).resolve()
    if not (root / "app" / "src" / "main").is_dir():
        print("error: %s doesn't look like a morphe-manager checkout (no app/src/main)" % root, file=sys.stderr)
        sys.exit(1)

    ws = Workspace(root)
    built, problems = build_plan(ws)
    if problems:
        for pr in problems:
            print("[!] " + pr, file=sys.stderr)
        sys.exit(1)
    plan, new_files, adv, needles = built

    failed = []
    status = {}
    for label, path, edits in plan:
        cur = ws.get(path)
        bad = []
        for desc, fn, optional in edits:
            r = fn(cur)
            if r is None:
                if optional:
                    note("%s: '%s' anchor not found, skipped" % (label, desc))
                else:
                    bad.append(desc)
            else:
                cur = r
        if bad:
            failed.append(label)
            print("[!] %s: no matching anchor for: %s" % (label, ", ".join(bad)), file=sys.stderr)
            status[path] = "failed"
            continue
        ws.set(path, cur)
        status[path] = "pending" if cur != ws.orig[path] else "applied"

    if not failed:
        fix_duplicate_strip_toggle(ws, adv)
        if ws.get(adv) != ws.orig[adv]:
            status[adv] = "pending"

    new_status = {}
    for path, text in new_files:
        old = path.read_text(encoding="utf-8").replace("\r\n", "\n") if path.is_file() else None
        ws.add_new(path, text)
        new_status[path] = "applied" if old == text else ("pending-new" if old is None else "pending")

    if failed:
        print("\n%d file(s) failed: %s\nnothing written." % (len(failed), ", ".join(failed)), file=sys.stderr)
        sys.exit(1)

    changed = [p for p, s in list(status.items()) + list(new_status.items()) if s.startswith("pending")]
    errors = verify(ws, changed, ws.main / "res" / "values" / "strings.xml", needles)
    if errors:
        for e in errors:
            print("[!] " + e, file=sys.stderr)
        print("\nnothing written.", file=sys.stderr)
        sys.exit(1)

    for p, s in list(status.items()) + list(new_status.items()):
        rel = p.relative_to(root)
        if s == "applied":
            print("[=] %s: already applied" % rel)
        elif args.check:
            print("[ ] %s: pending" % rel)
        else:
            ws.write(p)
            print("[+] %s: %s" % (rel, "created" if s == "pending-new" else "applied"))
    for n in NOTES:
        print("[~] " + n)

    if args.check:
        sys.exit(1 if changed else 0)
    print("\ndone.")


if __name__ == "__main__":
    main()
