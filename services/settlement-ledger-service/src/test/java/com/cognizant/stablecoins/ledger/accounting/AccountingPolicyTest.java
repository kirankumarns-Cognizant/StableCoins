package com.cognizant.stablecoins.ledger.accounting;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AccountingPolicyTest {
    @Test void allApprovedAccountsValidateAtStartup(){var f=new AccountingFixture();assertThatCode(f.policy::validate).doesNotThrowAnyException();}
    @Test void missingConfiguredAccountFailsClosed(){var f=new AccountingFixture();f.properties.getAccounts().put(AccountingProperties.Role.PROVIDER_INR,"149999");assertThatThrownBy(f.policy::validate).hasMessage("Journal validation failed: ACCOUNTING_POLICY_CONFIGURATION_INVALID");}
    @Test void wrongAssetIsRejected(){var f=new AccountingFixture();f.chart.get("140001").setAsset("USD");assertThatThrownBy(f.policy::validate).hasMessage("Journal validation failed: ACCOUNTING_POLICY_CONFIGURATION_INVALID");}
    @Test void wrongTypeIsRejected(){var f=new AccountingFixture();f.chart.get("240001").setAccountType("ASSET");assertThatThrownBy(f.policy::validate).hasMessage("Journal validation failed: ACCOUNTING_POLICY_CONFIGURATION_INVALID");}
    @Test void inactiveMappingIsRejected(){var f=new AccountingFixture();f.chart.get("130001").setActive(false);assertThatThrownBy(f.policy::validate).hasMessage("Journal validation failed: ACCOUNTING_POLICY_CONFIGURATION_INVALID");}
    @Test void mappingIsRevalidatedAtPosting(){var f=new AccountingFixture();f.policy.validate();f.chart.get("110003").setActive(false);assertThatThrownBy(()->f.service.fundAndConvert(f.context(PostingEvidence.Outcome.USDC_WALLET))).hasMessage("Journal validation failed: ACCOUNTING_POLICY_CONFIGURATION_INVALID");}
}
