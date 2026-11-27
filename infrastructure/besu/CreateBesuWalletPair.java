import com.cognizant.stablecoins.tools.wallets.WalletGenerator;
import java.io.Console;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import org.web3j.crypto.WalletUtils;

/** Explicit private-demo wallet setup. No RPC or public wallet modification. */
class CreateBesuWalletPair {
    static char[] password(Console console, String prompt) {
        char[] result = console.readPassword("%s", prompt);
        if (result == null || result.length < 12) {
            clear(result);
            throw new IllegalArgumentException("PASSWORD_AT_LEAST_12_CHARACTERS_REQUIRED");
        }
        return result;
    }
    static void clear(char[] value) { if (value != null) Arrays.fill(value, '\0'); }
    static char[] confirmed(Console console, String role) {
        char[] first = password(console, "Create BESU " + role + " password: ");
        char[] second = null;
        boolean matched = false;
        try {
            second = password(console, "Confirm BESU " + role + " password: ");
            matched = Arrays.equals(first, second);
            if (!matched) throw new IllegalArgumentException("PASSWORD_CONFIRMATION_MISMATCH");
            return first;
        } finally { clear(second); if (!matched) clear(first); }
    }

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
    static void verify(Console console, String role, Path file, String expected) throws Exception {
        char[] value = password(console, "Re-enter BESU " + role + " password to verify saved file: ");
        try {
            if (!WalletUtils.loadCredentials(new String(value), file.toFile()).getAddress().equalsIgnoreCase(expected))
                throw new IllegalStateException("BESU_WALLET_ADDRESS_MISMATCH");
        } finally { clear(value); }
    }
    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) { System.err.println("INTERACTIVE_TERMINAL_REQUIRED"); System.exit(2); }
        char[] treasury = null, beneficiary = null;
        Path dir = Path.of(".local", "besu-wallets");
        int exit = 1;
        try {
            if (args.length != 0 || Files.exists(dir, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(dir.getParent())) throw new IllegalArgumentException("BESU_WALLET_DIRECTORY_MUST_NOT_EXIST");
            System.out.println("Dedicated PRIVATE_DEMO wallets. Not Circle USDC; no public faucets needed.");
            treasury = confirmed(console, "TREASURY");
            beneficiary = confirmed(console, "BENEFICIARY");
            Files.createDirectories(dir.getParent());
            Files.createDirectory(dir);
            protect(dir, "rwx------");
            var pair = new WalletGenerator().generate(treasury, beneficiary, dir);
            clear(treasury); clear(beneficiary);
            protect(pair.getTreasuryWalletFile(), "rw-------");
            protect(pair.getBeneficiaryWalletFile(), "rw-------");
            verify(console, "TREASURY", pair.getTreasuryWalletFile(), pair.getTreasuryAddress());
            verify(console, "BENEFICIARY", pair.getBeneficiaryWalletFile(), pair.getBeneficiaryAddress());
            System.out.println("BESU_PAIR_VERIFIED");
            System.out.println("Treasury: " + pair.getTreasuryAddress());
            System.out.println("Beneficiary: " + pair.getBeneficiaryAddress());
            System.out.println("Directory: " + dir.toAbsolutePath());
            System.out.println("No chain started or transaction submitted. Preserve these encrypted files.");
            exit = 0;
        } catch (Exception | LinkageError e) {
            System.err.println("BESU_PAIR_NOT_VERIFIED: " + e.getClass().getSimpleName());
            System.err.println("Preserve any created directory; do not fund or initialize from an unverified pair.");
        } finally { clear(treasury); clear(beneficiary); }
        System.exit(exit);
    }
}
