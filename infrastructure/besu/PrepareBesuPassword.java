import java.io.Console;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import org.web3j.crypto.WalletUtils;

/** Interactive Windows/Codespaces/Linux setup. Verifies the actual dedicated wallet before writing its password. */
class PrepareBesuPassword {

    static void protect(Path path, String mode) throws Exception {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(mode));
        } else {
            var view = Files.getFileAttributeView(path, java.nio.file.attribute.AclFileAttributeView.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (view == null) throw new IllegalStateException("OWNER_ONLY_PERMISSIONS_UNSUPPORTED");
            String domain = System.getenv("USERDOMAIN");
            String name = (domain == null ? "" : domain + "\\") + System.getProperty("user.name");
            var user = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(name);
            var builder = java.nio.file.attribute.AclEntry.newBuilder()
                    .setType(java.nio.file.attribute.AclEntryType.ALLOW).setPrincipal(user)
                    .setPermissions(java.util.EnumSet.allOf(java.nio.file.attribute.AclEntryPermission.class));
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                builder.setFlags(java.nio.file.attribute.AclEntryFlag.FILE_INHERIT,
                        java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT);
            view.setAcl(java.util.List.of(builder.build()));
            if (view.getAcl().size() != 1 || !view.getAcl().getFirst().principal().equals(user))
                throw new IllegalStateException("OWNER_ONLY_ACL_REQUIRED");
        }
    }

    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) { System.err.println("INTERACTIVE_TERMINAL_REQUIRED"); System.exit(2); }
        char[] password = null, confirmation = null;
        byte[] bytes = null;
        int exit = 1;
        try {
            if (args.length != 0) throw new IllegalArgumentException("NO_ARGUMENTS_ALLOWED");
            Path root = Path.of("").toAbsolutePath();
            Path local = root.resolve(".local");
            Path wallets = local.resolve("besu-wallets");
            Path wallet = wallets.resolve("treasury-wallet.json");
            Path secrets = local.resolve("secrets");
            Path file = secrets.resolve("besu-treasury-password");
            if (Files.isSymbolicLink(local) || Files.isSymbolicLink(wallets) || Files.isSymbolicLink(secrets)
                    || Files.isSymbolicLink(wallet) || !Files.isRegularFile(wallet, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalArgumentException("DEDICATED_BESU_WALLET_REQUIRED");
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalArgumentException("EXISTING_BESU_PASSWORD_PRESERVED");
            if (Files.getFileStore(wallet).supportsFileAttributeView("posix") && !Files.getPosixFilePermissions(wallet).equals(PosixFilePermissions.fromString("rw-------")))
                throw new IllegalArgumentException("BESU_WALLET_MODE_600_REQUIRED");
            password = console.readPassword("Dedicated BESU treasury password: ");
            confirmation = console.readPassword("Confirm dedicated BESU treasury password: ");
            if (password == null || password.length < 12 || !Arrays.equals(password, confirmation))
                throw new IllegalArgumentException("PASSWORD_CONFIRMATION_FAILED");
            // Loading proves the password unlocks this saved file; confirmation alone does not.
            var credentials = WalletUtils.loadCredentials(new String(password), wallet.toFile());
            var metadata = org.web3j.protocol.ObjectMapperFactory.getObjectMapper()
                    .readValue(wallet.toFile(), org.web3j.crypto.WalletFile.class);
            String recorded = metadata.getAddress();
            if (recorded == null || !credentials.getAddress().equalsIgnoreCase(
                    recorded.startsWith("0x") ? recorded : "0x" + recorded))
                throw new IllegalArgumentException("BESU_WALLET_ADDRESS_MISMATCH");
            Files.createDirectories(secrets);
            protect(secrets, "rwx------");
            ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
            bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            if (encoded.hasArray()) Arrays.fill(encoded.array(), (byte) 0);
            // Atomic create-new prevents accidental replacement; permissions are restrictive at creation.
            try (var channel = Files.newByteChannel(file,
                    java.util.Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    Files.getFileStore(secrets).supportsFileAttributeView("posix") ? new java.nio.file.attribute.FileAttribute<?>[] { PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")) } : new java.nio.file.attribute.FileAttribute<?>[0])) {
                ByteBuffer data = ByteBuffer.wrap(bytes);
                while (data.hasRemaining()) channel.write(data);
            }
            protect(file, "rw-------");
            if (Files.getFileStore(file).supportsFileAttributeView("posix") && !Files.getPosixFilePermissions(file).equals(PosixFilePermissions.fromString("rw-------")))
                throw new IllegalStateException("BESU_PASSWORD_MODE_600_REQUIRED");
            System.out.println("BESU_TREASURY_PASSWORD_VERIFIED_AND_SAVED");
            System.out.println("Protected file: .local/secrets/besu-treasury-password");
            System.out.println("No wallet generated, chain started, application enabled or transaction submitted.");
            exit = 0;
        } catch (Exception | LinkageError e) {
            // Do not print KDF errors, input, exception messages or password bytes.
            System.err.println("BESU_PASSWORD_SETUP_FAILED: " + e.getClass().getSimpleName());
            System.err.println("Check dedicated wallet, password, permissions and existing file. Preserve existing files.");
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
        }
        System.exit(exit);
    }
}