# CI Pipeline Proposal: GitHub Actions

## Recommendation: GitHub Actions

**Rationale:**
1. **No additional cost** — 2,000 free minutes/month for private repos, unlimited for public
2. **Native Android support** — ubuntu-latest runners have Android SDK pre-installed
3. **Wide emulator support** — `reactivecircus/android-emulator-runner` is a mature action
4. **Matrix builds** — easy to run across multiple API levels simultaneously
5. **Built-in secrets management** — no external vault needed

---

## Pipeline 1: Unit Tests (on every push/PR)

```yaml
name: Unit Tests

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  unit-tests:
    name: Unit Tests
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Cache Gradle dependencies
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
          restore-keys: |
            ${{ runner.os }}-gradle-

      - name: Cache Android SDK
        uses: actions/cache@v4
        with:
          path: /usr/local/lib/android/sdk
          key: ${{ runner.os }}-android-sdk-${{ env.ANDROID_SDK_VERSION }}

      - name: Create keystore.properties for build
        run: |
          mkdir -p app/keystore
          echo "keyAlias=release" > app/keystore.properties
          echo "keyPassword=release" >> app/keystore.properties
          echo "storeFile=keystore/release.keystore" >> app/keystore.properties
          echo "storePassword=release" >> app/keystore.properties
        # Note: The actual release keystore must NOT be committed.
        # CI builds use debug signing (debug build type has no signing config).
        # For release builds, store the keystore as a base64 secret.

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Run unit tests
        run: ./gradlew test --no-daemon

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: unit-test-reports
          path: app/build/reports/tests/
```

---

## Pipeline 2: Espresso Tests (scheduled + manual trigger)

```yaml
name: Espresso Tests

on:
  schedule:
    # Run nightly at 2 AM UTC
    - cron: '0 2 * * *'
  workflow_dispatch:
    # Allow manual trigger from GitHub UI
    inputs:
      api_level:
        description: 'Android API level'
        required: true
        default: '34'
        type: choice
        options:
          - '28'
          - '30'
          - '32'
          - '34'

jobs:
  espresso-tests:
    name: Espresso (API ${{ inputs.api_level || 34 }})
    runs-on: ubuntu-latest

    strategy:
      matrix:
        api-level: [28, 30, 32, 34]
        target: [default]
      # For scheduled runs, test all API levels
      # For manual runs, use the selected API level
      max-parallel: 2

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Run Espresso tests (API ${{ matrix.api-level }})
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: ${{ matrix.api-level }}
          target: ${{ matrix.target }}
          arch: x86_64
          emulator-options: -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none -camera-front none
          disable-animations: true
          emulator-build: 11024963
          script: |
            ./gradlew connectedCheck --no-daemon

      - name: Upload Espresso test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: espresso-reports-api-${{ matrix.api-level }}
          path: |
            app/build/reports/androidTests/
            app/build/outputs/androidTest-results/
```

---

## Pipeline 3: Release Build (on tag)

```yaml
name: Release Build

on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    name: Release Build
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Decode keystore
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
        run: |
          mkdir -p app/keystore
          echo "$KEYSTORE_BASE64" | base64 -d > app/keystore/release.keystore

      - name: Create keystore.properties
        run: |
          echo "keyAlias=${{ secrets.KEYSTORE_ALIAS }}" > app/keystore.properties
          echo "keyPassword=${{ secrets.KEYSTORE_PASSWORD }}" >> app/keystore.properties
          echo "storeFile=keystore/release.keystore" >> app/keystore.properties
          echo "storePassword=${{ secrets.KEYSTORE_STORE_PASSWORD }}" >> app/keystore.properties

      - name: Run unit tests
        run: ./gradlew test --no-daemon

      - name: Build release APK
        run: ./gradlew assembleRelease --no-daemon

      - name: Build release AAB
        run: ./gradlew bundleRelease --no-daemon

      - name: Sign APK
        run: |
          jarsigner -keystore app/keystore/release.keystore \
            -storepass "${{ secrets.KEYSTORE_STORE_PASSWORD }}" \
            app/build/outputs/apk/release/app-release-unsigned.apk \
            "${{ secrets.KEYSTORE_ALIAS }}"

      - name: Upload release artifacts
        uses: actions/upload-artifact@v4
        with:
          name: release-artifacts
          path: |
            app/build/outputs/apk/release/*.apk
            app/build/outputs/bundle/release/*.aab
```

---

## Required Secrets

| Secret Name | Purpose | Example |
|------------|---------|---------|
| `KEYSTORE_BASE64` | Release keystore as base64 | `base64 -w0 release.keystore` output |
| `KEYSTORE_ALIAS` | Key alias in keystore | `release` |
| `KEYSTORE_PASSWORD` | Key password | (generated during keystore creation) |
| `KEYSTORE_STORE_PASSWORD` | Keystore password | (generated during keystore creation) |

**Setting them up:**
```bash
# Generate base64 of keystore
base64 -w0 app/keystore/release.keystore | pbcopy  # macOS
# Then paste into GitHub repo → Settings → Secrets and variables → Actions

# The keystore.properties content for CI is generated in the workflow, not committed.
```

---

## Cost and Performance

| Factor | Estimate | Notes |
|--------|----------|-------|
| **Unit test runtime** | ~3 min | Full gradle build + test execution |
| **Emulator boot time** | ~2-3 min | On ubuntu-latest with x86_64 KVM |
| **Espresso test runtime** | ~5-8 min | Including boot, install, test execution |
| **Full matrix (4 API levels)** | ~20-30 min | Parallelized (max-parallel: 2) |
| **Monthly cost (public repo)** | $0 | Free for public repos + 2000 min private |
| **Monthly cost (private repo)** | $0 (within free tier) | 2000 min = ~100 runs of full pipeline |

**Strategies to stay within free tier:**
1. Run unit tests on every push/PR (essential, fast, cheap)
2. Run Espresso tests only on develop/main pushes + nightly schedule
3. Use API level 34 only for PR verification, full matrix on nightly
4. Cache Gradle wrapper + dependencies to reduce build time by ~60%

---

## How to Enable

1. In GitHub repo: Settings → Actions → General → Allow all actions
2. Create `.github/workflows/unit-tests.yml`
3. Create `.github/workflows/espresso-tests.yml`
4. For release builds: add secrets and create `.github/workflows/release.yml`
5. Test with a push to a feature branch — the workflow will appear under the Actions tab
