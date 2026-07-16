# SFTP and SSH-Key Authentication Design

## Purpose

Add SFTP as a first-class network-storage protocol and support both password and SSH private-key authentication. SSH-key configuration is SFTP-specific; SMB, FTP, and WebDAV keep their existing authentication behavior.

This resolves GitHub issue #1806 and makes the existing README claim of SFTP support accurate.

## Scope

The feature includes:

- SFTP browsing and seekable media streaming on port 22 by default.
- Password authentication for SFTP.
- Private-key authentication for SFTP, including encrypted keys with an optional passphrase.
- Importing keys through Android's document picker into app-private storage.
- Trust-on-first-use SSH host-key verification with a visible SHA-256 fingerprint.
- Persistence, editing, replacement, and deletion of SFTP credentials and imported keys.
- Migration of existing network connections without changing their behavior.

The feature does not include SCP, FTPS, SSH agents, hardware-backed FIDO keys, jump hosts, SSH tunneling, file uploads, or changes to SMB/WebDAV authentication.

## Library Choice

Use `com.hierynomus:sshj:0.40.0`.

SSHJ is focused on SSH/SFTP clients, supports modern OpenSSH private-key formats, encrypted Ed25519 keys, PKCS#8 keys, SHA-256 host fingerprints, and Android-compatible operation. Version 0.40.0 is newer than the 0.38.0 minimum that fixes CVE-2023-48795 (Terrapin).

Alternatives were rejected for this implementation:

- `com.github.mwiede:jsch` is actively maintained but has a lower-level API and needs additional algorithm/provider configuration on older Android runtimes.
- Apache MINA SSHD is broader and heavier, and its maintainers do not actively support or thoroughly test Android.

## Model and Persistence

Add `SFTP(22)` to `NetworkProtocol`.

Add a `NetworkAuthentication` enum:

- `PASSWORD`: the existing password/anonymous mechanism and the default for migrated rows.
- `SSH_KEY`: valid only when the protocol is `SFTP`.

Extend `NetworkConnection` and `NetworkConnectionEntity` with:

- `authentication: NetworkAuthentication = PASSWORD`
- `privateKeyFileName: String = ""`
- `privateKeyPassphrase: String = ""`
- `hostKeyFingerprint: String = ""`

The private-key field stores only a generated internal filename, never the source document URI or absolute path. The key data lives below the app's private files directory. The optional passphrase follows the same app-private Room persistence model as existing network passwords.

Increment the Room database from version 7 to version 8. Migration 7-to-8 adds non-null columns with the defaults above, so all existing SMB, FTP, and WebDAV rows remain password-authenticated and continue to work unchanged. Export the version 8 schema and add a Room migration test.

## Private-Key Lifecycle

Introduce an injectable `SshKeyStore` in `core:media`, with a file-system core that is unit-testable without Android framework classes. It owns URI import through an application `Context`, resolves generated filenames to app-private files, and exposes only the narrow stage/commit/read/delete operations needed by callers.

Key files have two states:

1. **Staged:** Selecting a document copies its bytes to a generated file in a staging directory. The original display name is retained only in UI state. A staged key can be retried without reopening the picker.
2. **Committed:** After connection testing and host verification succeed, the staged file is atomically moved into the permanent key directory. The committed generated filename is stored in Room.

Lifecycle rules:

- Cancelling a new connection or clearing its ViewModel deletes its uncommitted staged file.
- A failed authentication attempt leaves the staged key available for correction and retry during the same screen session.
- Replacing a key while editing preserves the old committed key until the replacement connection succeeds and its row is saved.
- After a successful replacement, the old committed key is deleted.
- Removing or deleting a connection deletes its committed key.
- A missing or unreadable committed key produces a specific user-facing error and never falls back to password authentication.

## SFTP Client

Add `SftpClient` implementing the existing `NetworkClient` contract and register it in `NetworkClientFactory`. Convert `NetworkClientFactory` from a global object to an injected singleton that receives `SshKeyStore`; inject that factory into the add-connection ViewModel, browse ViewModel, and streaming proxy. Existing SMB, FTP, and WebDAV construction remains behaviorally identical.

Connection setup:

1. Create and configure an SSHJ `SSHClient` with connection and socket timeouts matching the existing network clients.
2. Install a host-key verifier before connecting.
3. Connect to `host:effectivePort`.
4. Authenticate with `authPassword` for `PASSWORD`, or ask `SshKeyStore` to resolve the generated staged/committed filename, load that private key, and call public-key authentication for `SSH_KEY`.
5. Create an SSHJ SFTP client and verify the configured root path is reachable.

Browsing behavior follows FTP semantics: `NetworkConnection.path` is an absolute server path normalized to `/` when blank. Directory entries `.` and `..` are excluded. File size and modified time come from SFTP attributes.

Streaming uses a fresh SSH connection and SFTP channel per stream, keeping playback independent from the browsing connection. The stream opens the remote file at the requested byte offset, supports repeated reads, and closes the remote handle, SFTP channel, and SSH connection together. This preserves Media3 range/seek behavior without sharing mutable channel state.

## Host-Key Verification

Never use a verifier that blindly accepts every server key.

For an SFTP connection without a saved fingerprint, the verifier calculates the server key's SHA-256 fingerprint and throws a typed `HostKeyConfirmationRequired` exception carrying:

- Host and port.
- Host-key algorithm.
- SHA-256 fingerprint.

The add-connection ViewModel converts this into a confirmation state. The UI displays the algorithm and fingerprint and explains that the user should compare it with the server administrator's fingerprint.

Accepting the fingerprint retries the exact connection with that fingerprint pinned. The connection is saved only after the retry succeeds. Rejecting returns to the editable form without saving.

For a saved connection, the verifier accepts only the stored fingerprint. A changed fingerprint fails with a specific host-key mismatch error that shows both saved and presented fingerprints. Editing may explicitly clear the saved fingerprint to start confirmation again; ordinary retries never replace it automatically.

## Add/Edit Connection UI

The protocol selector includes SFTP. Existing SMB, FTP, and WebDAV forms remain unchanged.

For SFTP:

- Username is required.
- An authentication selector offers `Password` and `SSH key`.
- Password mode shows the existing password field.
- SSH-key mode shows:
  - A `Choose private key` document-picker action.
  - The selected key's display name.
  - Replace and remove actions.
  - An optional passphrase field using password visual transformation.
- `Test & Save` is enabled only when the common required fields and the selected authentication method's required fields are present.

Changing away from SFTP resets SFTP-only transient UI state but does not delete a committed key from an existing connection until that edit is successfully saved. Switching authentication methods does not silently reuse credentials from the hidden method.

The host-key confirmation uses the existing dialog style and is represented as an explicit `SaveState`, alongside idle, testing, and error states.

## Data Flow

For a new key-authenticated connection:

1. The screen launches `OpenDocument` and passes the selected URI to the ViewModel.
2. The ViewModel asks `SshKeyStore` to stage the document and updates screen state with the staged identifier and display name.
3. `Test & Save` creates a connection draft referencing the staged key.
4. `NetworkClientFactory` creates `SftpClient`; initial connection yields host-key confirmation.
5. After user acceptance, the ViewModel retries with the fingerprint pinned.
6. On success, the key store commits the staged key, the repository saves the Room entity, and navigation returns to the connection list.

Password-authenticated SFTP follows the same flow without key staging. Existing protocols continue through their current direct test-and-save path.

## Error Handling

Present actionable messages for:

- No private key selected.
- Private key missing after a saved connection is loaded.
- Unsupported or malformed private-key format.
- Incorrect or missing key passphrase.
- Password or public-key authentication rejection.
- Unknown host key awaiting confirmation.
- Host-key mismatch.
- Unreachable host, timeout, or disconnected stream.
- Missing or inaccessible SFTP root path.

Failures clean up only resources created by that attempt. A connection failure must not delete a committed old key or overwrite a saved fingerprint.

## Testing

Follow test-driven development for production behavior.

Automated coverage includes:

- `NetworkProtocol.SFTP` defaults to port 22 and factory selection is correct.
- Migration 7-to-8 adds the new columns with backward-compatible defaults.
- Repository entity/model mappings preserve all SFTP authentication fields.
- Key-store staging, commit, replacement, cancellation cleanup, and deletion.
- Password and SSH-key validation rules.
- Host fingerprint generation, first-use confirmation, acceptance, and mismatch rejection.
- SFTP path normalization and directory-entry mapping.
- Seekable SFTP stream reads begin at the requested offset and close every owned resource.
- Add/edit ViewModel states for testing, fingerprint confirmation, successful save, failed authentication, and key replacement.

Verification commands:

- `env ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew test`
- `env ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew ktlintCheck`
- `env ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew assembleDebug`

Disposable-emulator QA is a required acceptance gate, not an optional manual check:

1. Create a fresh task-specific Android Virtual Device from an installed Google APIs image at API 35 or newer. Start it with wiped data and do not reuse a developer's existing emulator.
2. Start a disposable local OpenSSH/SFTP server with throwaway password credentials, unencrypted and encrypted client keys, a known media fixture, and a generated server host key. Expose it to the Android emulator through the emulator host gateway (`10.0.2.2`).
3. Build and install the debug app on the disposable emulator, clear logcat, and confirm the package launches without a crash.
4. Drive the UI with `adb`, deriving every tap coordinate from a `uiautomator` tree rather than from screenshots. Push imported test keys to the emulator's Downloads directory and select them through Android's document picker.
5. Verify password authentication, an unencrypted private key, and an encrypted private key with its passphrase.
6. Verify first-use fingerprint confirmation, rejection without saving, acceptance and successful retry, persistence across reconnect, and host-key mismatch after restarting the disposable server with a different host key.
7. Verify root-directory browsing, nested navigation, video playback, and a seek that causes a non-zero range read from SFTP.
8. Verify editing/replacing a key and deleting the connection leave no orphaned committed key files, using app-visible behavior plus focused test/log evidence rather than requiring a rooted emulator.
9. Save step-specific UI trees, screenshots, and app-scoped logcat output as QA evidence. Confirm the crash buffer is empty.
10. Stop and delete the task-specific AVD and disposable SFTP server, then remove all throwaway keys, credentials, and media fixtures. Preserve only the non-secret QA evidence needed for review.

If the environment cannot create or boot a disposable emulator or server, report the exact missing SDK image, virtualization, container-runtime, or port-access prerequisite. Unit tests and a successful build do not replace this emulator gate.

## Documentation

Keep the README SFTP feature entry and update user-visible connection descriptions so SFTP appears consistently alongside SMB, FTP, and WebDAV. Add SSHJ to generated/open-source library attribution through the existing dependency reporting mechanism.

## Acceptance Criteria

- Users can create and edit SFTP connections using either a password or an imported SSH private key.
- Encrypted keys work when the correct passphrase is provided.
- Private-key bytes are copied to app-private storage and removed when no longer referenced.
- First-use host fingerprints require explicit confirmation and later key changes are rejected.
- Users can browse and play SFTP-hosted videos and seek within them.
- SMB, FTP, and WebDAV behavior is unchanged.
- Existing database rows migrate without data loss.
- Unit tests, formatting checks, and the debug build complete successfully.
- Disposable-emulator QA completes with retained non-secret evidence and full cleanup of the AVD, server, keys, credentials, and fixtures.
