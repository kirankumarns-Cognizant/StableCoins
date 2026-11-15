package com.cognizant.stablecoins.blockchain.service;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import static org.assertj.core.api.Assertions.*;
class ReceiptFeeParserTest {
 final ObjectMapper mapper=new ObjectMapper();
 ObjectNode receipt(){var n=mapper.createObjectNode();n.put("transactionHash","0x"+"a".repeat(64));n.put("blockNumber","0x64");n.put("gasUsed","0xa410");n.put("effectiveGasPrice","0x3b9aca00");n.put("status","0x1");return n;}
 @Test void baseIncludesReceiptL1Fee(){var n=receipt().put("l1Fee","0x64");var r=ReceiptFeeParser.parse(n,84532);assertThat(r.isFeeComponentsComplete()).isTrue();assertThat(r.getTotalFeeWei()).isEqualTo(new BigInteger("42000000000100"));}
 @Test void missingL1FeeIsIncomplete(){assertThat(ReceiptFeeParser.parse(receipt(),84532).isFeeComponentsComplete()).isFalse();}
 @Test void ethereumNeedsNoRollupFee(){var r=ReceiptFeeParser.parse(receipt(),11155111);assertThat(r.isFeeComponentsComplete()).isTrue();assertThat(r.getAdditionalFeeWei()).isZero();}
 @Test void missingExecutionPriceIsNotZeroActualFee(){var n=receipt().put("l1Fee","0x64");n.remove("effectiveGasPrice");assertThat(ReceiptFeeParser.parse(n,84532).isFeeComponentsComplete()).isFalse();}
 @Test void nonzeroOperatorParametersWithoutAnEvidencedFeeStayIncomplete(){var n=receipt().put("l1Fee","0x64").put("operatorFeeScalar","0x1");assertThat(ReceiptFeeParser.parse(n,84532).getFeeEvidenceReason()).isEqualTo("OPERATOR_FEE_UNSUPPORTED");}
 @Test void evidencedOperatorFeeIsAddedOnce(){var n=receipt().put("l1Fee","0x64").put("operatorFee","0x32");assertThat(ReceiptFeeParser.parse(n,84532).getAdditionalFeeWei()).isEqualTo(BigInteger.valueOf(150));}
 @Test void zeroOperatorParametersDoNotInventAnExtraFee(){var n=receipt().put("l1Fee","0x64").put("operatorFeeScalar","0x0").put("operatorFeeConstant","0x0");assertThat(ReceiptFeeParser.parse(n,84532).isFeeComponentsComplete()).isTrue();}
 @Test void revertedReceiptStillHasAnActualNetworkExpense(){var r=ReceiptFeeParser.parse(receipt().put("status","0x0").put("l1Fee","0x64"),84532);assertThat(r.isSuccess()).isFalse();assertThat(r.isFeeComponentsComplete()).isTrue();}
 @Test void malformedQuantitiesAreRejected(){assertThatThrownBy(()->ReceiptFeeParser.parse(receipt().put("gasUsed","-1"),84532)).isInstanceOf(IllegalArgumentException.class);}
}
