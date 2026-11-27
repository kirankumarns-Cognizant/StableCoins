package com.cognizant.stablecoins.payment.agent;
import com.cognizant.stablecoins.payment.intent.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
/** Optional natural-language extraction. Always unconfirmed; never a payment command. No text is retained. */
public record IntentDraft(String sourceAmountUsd,BeneficiaryOutcome requiredOutcome,String deliverBy,String maximumFeeUsd,
        RoutingPriority priority,List<BeneficiaryOutcome> allowedAlternatives,boolean confirmed,List<Span> evidence) {
    public enum Field { SOURCE_AMOUNT_USD, REQUIRED_OUTCOME, DELIVER_BY, MAXIMUM_FEE_USD, PRIORITY, ALLOWED_ALTERNATIVE }
    public record Span(Field field,int start,int end){ }
    public IntentDraft {allowedAlternatives=List.copyOf(allowedAlternatives);evidence=List.copyOf(evidence);}
    public List<Field> missingFields(){var fields=new ArrayList<Field>();if(sourceAmountUsd==null)fields.add(Field.SOURCE_AMOUNT_USD);
        if(requiredOutcome==null)fields.add(Field.REQUIRED_OUTCOME);if(deliverBy==null)fields.add(Field.DELIVER_BY);
        if(maximumFeeUsd==null)fields.add(Field.MAXIMUM_FEE_USD);if(priority==null)fields.add(Field.PRIORITY);return List.copyOf(fields);}
    boolean validate(String instruction,com.cognizant.stablecoins.payment.approval.RecommendationDocument document){
        if(confirmed||evidence.size()>12||allowedAlternatives.size()>3)throw new AgentFailure("AGENT_INVALID_INTENT_DRAFT");
        var intent=document.intent().instruction();var policy=document.intent().policy();boolean changed=false;
        try{var outcomes=new HashSet<>(allowedAlternatives);if(requiredOutcome!=null)outcomes.add(requiredOutcome);
            policy.requireAllowed(outcomes,maximumFeeUsd==null?null:amount(maximumFeeUsd));
        }catch(com.cognizant.stablecoins.payment.exception.PaymentValidationException e){throw new AgentFailure("AGENT_DRAFT_POLICY_REJECTED");}
        var seen=new HashSet<Field>();
        for(var span:evidence){if(span.field()==null||span.start()<0||span.end()<=span.start()||span.end()>instruction.length())throw new AgentFailure("AGENT_UNGROUNDED_INTENT_DRAFT");
            String text=instruction.substring(span.start(),span.end()).strip();seen.add(span.field());
            boolean valid=switch(span.field()){
                case SOURCE_AMOUNT_USD -> sourceAmountUsd!=null&&(text.contains("USD")||text.contains("$"))&&amount(text).compareTo(amount(sourceAmountUsd))==0;
                case MAXIMUM_FEE_USD -> maximumFeeUsd!=null&&(text.contains("USD")||text.contains("$"))&&amount(text).compareTo(amount(maximumFeeUsd))==0;
                case DELIVER_BY -> deliverBy!=null&&text.equals(deliverBy);
                case REQUIRED_OUTCOME -> requiredOutcome!=null&&outcomeMention(text,requiredOutcome);
                case PRIORITY -> priority!=null&&priorityMention(text,priority);
                case ALLOWED_ALTERNATIVE -> allowedAlternatives.stream().anyMatch(o->outcomeMention(text,o));
            };
            if(!valid)throw new AgentFailure("AGENT_UNGROUNDED_INTENT_DRAFT");
        }
        if(sourceAmountUsd!=null){need(seen,Field.SOURCE_AMOUNT_USD);var amount=amount(sourceAmountUsd);if(amount.signum()<=0)throw new AgentFailure("AGENT_INVALID_INTENT_DRAFT");changed|=amount.compareTo(document.context().sourceAmountUsd())!=0;}
        if(maximumFeeUsd!=null){need(seen,Field.MAXIMUM_FEE_USD);var fee=amount(maximumFeeUsd);if(fee.signum()<0)throw new AgentFailure("AGENT_DRAFT_POLICY_REJECTED");changed|=fee.compareTo(intent.maximumFee())!=0;}
        if(requiredOutcome!=null){need(seen,Field.REQUIRED_OUTCOME);changed|=requiredOutcome!=intent.requiredOutcome();}
        if(deliverBy!=null){need(seen,Field.DELIVER_BY);try{var date=Instant.parse(deliverBy);if(!date.isAfter(Instant.now()))throw new AgentFailure("AGENT_INVALID_INTENT_DRAFT");changed|=!date.equals(intent.deliverBy());}catch(java.time.format.DateTimeParseException e){throw new AgentFailure("AGENT_INVALID_INTENT_DRAFT");}}
        if(priority!=null){need(seen,Field.PRIORITY);changed|=priority!=intent.priority();}
        if(!allowedAlternatives.isEmpty()){need(seen,Field.ALLOWED_ALTERNATIVE);
            if(new HashSet<>(allowedAlternatives).size()!=allowedAlternatives.size()||allowedAlternatives.contains(requiredOutcome))throw new AgentFailure("AGENT_DRAFT_POLICY_REJECTED");
            for(var alternative:allowedAlternatives)if(evidence.stream().filter(e->e.field()==Field.ALLOWED_ALTERNATIVE).noneMatch(e->outcomeMention(instruction.substring(e.start(),e.end()),alternative)))throw new AgentFailure("AGENT_UNGROUNDED_INTENT_DRAFT");
            changed|=!new HashSet<>(allowedAlternatives).equals(intent.allowedAlternatives());
        }
        return changed;
    }
    private static void need(Set<Field> seen,Field field){if(!seen.contains(field))throw new AgentFailure("AGENT_UNGROUNDED_INTENT_DRAFT");}
    private static BigDecimal amount(String value){try{String text=value.replace("USD","").replace("$","").replace(",","").strip();if(!text.matches("[0-9]{1,16}(?:\\.[0-9]{1,2})?"))throw new NumberFormatException();return new BigDecimal(text);}catch(Exception e){throw new AgentFailure("AGENT_INVALID_INTENT_DRAFT");}}
    private static boolean outcomeMention(String text,BeneficiaryOutcome outcome){String t=text.toUpperCase(Locale.ROOT);return switch(outcome){case TOKEN_WALLET -> false;case USDC_WALLET -> t.contains("USDC")&&t.contains("WALLET");case SIMULATED_BANK_INR -> t.contains("INR")&&t.contains("BANK");case SIMULATED_BANK_USD -> t.contains("USD")&&t.contains("BANK");};}
    private static boolean priorityMention(String text,RoutingPriority priority){String t=text.toLowerCase(Locale.ROOT);return priority==RoutingPriority.LOWEST_COST?t.contains("lowest cost")||t.contains("cheapest"):t.contains("fastest")||t.contains("meet deadline");}
}
