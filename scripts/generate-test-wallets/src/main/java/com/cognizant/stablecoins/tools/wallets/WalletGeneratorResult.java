package com.cognizant.stablecoins.tools.wallets;

import java.nio.file.Path;

/**
 * Immutable result returned after generating two test wallets.
 *
 * <p>Contains ONLY public, non-secret data:
 * <ul>
 *   <li>EIP-55 checksum public addresses</li>
 *   <li>Paths to the encrypted keystore JSON files</li>
 * </ul>
 *
 * <p>Private keys are NEVER stored in this object, never returned to callers,
 * and never appear in any log or console output.
 */
public final class WalletGeneratorResult {

    private final String treasuryAddress;
    private final String beneficiaryAddress;
    private final Path treasuryWalletFile;
    private final Path beneficiaryWalletFile;

    public WalletGeneratorResult(String treasuryAddress,
                                  String beneficiaryAddress,
                                  Path treasuryWalletFile,
                                  Path beneficiaryWalletFile) {
        this.treasuryAddress = treasuryAddress;
        this.beneficiaryAddress = beneficiaryAddress;
        this.treasuryWalletFile = treasuryWalletFile;
        this.beneficiaryWalletFile = beneficiaryWalletFile;
    }

    /** EIP-55 checksum Ethereum address for the Treasury wallet. */
    public String getTreasuryAddress() {
        return treasuryAddress;
    }

    /** EIP-55 checksum Ethereum address for the Beneficiary wallet. */
    public String getBeneficiaryAddress() {
        return beneficiaryAddress;
    }

    /** Path to the encrypted Ethereum keystore JSON for the Treasury wallet. */
    public Path getTreasuryWalletFile() {
        return treasuryWalletFile;
    }

    /** Path to the encrypted Ethereum keystore JSON for the Beneficiary wallet. */
    public Path getBeneficiaryWalletFile() {
        return beneficiaryWalletFile;
    }
}
