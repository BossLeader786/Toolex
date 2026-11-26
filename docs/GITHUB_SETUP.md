# GitHub-only setup (koi laptop nahi chahiye)

## 1. APK kahan milega

| Kahan | Kab |
|---|---|
| **Releases** ([latest](https://github.com/BossLeader786/Toolex/releases/latest)) | jab bhi `v*` tag push ho |
| **Actions → Toolex build → Artifacts → toolex-debug-apk** | har push / PR par |
| **Actions tab → Run workflow** | jab manually build chahiye |

Release link hamesha same format ka hota hai, isliye bookmark kar sakte ho:
`https://github.com/BossLeader786/Toolex/releases/latest/download/Toolex-v1.0.0-debug.apk`

## 2. Install page (QR) — ek baar ka toggle

Build ke baad APK + QR wala page `gh-pages` branch me chala jata hai. Use live
karne ke liye sirf ek setting:

**Repo → Settings → Pages → Build and deployment**

* Source: **Deploy from a branch**
* Branch: **gh-pages** · Folder: **/ (root)** → **Save**

1-2 minute me live: `https://bossleader786.github.io/Toolex/`
(Token se ye setting enable nahi ho sakti — GitHub admin permission maangta hai.)

## 3. Naya version release karna

```bash
git tag -a v1.1.0 -m "Toolex 1.1.0"
git push origin v1.1.0
```

Workflow khud: tests → engine verification → APK → **Release** → pages update.

`app/build.gradle.kts` me `versionCode`/`versionName` badalna na bhoolein.

## 4. Proper signing (optional, Play Store ke liye zaroori)

1. Apne PC par ek baar keystore banao:
   ```bash
   keytool -genkey -v -keystore toolex.jks -keyalg RSA -keysize 2048 \
           -validity 10000 -alias toolex
   ```
2. Base64 me badlo: `base64 -w0 toolex.jks > toolex.jks.b64`
3. Repo → Settings → Secrets and variables → Actions me add karo:

| Secret | Value |
|---|---|
| `TOOLEX_KEYSTORE_B64` | `toolex.jks.b64` ka content |
| `TOOLEX_STORE_PASSWORD` | keystore password |
| `TOOLEX_KEY_ALIAS` | `toolex` |
| `TOOLEX_KEY_PASSWORD` | key password |

Secrets na hone par bhi build chalta hai (debug-signed APK), bas Play Store par
upload nahi hoga.

## 5. CI kya kya check karta hai

1. `assembleRelease` — bytecode check: minSdk 24 se naye Android APIs use hue to fail
2. `testDebugUnitTest` — page math, size parsing, file naming, ladder order
3. `assembleDebug` — jo APK aap install karte ho
4. `tools/verify_pdf.py` — 288 layout cases original Flask app se match + PDFs pypdf se valid

Fail hone par poora log **commit comment** me bhi chala jata hai (Actions log
download har network se accessible nahi hota).
