package com.cognizant.stablecoins.tools.wallets;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Entry point for the StableCoinsDemo test wallet generator.
 *
 * <p>Reads passwords interactively (no echo) via {@link java.io.Console},
 * delegates to {@link WalletGenerator}, and prints ONLY non-secret information
 * to stdout.
 *
 * <p>SAFETY RULES enforced here:
 * <ul>
 *   <li>Private keys are NEVER printed.</li>
 *   <li>Passwords are NEVER printed or echoed.</li>
 *   <li>Raw wallet JSON contents are NEVER read back to the console.</li>
 *   <li>If either wallet file already exists, generation is aborted immediately.</li>
 * </ul>
 *
 * <p>These wallets are for <strong>Base Sepolia TESTNET ONLY</strong>.
 */
public class WalletGeneratorMain {

    /**
     * Functional interface for reading passwords without console echo.
     * Extracted to allow testability without a live {@link java.io.Console}.
     */
    @FunctionalInterface
    interface PasswordReader {
        /** Prompt the user and return the entered characters. Must not echo input. */
        char[] readPassword(String prompt);
    }

    public static void main(String[] args) {
        java.io.Console console = System.console();
        if (console == null) {
            System.err.println("ERROR: No interactive console detected.");
            System.err.println("Run this utility directly from a terminal, not inside Maven or an IDE.");
            System.err.println("Use: scripts/generate-test-wallets.ps1");
            System.exit(1);
        }

        PasswordReader reader = prompt -> console.readPassword("%s", prompt);
        Path walletDir = Paths.get(".local", "wallets");

        System.exit(run(reader, System.out, System.err, walletDir));
    }

    /**
     * Core logic — separated from {@code main} to allow unit testing.
     *
     * @return 0 on success, non-zero on any error or abort
     */
    static int run(PasswordReader passwordReader, PrintStream out, PrintStream err, Path walletDir) {
        WalletGenerator generator = new WalletGenerator();

        // Guard: abort if either wallet file already exists
        if (generator.walletsExist(walletDir)) {
            err.println("TEST WALLETS ALREADY EXIST.");
            err.println("Generation aborted to prevent accidental wallet replacement.");
            err.println("Rename or delete the existing files in:");
            err.println("  " + walletDir.toAbsolutePath());
            err.println("before generating a new pair.");
            return 2;
        }

        out.println("StableCoinsDemo Test Wallet Generator");
        out.println("Network: Base Sepolia TESTNET ONLY");
        out.println("--------------------------------------");
        out.println();
        out.println("You will be prompted for two passwords — one per wallet.");
        out.println("Passwords are NOT displayed. Store them securely offline.");
        out.println("If you lose a password, the wallet file cannot be recovered.");
        out.println();

        // Collect Treasury password with confirmation
        char[] treasuryPassword = passwordReader.readPassword("Enter password for Treasury wallet: ");
        char[] treasuryConfirm  = passwordReader.readPassword("Confirm Treasury wallet password:  ");

        if (!Arrays.equals(treasuryPassword, treasuryConfirm)) {
            Arrays.fill(treasuryPassword, '\0');
            Arrays.fill(treasuryConfirm, '\0');
            err.println("ERROR: Treasury passwords do not match. No files written.");
            return 3;
        }
        Arrays.fill(treasuryConfirm, '\0');

        // Collect Beneficiary password with confirmation
        char[] beneficiaryPassword = passwordReader.readPassword("Enter password for Beneficiary wallet: ");
        char[] beneficiaryConfirm  = passwordReader.readPassword("Confirm Beneficiary wallet password:  ");

        if (!Arrays.equals(beneficiaryPassword, beneficiaryConfirm)) {
            Arrays.fill(treasuryPassword, '\0');
            Arrays.fill(beneficiaryPassword, '\0');
            Arrays.fill(beneficiaryConfirm, '\0');
            err.println("ERROR: Beneficiary passwords do not match. No files written.");
            return 3;
        }
        Arrays.fill(beneficiaryConfirm, '\0');

        try {
            // Passwords are cleared inside WalletGenerator.generate() after each keystore is written
            WalletGeneratorResult result = generator.generate(treasuryPassword, beneficiaryPassword, walletDir);
            printSuccess(out, result);
            return 0;
        } catch (Exception e) {
            err.println("ERROR: Wallet generation failed: " + e.getMessage());
            return 4;
        }
    }

    private static void printSuccess(PrintStream out, WalletGeneratorResult result) {
        out.println();
        out.println("StableCoinsDemo test wallets created.");
        out.println();
        out.println("Network designation:");
        out.println("  Base Sepolia TESTNET ONLY");
        out.println();
        out.println("Treasury public address:");
        out.println("  " + result.getTreasuryAddress());
        out.println();
        out.println("Beneficiary public address:");
        out.println("  " + result.getBeneficiaryAddress());
        out.println();
        out.println("Encrypted wallet files:");
        out.println("  " + result.getTreasuryWalletFile().toAbsolutePath());
        out.println("  " + result.getBeneficiaryWalletFile().toAbsolutePath());
        out.println();
        out.println("PRIVATE KEYS: NOT DISPLAYED");
        out.println("PASSWORDS: NOT DISPLAYED");
        out.println();
        out.println("Next step:");
        out.println("  Fund Treasury wallet with Base Sepolia ETH and test USDC.");
        out.println();
        out.println("WARNING:");
        out.println("  These are disposable Base Sepolia TESTNET wallets.");
        out.println("  Never fund with mainnet ETH, production USDC, or other assets");
        out.println("  having real financial value.");
    }
}
