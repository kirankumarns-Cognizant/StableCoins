package com.cognizant.stablecoins.payment.agent;
import com.cognizant.stablecoins.payment.intent.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static com.cognizant.stablecoins.payment.agent.AgentFixtures.*;
import static com.cognizant.stablecoins.payment.routing.SimulatedRoutingScenarios.Scenario.*;
import static org.assertj.core.api.Assertions.*;
class IntentDraftTest {
    final String text="Pay USD 10,000 to INR bank using cheapest with fee USD 25";
    final com.cognizant.stablecoins.payment.approval.RecommendationDocument document=material(NORMAL,1).document();
    IntentDraft.Span span(IntentDraft.Field field,String part){int start=text.indexOf(part);return new IntentDraft.Span(field,start,start+part.length());}
    IntentDraft draft(String amount,boolean confirmed){return new IntentDraft(amount,BeneficiaryOutcome.SIMULATED_BANK_INR,null,"25",RoutingPriority.LOWEST_COST,List.of(),confirmed,List.of(
        span(IntentDraft.Field.SOURCE_AMOUNT_USD,"USD 10,000"),span(IntentDraft.Field.REQUIRED_OUTCOME,"INR bank"),span(IntentDraft.Field.MAXIMUM_FEE_USD,"USD 25"),span(IntentDraft.Field.PRIORITY,"cheapest")));}
    @Test void explicitRequirementsBecomeUnconfirmedPartialDraft(){var d=draft("10000",false);assertThat(d.validate(text,document)).isFalse();assertThat(d.confirmed()).isFalse();assertThat(d.missingFields()).containsExactly(IntentDraft.Field.DELIVER_BY);}
    @Test void inventedAmountFailsSourceProof(){assertThatThrownBy(()->draft("9000",false).validate(text,document)).hasMessage("AGENT_UNGROUNDED_INTENT_DRAFT");}
    @Test void modelCannotConfirmIntent(){assertThatThrownBy(()->draft("10000",true).validate(text,document)).hasMessage("AGENT_INVALID_INTENT_DRAFT");}
    @Test void explicitDifferentAmountIsOnlyAProposedChange(){String changed=text.replace("10,000","12,000");var d=draft("12000",false);assertThat(d.validate(changed,document)).isTrue();assertThat(document.context().sourceAmountUsd()).isEqualByComparingTo("10000");}
    @Test void feeCannotExceedExistingCustomerPolicy(){var d=new IntentDraft(null,null,null,"99",null,List.of(),false,List.of(new IntentDraft.Span(IntentDraft.Field.MAXIMUM_FEE_USD,0,6)));
        assertThatThrownBy(()->d.validate("USD 99",document)).hasMessage("AGENT_DRAFT_POLICY_REJECTED");}
    @Test void outOfRangeSpansAreRejected(){var d=new IntentDraft("10000",null,null,null,null,List.of(),false,List.of(new IntentDraft.Span(IntentDraft.Field.SOURCE_AMOUNT_USD,0,9999)));
        assertThatThrownBy(()->d.validate(text,document)).hasMessage("AGENT_UNGROUNDED_INTENT_DRAFT");}
    @Test void relativeDateCannotBecomeInventedDeadline(){var d=new IntentDraft(null,null,"tomorrow",null,null,List.of(),false,List.of(new IntentDraft.Span(IntentDraft.Field.DELIVER_BY,0,8)));
        assertThatThrownBy(()->d.validate("tomorrow",document)).hasMessage("AGENT_INVALID_INTENT_DRAFT");}
    @Test void bareNumberCannotInventUsdCurrency(){var d=new IntentDraft("10000",null,null,null,null,List.of(),false,List.of(new IntentDraft.Span(IntentDraft.Field.SOURCE_AMOUNT_USD,0,5)));
        assertThatThrownBy(()->d.validate("10000 EUR",document)).hasMessage("AGENT_UNGROUNDED_INTENT_DRAFT");}
    @Test void incompleteDraftReportsAllMissingFields(){var d=new IntentDraft(null,null,null,null,null,List.of(),false,List.of());assertThat(d.missingFields()).hasSize(5);assertThat(d.validate(text,document)).isFalse();}
}
