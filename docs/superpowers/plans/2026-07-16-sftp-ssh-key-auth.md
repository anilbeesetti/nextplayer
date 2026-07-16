# SFTP and SSH-Key Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Every implementer must also use superpowers:test-driven-development; the controller uses superpowers:dispatching-parallel-agents only for the explicitly independent waves below.

**Goal:** Add secure, seekable SFTP browsing and playback with password or imported SSH-key authentication, pinned host fingerprints, reliable key lifecycle management, and disposable-emulator validation.

**Architecture:** Extend the persisted network model with SFTP/authentication metadata, use SSHJ 0.40.0 behind the existing `NetworkClient` contract, and keep key bytes in app-private staged/committed directories. Convert the network client factory to an injected singleton, surface unknown host keys as a typed confirmation state, and retain the local HTTP proxy for Media3 range reads.

**Tech Stack:** Kotlin 2.4.10, Android/Compose Material 3, Room 2.8.4, Hilt 2.60.1, SSHJ 0.40.0, coroutines, JUnit 4, AndroidX Room migration testing, adb/uiautomator, disposable OpenSSH container.

## Global Constraints

- SFTP is a distinct `NetworkProtocol.SFTP` with default port `22`; do not treat FTP as SFTP.
- SFTP supports both `PASSWORD` and `SSH_KEY`; SSH keys are never used by SMB, FTP, or WebDAV.
- Use `com.hierynomus:sshj:0.40.0`; never add `PromiscuousVerifier` or any unconditional host-key acceptance.
- The minimum Android SDK remains 23; avoid APIs that require a higher SDK without existing desugaring.
- Store only a generated key filename in Room; never persist a source URI, source path, absolute internal path, or key bytes.
- A staged key is committed only after connection and host verification succeed. Failed authentication retains the staged key for retry; cancellation deletes it.
- Existing committed keys survive failed edits. Replace/delete them only after the corresponding Room mutation succeeds.
- Room version becomes 8; migrated connections default to `PASSWORD` with empty key/passphrase/fingerprint fields.
- First use requires explicit SHA-256 fingerprint confirmation; a changed saved fingerprint is rejected and never updated automatically.
- Streaming owns a fresh SSH/SFTP connection and remote handle per stream/range lifecycle.
- All new production behavior follows red-green-refactor. Every test must be observed failing for the intended reason before implementation.
- Disposable-emulator QA is required and must retain sanitized evidence, then delete the AVD, server, keys, credentials, and fixtures.

## Coordination and Parallel Waves

1. Run Task 1 alone to establish shared names and dependency aliases.
2. Create and boot the task-specific disposable AVD, reserved exclusively for Task 2's migration test until final QA.
3. Run Tasks 2, 3, and 4 in parallel. Their file ownership does not overlap.
4. Run Tasks 5 and 6 in parallel after Tasks 2–4 pass review.
5. Run Task 7 after Tasks 5–6 are integrated.
6. Run Task 8 as the serial acceptance gate while final code review and dependency inspection run in parallel.

No implementation agent may edit the plan or another task's owned files. If a required cross-task signature differs from this plan, stop and report `NEEDS_CONTEXT` rather than changing both sides.

---

### Task 1: Shared model and dependency aliases

**Files:**

- Modify: `core/model/src/main/java/dev/anilbeesetti/nextplayer/core/model/NetworkConnection.kt`
- Modify: `core/model/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `core/model/src/test/java/dev/anilbeesetti/nextplayer/core/model/NetworkConnectionTest.kt`

**Interfaces:**

- Produces: `NetworkProtocol.SFTP`, `NetworkAuthentication.PASSWORD`, `NetworkAuthentication.SSH_KEY`.
- Produces on `NetworkConnection`: `authentication`, `privateKeyFileName`, `privateKeyPassphrase`, `hostKeyFingerprint`.
- Produces catalog aliases: `libs.sshj`, `libs.androidx.room.testing`.

- [ ] **Step 1: Add the failing model test**

```kotlin
package dev.anilbeesetti.nextplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkConnectionTest {
    @Test
    fun `SFTP defaults to port 22`() {
        assertEquals(22, NetworkProtocol.SFTP.defaultPort)
    }

    @Test
    fun `network connection defaults to password authentication`() {
        assertEquals(NetworkAuthentication.PASSWORD, NetworkConnection.sample.authentication)
        assertEquals("", NetworkConnection.sample.privateKeyFileName)
        assertEquals("", NetworkConnection.sample.privateKeyPassphrase)
        assertEquals("", NetworkConnection.sample.hostKeyFingerprint)
    }
}
```

- [ ] **Step 2: Run the test and observe RED**

Run:

```bash
env ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew :core:model:test
```

Expected: compilation fails because `SFTP`, `NetworkAuthentication`, and the new properties do not exist.

- [ ] **Step 3: Add the model and test dependency**

Add to `NetworkConnection.kt`:

```kotlin
enum class NetworkProtocol(val defaultPort: Int) {
    SMB(445),
    FTP(21),
    SFTP(22),
    WEBDAV(80),
}

enum class NetworkAuthentication {
    PASSWORD,
    SSH_KEY,
}
```

Append these constructor properties after `useHttps`:

```kotlin
val authentication: NetworkAuthentication = NetworkAuthentication.PASSWORD,
val privateKeyFileName: String = "",
val privateKeyPassphrase: String = "",
val hostKeyFingerprint: String = "",
```

Add `testImplementation(libs.junit4)` to `core/model/build.gradle.kts`.

Add to the version catalog:

```toml
sshj = "0.40.0"

androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
sshj = { group = "com.hierynomus", name = "sshj", version.ref = "sshj" }
```

- [ ] **Step 4: Run GREEN and check formatting**

```bash
env ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew :core:model:test ktlintCheck
```

Expected: both model tests pass and formatting succeeds.

- [ ] **Step 5: Commit**

```bash
git add core/model gradle/libs.versions.toml
git commit -m "feat: add SFTP connection model"
```

---

### Task 2: Room migration and repository mappings

**Files:**

- Modify: `core/database/src/main/java/dev/anilbeesetti/nextplayer/core/database/entities/NetworkConnectionEntity.kt`
- Modify: `core/database/src/main/java/dev/anilbeesetti/nextplayer/core/database/MediaDatabase.kt`
- Modify: `core/database/src/main/java/dev/anilbeesetti/nextplayer/core/database/DatabaseModule.kt`
- Modify: `core/database/build.gradle.kts`
- Create: `core/database/src/androidTest/java/dev/anilbeesetti/nextplayer/core/database/MediaDatabaseMigrationTest.kt`
- Create: `core/database/schemas/dev.anilbeesetti.nextplayer.core.database.MediaDatabase/8.json`
- Modify: `core/data/src/main/java/dev/anilbeesetti/nextplayer/core/data/repository/LocalNetworkConnectionRepository.kt`
- Modify: `core/data/build.gradle.kts`
- Create: `core/data/src/test/java/dev/anilbeesetti/nextplayer/core/data/repository/LocalNetworkConnectionRepositoryTest.kt`

**Interfaces:**

- Consumes the exact Task 1 model fields.
- Produces Room columns `authentication`, `private_key_file_name`, `private_key_passphrase`, `host_key_fingerprint`.
- Produces `MediaDatabase.MIGRATION_7_8`.

- [ ] **Step 1: Write the failing migration test**

Configure `core/database/build.gradle.kts`:

```kotlin
sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
```

and:

```kotlin
androidTestImplementation(libs.androidx.room.testing)
```

Create a migration test that builds schema 7, inserts one legacy row, migrates, and asserts the new defaults:

```kotlin
@RunWith(AndroidJUnit4::class)
class MediaDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MediaDatabase::class.java,
    )

    @Test
    fun migrate7To8_preservesConnectionAndAddsPasswordDefaults() {
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL(
                "INSERT INTO network_connection " +
                    "(id,name,protocol,host,port,path,username,password,use_https,created_at) " +
                    "VALUES (1,'NAS','FTP','10.0.2.2',2121,'/media','alice','secret',0,123)",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 8, true, MediaDatabase.MIGRATION_7_8).use { db ->
            db.query("SELECT * FROM network_connection WHERE id = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("NAS", cursor.getString(cursor.getColumnIndexOrThrow("name")))
                assertEquals("PASSWORD", cursor.getString(cursor.getColumnIndexOrThrow("authentication")))
                assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("private_key_file_name")))
                assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("private_key_passphrase")))
                assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("host_key_fingerprint")))
            }
        }
    }

    private companion object { const val TEST_DB = "migration-test" }
}
```

- [ ] **Step 2: Run the migration test and observe RED**

Create the task-specific disposable AVD once and reserve serial `emulator-5580` for this plan:

```bash
printf 'no\n' | avdmanager create avd --force --name nextplayer-sftp-disposable --package "system-images;android-36.1;google_apis;arm64-v8a" --device pixel_6
/Users/anil/Library/Android/sdk/emulator/emulator -avd nextplayer-sftp-disposable -port 5580 -wipe-data -no-snapshot -no-boot-anim
adb -s emulator-5580 wait-for-device
```

Keep this AVD until Task 8, where it is wiped again for end-to-end QA and then deleted.

```bash
env ANDROID_HOME=/Users/anil/Library/Android/sdk ANDROID_SERIAL=emulator-5580 ./gradlew :core:database:connectedDebugAndroidTest
```

Expected: test compilation fails because version 8 and `MIGRATION_7_8` do not exist.

- [ ] **Step 3: Implement schema 8 and migration**

Add entity fields before `createdAt`:

```kotlin
@ColumnInfo(name = "authentication", defaultValue = "'PASSWORD'")
val authentication: String = "PASSWORD",
@ColumnInfo(name = "private_key_file_name", defaultValue = "''")
val privateKeyFileName: String = "",
@ColumnInfo(name = "private_key_passphrase", defaultValue = "''")
val privateKeyPassphrase: String = "",
@ColumnInfo(name = "host_key_fingerprint", defaultValue = "''")
val hostKeyFingerprint: String = "",
```

Set `MediaDatabase` version to `8` and add:

```kotlin
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `network_connection` ADD COLUMN `authentication` TEXT NOT NULL DEFAULT 'PASSWORD'")
        db.execSQL("ALTER TABLE `network_connection` ADD COLUMN `private_key_file_name` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `network_connection` ADD COLUMN `private_key_passphrase` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `network_connection` ADD COLUMN `host_key_fingerprint` TEXT NOT NULL DEFAULT ''")
    }
}
```

Register `MIGRATION_7_8` in `DatabaseModule`.

- [ ] **Step 4: Write the failing repository round-trip test**

Add `testImplementation(libs.kotlinx.coroutines.test)` to `core/data/build.gradle.kts`.

Use a hand-written `NetworkConnectionDao` fake backed by `MutableStateFlow<List<NetworkConnectionEntity>>`. Upsert this model and assert every field survives both directions:

```kotlin
val connection = NetworkConnection(
    name = "SFTP",
    protocol = NetworkProtocol.SFTP,
    host = "10.0.2.2",
    username = "alice",
    authentication = NetworkAuthentication.SSH_KEY,
    privateKeyFileName = "123.key",
    privateKeyPassphrase = "passphrase",
    hostKeyFingerprint = "SHA256:abc",
)
repository.upsert(connection)
assertEquals(connection.copy(id = 1), repository.getConnection(1))
```

Run:

```bash
env ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew :core:data:testDebugUnitTest
```

Expected: assertions fail because the repository does not map the four fields.

- [ ] **Step 5: Implement defensive repository mappings**

Map entity to model with:

```kotlin
authentication = runCatching { NetworkAuthentication.valueOf(authentication) }
    .getOrDefault(NetworkAuthentication.PASSWORD),
privateKeyFileName = privateKeyFileName,
privateKeyPassphrase = privateKeyPassphrase,
hostKeyFingerprint = hostKeyFingerprint,
```

Map model to entity with the corresponding values and `authentication.name`.

- [ ] **Step 6: Run GREEN and export schema 8**

```bash
env ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew :core:data:testDebugUnitTest :core:database:connectedDebugAndroidTest :core:database:assembleDebug
```

Expected: repository and migration tests pass; schema `8.json` is generated and versions 1–7 remain unchanged.

- [ ] **Step 7: Commit**

```bash
git add core/database core/data
git commit -m "feat: persist SFTP authentication settings"
```

---

### Task 3: App-private key store and host-key verification

**Files:**

- Modify: `core/media/build.gradle.kts`
- Modify: `core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/MediaModule.kt`
- Create: `core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/network/keys/SshKeyStore.kt`
- Create: `core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/network/keys/DefaultSshKeyStore.kt`
- Create: `core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/network/keys/SshKeyFiles.kt`
- Create: `core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/network/sftp/SftpHostKeyVerifier.kt`
- Create: `core/media/src/test/java/dev/anilbeesetti/nextplayer/core/media/network/keys/SshKeyFilesTest.kt`
- Create: `core/media/src/test/java/dev/anilbeesetti/nextplayer/core/media/network/sftp/SftpHostKeyVerifierTest.kt`

**Interfaces:**

```kotlin
data class StagedSshKey(val fileName: String, val displayName: String)

interface SshKeyStore {
    suspend fun stage(uri: Uri): StagedSshKey
    fun resolve(fileName: String): File
    suspend fun commit(fileName: String): String
    suspend fun delete(fileName: String)
}
```

Produces `HostKeyConfirmationRequired` and `HostKeyMismatch` with typed fields.

- [ ] **Step 1: Write failing key-file lifecycle tests**

```kotlin
@Test fun `stage commit resolve and delete preserve bytes`() {
    val store = SshKeyFiles(stagingDir, committedDir, fileName = { "fixed.key" })
    val staged = store.stage("private-key".byteInputStream())
    assertEquals("private-key", store.resolve(staged).readText())
    val committed = store.commit(staged)
    assertEquals("private-key", store.resolve(committed).readText())
    store.delete(committed)
    assertFalse(File(committedDir, committed).exists())
}

@Test fun `generated filename rejects traversal`() {
    assertThrows(IllegalArgumentException::class.java) { store.resolve("../secret") }
}
```

Run `:core:media:testDebugUnitTest`; expect unresolved `SshKeyFiles`.

- [ ] **Step 2: Implement the file-only core**

Use a strict generated-name pattern and same-filesystem rename:

```kotlin
private val validName = Regex("[0-9a-fA-F-]+\\.key")

fun commit(fileName: String): String {
    val source = stagedFile(fileName)
    require(source.isFile) { "Private key is missing" }
    committedDirectory.mkdirs()
    val target = committedFile(fileName)
    check(source.renameTo(target)) { "Couldn't commit private key" }
    return fileName
}
```

`resolve` checks staging first, committed second, then throws `FileNotFoundException("Private key is missing")`.

- [ ] **Step 3: Add the Android-facing store and Hilt binding**

`DefaultSshKeyStore.stage(uri)` opens the URI, queries `OpenableColumns.DISPLAY_NAME`, delegates bytes to `SshKeyFiles`, and returns a display name without persisting the URI. Use `Dispatchers.IO` for all file operations.

Add `implementation(libs.sshj)` and `testImplementation(libs.kotlinx.coroutines.test)` to `core/media/build.gradle.kts` before compiling the host verifier.

Bind it in `MediaModule`:

```kotlin
@Binds
@Singleton
fun bindSshKeyStore(store: DefaultSshKeyStore): SshKeyStore
```

- [ ] **Step 4: Write failing host-verifier tests**

Generate two RSA key pairs with `KeyPairGenerator`. Assert:

```kotlin
val confirmation = assertThrows(HostKeyConfirmationRequired::class.java) {
    SftpHostKeyVerifier("").verify("host", 22, first.public)
}
assertTrue(confirmation.fingerprint.startsWith("SHA256:"))
assertTrue(SftpHostKeyVerifier(confirmation.fingerprint).verify("host", 22, first.public))
val mismatch = assertThrows(HostKeyMismatch::class.java) {
    SftpHostKeyVerifier(confirmation.fingerprint).verify("host", 22, second.public)
}
assertEquals(confirmation.fingerprint, mismatch.expectedFingerprint)
```

- [ ] **Step 5: Implement strict verification**

```kotlin
class HostKeyConfirmationRequired(
    val host: String,
    val port: Int,
    val algorithm: String,
    val fingerprint: String,
) : IOException("Confirm SSH host key $fingerprint for $host:$port")

class HostKeyMismatch(
    val expectedFingerprint: String,
    val presentedFingerprint: String,
) : IOException("SSH host key changed")
```

The verifier computes SSHJ's SHA-256 fingerprint, throws confirmation when expected is blank, returns true only on exact match, and throws mismatch otherwise.

- [ ] **Step 6: Run GREEN**

```bash
env ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew :core:media:testDebugUnitTest
```

- [ ] **Step 7: Commit**

```bash
git add core/media
git commit -m "feat: add SSH key storage and host verification"
```

---

### Task 4: SFTP form rules, protocol presentation, and copy

**Files:**

- Create: `feature/network/src/main/java/dev/anilbeesetti/nextplayer/feature/network/screens/addconnection/ConnectionFormValidation.kt`
- Create: `feature/network/src/test/java/dev/anilbeesetti/nextplayer/feature/network/screens/addconnection/ConnectionFormValidationTest.kt`
- Modify: `feature/network/src/main/java/dev/anilbeesetti/nextplayer/feature/network/screens/list/NetworkScreen.kt`
- Modify: `core/ui/src/main/res/values/strings.xml`

**Interfaces:**

```kotlin
internal fun canSaveConnection(
    name: String,
    host: String,
    protocol: NetworkProtocol,
    username: String,
    authentication: NetworkAuthentication,
    hasPrivateKey: Boolean,
    isTesting: Boolean,
): Boolean
```

- [ ] **Step 1: Write failing validation tests**

Cover: legacy protocols need name/host only; SFTP password requires username; SFTP key requires username and key; testing always disables save.

```kotlin
assertFalse(canSaveConnection("S", "host", SFTP, "", PASSWORD, false, false))
assertTrue(canSaveConnection("S", "host", SFTP, "alice", PASSWORD, false, false))
assertFalse(canSaveConnection("S", "host", SFTP, "alice", SSH_KEY, false, false))
assertTrue(canSaveConnection("S", "host", SFTP, "alice", SSH_KEY, true, false))
```

Run `:feature:network:testDebugUnitTest`; expect unresolved function.

- [ ] **Step 2: Implement minimal pure validation**

```kotlin
if (name.isBlank() || host.isBlank() || isTesting) return false
if (protocol != NetworkProtocol.SFTP) return true
if (username.isBlank()) return false
return authentication != NetworkAuthentication.SSH_KEY || hasPrivateKey
```

- [ ] **Step 3: Add presentation and strings**

Map `NetworkProtocol.SFTP` to `NextIcons.Dns`. Add exact English strings for authentication, password, SSH key, choose/replace/remove private key, stored/selected key, optional passphrase, host-key confirmation, Trust, Reject, malformed/missing key, wrong passphrase, authentication rejected, host mismatch, and inaccessible root. Update the empty-state description to “Add an SMB, FTP, SFTP or WebDAV connection…”. The existing README SFTP entry remains unchanged.

- [ ] **Step 4: Run GREEN and resource compilation**

```bash
env ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew :feature:network:testDebugUnitTest :feature:network:assembleDebug
```

- [ ] **Step 5: Commit**

```bash
git add feature/network core/ui/src/main/res/values/strings.xml
git commit -m "feat: define SFTP connection form rules"
```

---

### Task 5: SSHJ SFTP client, injected factory, and streaming

**Files:**

- Modify: `core/media/build.gradle.kts`
- Modify: `core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/network/NetworkClient.kt`
- Modify: `core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/network/NetworkClientFactory.kt`
- Create: `core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/network/DefaultNetworkClientFactory.kt`
- Modify: `core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/network/proxy/NetworkStreamingProxy.kt`
- Modify: `core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/MediaModule.kt`
- Modify: `feature/network/src/main/java/dev/anilbeesetti/nextplayer/feature/network/screens/browse/NetworkBrowseViewModel.kt`
- Create: `core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/network/clients/SftpClient.kt`
- Create: `core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/network/sftp/SftpOwnedInputStream.kt`
- Create: `core/media/src/test/java/dev/anilbeesetti/nextplayer/core/media/network/sftp/SftpOwnedInputStreamTest.kt`
- Create: `core/media/src/test/java/dev/anilbeesetti/nextplayer/core/media/network/NetworkClientFactoryTest.kt`

**Interfaces:**

```kotlin
fun interface NetworkClientFactory {
    fun create(connection: NetworkConnection): NetworkClient
}

@Singleton
class DefaultNetworkClientFactory @Inject constructor(
    private val sshKeyStore: SshKeyStore,
) : NetworkClientFactory
```

- [ ] **Step 1: Write failing positional stream tests**

Extract an `OffsetReader` function `(Long, ByteArray, Int, Int) -> Int` and assert the first read receives the requested non-zero offset, later reads advance it, EOF is preserved, and `close()` closes remote file, SFTP client, and SSH client once even if an earlier close throws.

- [ ] **Step 2: Implement `SftpOwnedInputStream` and run GREEN**

Maintain `position = offset`; after each positive read add the returned count. Close resources in three independent `runCatching` calls and make close idempotent.

- [ ] **Step 3: Write the failing factory test**

Instantiate the factory with a fake key store and assert SFTP creates `SftpClient` while SMB/FTP/WebDAV retain their existing client types. Run and observe the exhaustive branch/client failure.

- [ ] **Step 4: Implement injected factory and proxy injection**

```kotlin
fun interface NetworkClientFactory {
    fun create(connection: NetworkConnection): NetworkClient
}

@Singleton
class DefaultNetworkClientFactory @Inject constructor(
    private val sshKeyStore: SshKeyStore,
) : NetworkClientFactory {
    override fun create(connection: NetworkConnection): NetworkClient = when (connection.protocol) {
        NetworkProtocol.SMB -> SmbClient(connection)
        NetworkProtocol.FTP -> FtpClient(connection)
        NetworkProtocol.SFTP -> SftpClient(connection, sshKeyStore)
        NetworkProtocol.WEBDAV -> WebDavClient(connection)
    }
}
```

Bind `DefaultNetworkClientFactory` to `NetworkClientFactory` in `MediaModule`. Inject the interface into `NetworkStreamingProxy` and `NetworkBrowseViewModel`, replacing both static calls.

- [ ] **Step 5: Implement `SftpClient`**

Use a persistent browsing SSH/SFTP pair. Normalize roots exactly like FTP. Configure 15-second connect and 120-second socket timeouts, install `SftpHostKeyVerifier`, authenticate with password or the resolved key, and verify the root with `stat`/`ls`.

Map directory entries to `NetworkFile`, excluding `.` and `..`. `fileSize` uses `stat(path).size`.

For `openStream`, create a fresh SSH client and SFTP client, open `RemoteFile` with `OpenMode.READ`, and wrap its positional `read(position, buffer, off, len)` in `SftpOwnedInputStream`.

When SSHJ wraps host-verifier exceptions, walk `generateSequence(error) { it.cause }` and rethrow the first `HostKeyConfirmationRequired` or `HostKeyMismatch`.

- [ ] **Step 6: Run focused tests and dependency inspection**

```bash
env ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew :core:media:testDebugUnitTest
env ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew :core:media:dependencies --configuration debugRuntimeClasspath
```

Expected: tests pass; only one compatible Bouncy Castle/EdDSA/SLF4J path is present and SSHJ is 0.40.0.

- [ ] **Step 7: Commit**

```bash
git add core/media
git commit -m "feat: add seekable SFTP network client"
```

---

### Task 6: Connection deletion cleanup

**Files:**

- Modify: `feature/network/src/main/java/dev/anilbeesetti/nextplayer/feature/network/screens/list/NetworkViewModel.kt`
- Create: `feature/network/src/test/java/dev/anilbeesetti/nextplayer/feature/network/screens/list/NetworkViewModelTest.kt`

**Interfaces:**

- Consumes `SshKeyStore.delete(fileName)` from Task 3.

- [ ] **Step 1: Write failing deletion tests**

Using fake repository/key store, assert an SSH-key connection deletion calls Room deletion and then deletes its key; password connections never call key deletion; repository failure never deletes the key.

- [ ] **Step 2: Implement safe deletion ordering**

```kotlin
fun deleteConnection(id: Long) {
    viewModelScope.launch {
        val connection = repository.getConnection(id) ?: return@launch
        repository.delete(id)
        if (connection.privateKeyFileName.isNotBlank()) {
            sshKeyStore.delete(connection.privateKeyFileName)
        }
    }
}
```

- [ ] **Step 3: Run GREEN**

```bash
env ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew :feature:network:testDebugUnitTest :feature:network:assembleDebug
```

- [ ] **Step 4: Commit**

```bash
git add feature/network
git commit -m "feat: integrate SFTP browsing and key cleanup"
```

---

### Task 7: Add/edit SFTP UI, host confirmation, and staged-key orchestration

**Files:**

- Modify: `feature/network/src/main/java/dev/anilbeesetti/nextplayer/feature/network/screens/addconnection/AddConnectionViewModel.kt`
- Modify: `feature/network/src/main/java/dev/anilbeesetti/nextplayer/feature/network/screens/addconnection/AddConnectionScreen.kt`
- Modify: `feature/network/build.gradle.kts`
- Create: `feature/network/src/test/java/dev/anilbeesetti/nextplayer/feature/network/screens/addconnection/AddConnectionViewModelTest.kt`
- Create: `feature/network/src/test/java/dev/anilbeesetti/nextplayer/feature/network/MainDispatcherRule.kt`

**Interfaces:**

```kotlin
data class SelectedPrivateKey(val stagedFileName: String, val displayName: String)
data class HostKeyConfirmation(val host: String, val port: Int, val algorithm: String, val fingerprint: String)

sealed interface SaveState {
    data object Idle : SaveState
    data object Testing : SaveState
    data class ConfirmHostKey(val confirmation: HostKeyConfirmation) : SaveState
    data class Error(val message: String?) : SaveState
}
```

ViewModel methods: `stagePrivateKey(Uri)`, `removeSelectedPrivateKey()`, `testAndSave(NetworkConnection)`, `acceptHostKey()`, `rejectHostKey()`, `clearError()`, `cancel()`.

- [ ] **Step 1: Write ViewModel RED tests**

Add `testImplementation(libs.kotlinx.coroutines.test)` to `feature/network/build.gradle.kts`.

Use fake repository, fake factory clients, and fake key store to cover:

- staging replaces and deletes the prior staged key;
- unknown host yields `ConfirmHostKey` and no save;
- reject returns idle and retains staged key;
- accept retries the retained exact draft with only the fingerprint changed;
- successful key save commits stage, upserts committed filename, then deletes old committed key;
- failed auth retains stage and old committed key;
- switching to password saves blank key fields then deletes the old key;
- `cancel()` and `onCleared()` delete only staged files.

Run `:feature:network:testDebugUnitTest`; observe missing methods/states.

- [ ] **Step 2: Implement ViewModel orchestration**

Inject repository, `NetworkClientFactory`, and `SshKeyStore`. Retain `pendingDraft` privately while confirmation is displayed. On connection failure, search the cause chain for typed host exceptions. Always disconnect the test client in `finally`.

Successful key save ordering:

```kotlin
val committed = sshKeyStore.commit(selected.stagedFileName)
val saved = draft.copy(privateKeyFileName = committed)
runCatching { repository.upsert(saved) }
    .onFailure { sshKeyStore.delete(committed) }
    .getOrThrow()
if (oldKey.isNotBlank() && oldKey != committed) sshKeyStore.delete(oldKey)
```

- [ ] **Step 3: Add document-picker and SFTP form UI**

In the route:

```kotlin
val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let(viewModel::stagePrivateKey)
}
```

For SFTP, show a password/SSH-key segmented selector. Key mode shows Choose/Replace, selected display name or “Private key stored”, Remove, and optional passphrase. Password mode shows the existing password field. Use `canSaveConnection` from Task 4.

Clear hidden submitted fields:

```kotlin
password = if (protocol == SFTP && authentication == SSH_KEY) "" else password
privateKeyFileName = if (protocol == SFTP && authentication == SSH_KEY) activeKeyName else ""
privateKeyPassphrase = if (protocol == SFTP && authentication == SSH_KEY) passphrase else ""
hostKeyFingerprint = if (protocol == SFTP) hostKeyFingerprint else ""
```

- [ ] **Step 4: Add fingerprint confirmation dialog**

Use `NextDialog`. Display `host:port`, algorithm, the full `SHA256:` fingerprint, comparison guidance, Reject, and Trust. Trust invokes `acceptHostKey`; reject invokes `rejectHostKey`.

- [ ] **Step 5: Run GREEN and assemble**

```bash
env ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew :feature:network:testDebugUnitTest :app:assembleDebug
```

- [ ] **Step 6: Commit**

```bash
git add feature/network
git commit -m "feat: add SFTP password and SSH key setup UI"
```

---

### Task 8: Full verification and disposable-emulator acceptance

**Files:**

- Modify only if evidence finds a bug: files owned by the responsible prior task.
- Create evidence outside git under `/tmp/nextplayer-sftp-qa/`.
- Do not commit keys, passwords, media fixtures, emulator state, raw unredacted logs, or generated server host material.

- [ ] **Step 1: Run full static/unit/build verification**

```bash
env ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew test
env ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew ktlintCheck
env ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew assembleDebug
```

Expected: all exit 0. Record exact task/test counts.

- [ ] **Step 2: Create disposable infrastructure**

Restart the task-specific AVD from Task 2 with wiped data:

```bash
mkdir -p /tmp/nextplayer-sftp-qa
adb -s emulator-5580 emu kill
/Users/anil/Library/Android/sdk/emulator/emulator -avd nextplayer-sftp-disposable -port 5580 -wipe-data -no-snapshot -no-boot-anim
adb -s emulator-5580 wait-for-device
```

Create exact throwaway fixtures and start the server on localhost port `22222`, reachable from the emulator as `10.0.2.2:22222`:

```bash
mkdir -p /tmp/nextplayer-sftp-fixtures/keys /tmp/nextplayer-sftp-fixtures/upload/nested
ssh-keygen -q -t ed25519 -N '' -f /tmp/nextplayer-sftp-fixtures/id_ed25519
ssh-keygen -q -t ed25519 -N 'keypass' -f /tmp/nextplayer-sftp-fixtures/id_ed25519_encrypted
cp /tmp/nextplayer-sftp-fixtures/id_ed25519.pub /tmp/nextplayer-sftp-fixtures/keys/
cp /tmp/nextplayer-sftp-fixtures/id_ed25519_encrypted.pub /tmp/nextplayer-sftp-fixtures/keys/
ffmpeg -y -f lavfi -i color=c=blue:s=640x360:d=20 -c:v libx264 -pix_fmt yuv420p -movflags +faststart /tmp/nextplayer-sftp-fixtures/upload/nested/test.mp4
docker run --rm -d --name nextplayer-sftp-qa -p 22222:22 -v /tmp/nextplayer-sftp-fixtures/upload:/home/test/upload -v /tmp/nextplayer-sftp-fixtures/keys:/home/test/.ssh/keys:ro atmoz/sftp:alpine 'test:testpass:1001'
```

- [ ] **Step 3: Install, launch, and capture clean startup evidence**

```bash
env ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew :app:installDebug --console=plain
adb -s emulator-5580 logcat -c
adb -s emulator-5580 shell am start -n dev.anilbeesetti.nextplayer.debug/dev.anilbeesetti.nextplayer.MainActivity
adb -s emulator-5580 exec-out uiautomator dump /dev/tty > /tmp/nextplayer-sftp-qa/startup.xml
adb -s emulator-5580 exec-out screencap -p > /tmp/nextplayer-sftp-qa/startup.png
```

- [ ] **Step 4: Exercise authentication and host verification**

Drive all taps from UI-tree bounds. Verify password auth, unencrypted key auth, encrypted key/passphrase auth, reject-without-save, trust-and-retry, fingerprint persistence, and mismatch after rotating the disposable server host key.

Rotate the server host key by recreating the ephemeral container, which generates a new host key while retaining the same user data and published port:

```bash
docker stop nextplayer-sftp-qa
docker run --rm -d --name nextplayer-sftp-qa -p 22222:22 -v /tmp/nextplayer-sftp-fixtures/upload:/home/test/upload -v /tmp/nextplayer-sftp-fixtures/keys:/home/test/.ssh/keys:ro atmoz/sftp:alpine 'test:testpass:1001'
```

Push test keys only to emulator Downloads and select them through DocumentsUI:

```bash
adb -s emulator-5580 push /tmp/nextplayer-sftp-fixtures/id_ed25519 /sdcard/Download/id_ed25519
adb -s emulator-5580 push /tmp/nextplayer-sftp-fixtures/id_ed25519_encrypted /sdcard/Download/id_ed25519_encrypted
adb -s emulator-5580 exec-out uiautomator dump /dev/tty > /tmp/nextplayer-sftp-qa/add-connection.xml
python3 /Users/anil/.codex/plugins/cache/openai-curated-remote/test-android-apps/0.1.2/skills/android-emulator-qa/scripts/ui_pick.py /tmp/nextplayer-sftp-qa/add-connection.xml "Add connection"
```

- [ ] **Step 5: Exercise browsing, playback, seek, edit, and delete**

Verify root and nested browsing, play the fixture, seek forward, and confirm log evidence shows a non-zero SFTP read offset. Replace the key, reconnect, delete the connection, and confirm no app-visible stale credential behavior or focused cleanup-test failure.

- [ ] **Step 6: Capture final evidence and verify no crash**

```bash
PID=$(adb -s emulator-5580 shell pidof -s dev.anilbeesetti.nextplayer.debug)
adb -s emulator-5580 logcat --pid "$PID" -d > /tmp/nextplayer-sftp-qa/app-logcat.txt
adb -s emulator-5580 logcat -b crash -d > /tmp/nextplayer-sftp-qa/crash-log.txt
```

Expected: crash log is empty. Sanitize secrets and source paths from retained evidence.

- [ ] **Step 7: Clean up disposable state**

Stop/delete the SFTP container and remove throwaway keys, credentials, fixtures, and pushed emulator documents. Stop the emulator and run:

```bash
adb -s emulator-5580 emu kill
docker stop nextplayer-sftp-qa
avdmanager delete avd -n nextplayer-sftp-disposable
rm -rf /tmp/nextplayer-sftp-fixtures
```

Verify `emulator -list-avds` no longer lists it and the server/container no longer exists.

- [ ] **Step 8: Final review and commit fixes only if evidence required them**

Run a whole-branch code review against the merge base. Any Critical/Important finding receives a focused fix with its covering test rerun, followed by full verification. If no fix is needed, do not create an empty commit.

---

## Completion Checklist

- [ ] Every task has its RED and GREEN evidence recorded.
- [ ] Parallel agents changed no overlapping files.
- [ ] Schema 8 is exported; schemas 1–7 are unchanged.
- [ ] No key URI, absolute path, key bytes, credentials, or QA secret is tracked by git.
- [ ] Dependency inspection confirms SSHJ 0.40.0 and no conflicting crypto/provider versions.
- [ ] Full unit tests, ktlint, and debug assembly pass freshly.
- [ ] Disposable-emulator QA passes with sanitized UI trees, screenshots, and logs.
- [ ] Disposable emulator/server/key/credential/fixture cleanup is verified.
- [ ] Whole-branch review has no open Critical or Important findings.
